package com.brand.agentpoc.modelusage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.modelusage.domain.ModelCostSource;
import com.brand.agentpoc.modelusage.domain.ModelUsageContext;
import com.brand.agentpoc.modelusage.domain.ModelUsageScenario;
import com.brand.agentpoc.modelusage.domain.ModelUsageSnapshot;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetReservationRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelUsageRecordingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T02:00:00Z");
    private ModelUsageEventRepository eventRepository;
    private ModelPriceVersionRepository priceRepository;
    private ModelBudgetReservationRepository reservationRepository;
    private ModelUsageRecordingService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(ModelUsageEventRepository.class);
        priceRepository = mock(ModelPriceVersionRepository.class);
        reservationRepository = mock(ModelBudgetReservationRepository.class);
        service = new ModelUsageRecordingService(eventRepository, priceRepository, reservationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(eventRepository.findByCallKey("call-1")).thenReturn(List.of());
    }

    @Test
    void copiesTheEffectivePriceIntoAnImmutableUsageEventSnapshot() throws Exception {
        ModelPriceVersionEntity price = price("catalog-v1", "2.00000000", "4.00000000");
        setField(price, "id", 11L);
        when(priceRepository
                .findByTenantIdAndProviderKeyIgnoreCaseAndModelNameIgnoreCaseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        7L, "openai-compatible", "gpt-test", NOW))
                .thenReturn(List.of(price));

        service.record("call-1", context(), ModelUsageStatus.SUCCESS,
                new ModelUsageSnapshot(1_000L, 500L, 1_500L), 80L, null);

        ArgumentCaptor<ModelUsageEventEntity> eventCaptor = ArgumentCaptor.forClass(ModelUsageEventEntity.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        ModelUsageEventEntity event = eventCaptor.getValue();
        assertThat(event.getPriceVersionId()).isEqualTo(11L);
        assertThat(event.getPriceVersionKey()).isEqualTo("catalog-v1");
        assertThat(event.getInputPricePerMillion()).isEqualByComparingTo("2.00000000");
        assertThat(event.getOutputPricePerMillion()).isEqualByComparingTo("4.00000000");
        assertThat(event.getEstimatedCost()).isEqualByComparingTo("0.00400000");
        assertThat(event.getCostSource()).isEqualTo(ModelCostSource.CATALOG_ESTIMATE);
    }

    @Test
    void preservesUnknownCostWhenTokensOrPriceMetadataAreMissing() {
        when(priceRepository
                .findByTenantIdAndProviderKeyIgnoreCaseAndModelNameIgnoreCaseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        any(), any(), any(), any()))
                .thenReturn(List.of());

        service.record("call-1", context(), ModelUsageStatus.SUCCESS, ModelUsageSnapshot.unknown(), 20L, null);

        ArgumentCaptor<ModelUsageEventEntity> eventCaptor = ArgumentCaptor.forClass(ModelUsageEventEntity.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEstimatedCost()).isNull();
        assertThat(eventCaptor.getValue().getCostSource()).isEqualTo(ModelCostSource.UNKNOWN);
    }

    @Test
    void ignoresARepeatedSettlementForTheSameLogicalCall() {
        when(eventRepository.findByCallKey("call-1")).thenReturn(List.of(mock(ModelUsageEventEntity.class)));

        service.record("call-1", context(), ModelUsageStatus.SUCCESS, ModelUsageSnapshot.unknown(), 10L, null);

        verify(eventRepository, never()).saveAndFlush(any());
    }

    private ModelUsageContext context() {
        return new ModelUsageContext(7L, 2L, ModelUsageScenario.CHAT,
                "openai-compatible", "gpt-test", "trace-1", false);
    }

    private ModelPriceVersionEntity price(String key, String input, String output) {
        return new ModelPriceVersionEntity(
                7L, "openai-compatible", "gpt-test", key,
                new BigDecimal(input), new BigDecimal(output), "USD", "MANUAL",
                NOW.minusSeconds(60), 2L, NOW.minusSeconds(60));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
