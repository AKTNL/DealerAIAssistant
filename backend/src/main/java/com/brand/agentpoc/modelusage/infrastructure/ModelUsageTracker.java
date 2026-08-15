package com.brand.agentpoc.modelusage.infrastructure;

import com.brand.agentpoc.modelusage.application.ModelBudgetAdmissionService;
import com.brand.agentpoc.modelusage.application.ModelBudgetAdmissionService.BudgetAdmission;
import com.brand.agentpoc.modelusage.application.ModelUsageRecordingService;
import com.brand.agentpoc.modelusage.domain.ModelBudgetExceededException;
import com.brand.agentpoc.modelusage.domain.ModelBudgetUnavailableException;
import com.brand.agentpoc.modelusage.domain.ModelUsageContext;
import com.brand.agentpoc.modelusage.domain.ModelUsageSnapshot;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
import com.brand.agentpoc.observability.domain.CorrelationField;
import com.brand.agentpoc.observability.domain.OperationalEvent;
import com.brand.agentpoc.observability.domain.OperationalOutcome;
import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ModelUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(ModelUsageTracker.class);

    private final ModelBudgetAdmissionService budgetAdmissionService;
    private final ModelUsageRecordingService recordingService;
    private final OperationalTelemetry operationalTelemetry;
    private final boolean enabled;

    @Autowired
    public ModelUsageTracker(
            ModelBudgetAdmissionService budgetAdmissionService,
            ModelUsageRecordingService recordingService,
            OperationalTelemetry operationalTelemetry
    ) {
        this(budgetAdmissionService, recordingService, operationalTelemetry, true);
    }

    private ModelUsageTracker(
            ModelBudgetAdmissionService budgetAdmissionService,
            ModelUsageRecordingService recordingService,
            OperationalTelemetry operationalTelemetry,
            boolean enabled
    ) {
        this.budgetAdmissionService = budgetAdmissionService;
        this.recordingService = recordingService;
        this.operationalTelemetry = operationalTelemetry;
        this.enabled = enabled;
    }

    public static ModelUsageTracker noop() {
        return new ModelUsageTracker(null, null, OperationalTelemetry.noop(), false);
    }

    public ChatModel track(ChatModel delegate, ModelUsageContext context) {
        Objects.requireNonNull(delegate, "delegate is required");
        Objects.requireNonNull(context, "context is required");
        if (!enabled) {
            return delegate;
        }
        return new TrackedChatModel(delegate, context);
    }

    private StartedCall begin(ModelUsageContext context) {
        String callKey = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        try {
            BudgetAdmission admission = budgetAdmissionService.admit(callKey, context);
            return new StartedCall(callKey, startedAt, admission.reservationId());
        } catch (ModelBudgetExceededException | ModelBudgetUnavailableException exception) {
            persist(callKey, context, ModelUsageStatus.REJECTED, ModelUsageSnapshot.unknown(), 0L, null);
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Model budget admission failed open: reason=governance_unavailable");
            return new StartedCall(callKey, startedAt, null);
        }
    }

    private void persist(
            String callKey,
            ModelUsageContext context,
            ModelUsageStatus status,
            ModelUsageSnapshot usage,
            long durationMs,
            Long reservationId
    ) {
        try {
            operationalTelemetry.observeVoid(OperationalEvent.MODEL_USAGE_RECORD, telemetry -> {
                telemetry.correlate(CorrelationField.TENANT_ID, context.tenantId());
                telemetry.correlate(CorrelationField.USER_ID, context.userId());
                telemetry.correlate(CorrelationField.CORRELATION_ID, callKey);
                telemetry.outcome(outcome(status));
                recordingService.record(callKey, context, status, usage, durationMs, reservationId);
            });
        } catch (RuntimeException exception) {
            log.warn("Model usage persistence failed: reason=governance_unavailable");
        }
    }

    private OperationalOutcome outcome(ModelUsageStatus status) {
        return switch (status) {
            case SUCCESS -> OperationalOutcome.SUCCESS;
            case ERROR -> OperationalOutcome.ERROR;
            case CANCELLED -> OperationalOutcome.CANCELLED;
            case REJECTED -> OperationalOutcome.REJECTED;
        };
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private final class TrackedChatModel implements ChatModel {
        private final ChatModel delegate;
        private final ModelUsageContext context;

        private TrackedChatModel(ChatModel delegate, ModelUsageContext context) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            StartedCall call = begin(context);
            try {
                ChatResponse response = delegate.call(prompt);
                persist(call.callKey(), context, ModelUsageStatus.SUCCESS, ModelUsageSnapshot.from(response),
                        elapsedMillis(call.startedAt()), call.reservationId());
                return response;
            } catch (RuntimeException | Error exception) {
                persist(call.callKey(), context, ModelUsageStatus.ERROR, ModelUsageSnapshot.unknown(),
                        elapsedMillis(call.startedAt()), call.reservationId());
                throw exception;
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                StartedCall call = begin(context);
                AtomicReference<ModelUsageSnapshot> usage = new AtomicReference<>(ModelUsageSnapshot.unknown());
                AtomicBoolean settled = new AtomicBoolean();
                return delegate.stream(prompt)
                        .doOnNext(response -> usage.updateAndGet(current ->
                                current.mergeCumulative(ModelUsageSnapshot.from(response))))
                        .doOnComplete(() -> settle(call, usage.get(), ModelUsageStatus.SUCCESS, settled))
                        .doOnError(error -> settle(call, usage.get(), ModelUsageStatus.ERROR, settled))
                        .doFinally(signal -> settle(call, usage.get(), ModelUsageStatus.CANCELLED, settled));
            });
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return delegate.getDefaultOptions();
        }

        private void settle(
                StartedCall call,
                ModelUsageSnapshot usage,
                ModelUsageStatus status,
                AtomicBoolean settled
        ) {
            if (settled.compareAndSet(false, true)) {
                persist(call.callKey(), context, status, usage, elapsedMillis(call.startedAt()), call.reservationId());
            }
        }
    }

    private record StartedCall(String callKey, long startedAt, Long reservationId) {
    }
}
