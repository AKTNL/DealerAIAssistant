package com.brand.agentpoc.modelusage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.modelusage.application.ModelBudgetAdmissionService;
import com.brand.agentpoc.modelusage.application.ModelBudgetAdmissionService.BudgetAdmission;
import com.brand.agentpoc.modelusage.application.ModelUsageRecordingService;
import com.brand.agentpoc.modelusage.domain.ModelBudgetExceededException;
import com.brand.agentpoc.modelusage.domain.ModelUsageContext;
import com.brand.agentpoc.modelusage.domain.ModelUsageScenario;
import com.brand.agentpoc.modelusage.domain.ModelUsageSnapshot;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

class ModelUsageTrackerTest {

    private final ModelUsageContext context = new ModelUsageContext(
            7L, 2L, ModelUsageScenario.CHAT, "openai-compatible", "gpt-test", "trace-1", false);
    private final Prompt prompt = new Prompt("hello");
    private ModelBudgetAdmissionService budgetService;
    private ModelUsageRecordingService recordingService;
    private ChatModel delegate;
    private ChatModel tracked;

    @BeforeEach
    void setUp() {
        budgetService = mock(ModelBudgetAdmissionService.class);
        recordingService = mock(ModelUsageRecordingService.class);
        delegate = mock(ChatModel.class);
        when(budgetService.admit(anyString(), eq(context))).thenReturn(BudgetAdmission.none());
        tracked = new ModelUsageTracker(budgetService, recordingService, OperationalTelemetry.noop())
                .track(delegate, context);
    }

    @Test
    void recordsOneSynchronousSuccessAfterTheDelegateReturns() {
        ChatResponse response = response(10, 4, 14);
        when(delegate.call(prompt)).thenReturn(response);

        tracked.call(prompt);

        verify(recordingService).record(anyString(), eq(context), eq(ModelUsageStatus.SUCCESS),
                eq(new ModelUsageSnapshot(10L, 4L, 14L)), anyLong(), isNull());
    }

    @Test
    void recordsOneSynchronousErrorAndRethrows() {
        when(delegate.call(prompt)).thenThrow(new IllegalStateException("provider failed"));

        assertThatThrownBy(() -> tracked.call(prompt)).isInstanceOf(IllegalStateException.class);

        verify(recordingService).record(anyString(), eq(context), eq(ModelUsageStatus.ERROR),
                eq(ModelUsageSnapshot.unknown()), anyLong(), isNull());
    }

    @Test
    void keepsMaximumCumulativeStreamUsageAndSettlesExactlyOnce() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(delegate.stream(prompt)).thenReturn(Flux.just(
                response(10, 2, 12),
                response(10, 7, 17),
                response(null, 4, null)
        ));
        ChatModel measured = new ModelUsageTracker(
                budgetService,
                recordingService,
                new OperationalTelemetry(ObservationRegistry.create(), meterRegistry)
        ).track(delegate, context);

        measured.stream(prompt).collectList().block();

        verify(recordingService, times(1)).record(anyString(), eq(context), eq(ModelUsageStatus.SUCCESS),
                eq(new ModelUsageSnapshot(10L, 7L, 17L)), anyLong(), isNull());
        assertThat(meterRegistry.get("agentpoc.model.call")
                .tag("app.outcome", "success").timer().count()).isEqualTo(1L);
    }

    @Test
    void recordsStreamCancellationExactlyOnce() {
        when(delegate.stream(prompt)).thenReturn(Flux.never());

        BaseSubscriber<ChatResponse> subscriber = new BaseSubscriber<>() {
        };
        tracked.stream(prompt).subscribe(subscriber);
        subscriber.cancel();

        verify(recordingService, times(1)).record(anyString(), eq(context), eq(ModelUsageStatus.CANCELLED),
                eq(ModelUsageSnapshot.unknown()), anyLong(), isNull());
    }

    @Test
    void recordsRejectedAdmissionWithoutCallingTheProvider() {
        when(budgetService.admit(anyString(), eq(context))).thenThrow(new ModelBudgetExceededException());

        assertThatThrownBy(() -> tracked.call(prompt)).isInstanceOf(ModelBudgetExceededException.class);

        verify(delegate, times(0)).call(prompt);
        verify(recordingService).record(anyString(), eq(context), eq(ModelUsageStatus.REJECTED),
                eq(ModelUsageSnapshot.unknown()), eq(0L), isNull());
    }

    @Test
    void emitsOneProviderDurationMetricPerLogicalCall() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ChatResponse response = response(10, 4, 14);
        when(delegate.call(prompt)).thenReturn(response);
        ChatModel measured = new ModelUsageTracker(
                budgetService,
                recordingService,
                new OperationalTelemetry(ObservationRegistry.create(), meterRegistry)
        ).track(delegate, context);

        measured.call(prompt);

        assertThat(meterRegistry.get("agentpoc.model.call")
                .tag("app.outcome", "success").timer().count()).isEqualTo(1L);
    }

    private ChatResponse response(Integer input, Integer output, Integer total) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(input, output, total))
                .build();
        return new ChatResponse(List.of(), metadata);
    }
}
