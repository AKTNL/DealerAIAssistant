package com.brand.agentpoc.modelusage.application;

import com.brand.agentpoc.modelusage.domain.ModelUsageContext;
import com.brand.agentpoc.modelusage.domain.ModelUsageSnapshot;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetReservationEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetReservationRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelUsageRecordingService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final ModelUsageEventRepository eventRepository;
    private final ModelPriceVersionRepository priceRepository;
    private final ModelBudgetReservationRepository reservationRepository;
    private final Clock clock;

    public ModelUsageRecordingService(
            ModelUsageEventRepository eventRepository,
            ModelPriceVersionRepository priceRepository,
            ModelBudgetReservationRepository reservationRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.priceRepository = priceRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String callKey,
            ModelUsageContext context,
            ModelUsageStatus status,
            ModelUsageSnapshot usage,
            long durationMs,
            Long reservationId
    ) {
        if (!eventRepository.findByCallKey(callKey).isEmpty()) {
            closeReservation(reservationId);
            return;
        }
        Instant now = Instant.now(clock);
        ModelPriceVersionEntity price = matchingPrice(context, now);
        BigDecimal estimatedCost = estimate(price, usage);
        eventRepository.saveAndFlush(new ModelUsageEventEntity(
                callKey,
                context.tenantId(),
                context.userId(),
                context.provider(),
                context.model(),
                context.scenario(),
                status,
                usage.tokenState(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                durationMs,
                context.traceId(),
                context.cacheHit(),
                price,
                estimatedCost,
                now
        ));
        closeReservation(reservationId);
    }

    private ModelPriceVersionEntity matchingPrice(ModelUsageContext context, Instant now) {
        List<ModelPriceVersionEntity> prices = priceRepository
                .findByTenantIdAndProviderKeyIgnoreCaseAndModelNameIgnoreCaseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        context.tenantId(), context.provider(), context.model(), now);
        return prices.isEmpty() ? null : prices.getFirst();
    }

    private BigDecimal estimate(ModelPriceVersionEntity price, ModelUsageSnapshot usage) {
        if (price == null || usage.inputTokens() == null || usage.outputTokens() == null) {
            return null;
        }
        BigDecimal input = price.getInputPricePerMillion().multiply(BigDecimal.valueOf(usage.inputTokens()));
        BigDecimal output = price.getOutputPricePerMillion().multiply(BigDecimal.valueOf(usage.outputTokens()));
        return input.add(output).divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }

    private void closeReservation(Long reservationId) {
        if (reservationId == null) {
            return;
        }
        reservationRepository.findById(reservationId).ifPresent(reservation -> close(reservation, Instant.now(clock)));
    }

    private void close(ModelBudgetReservationEntity reservation, Instant now) {
        reservation.close(now);
        reservationRepository.save(reservation);
    }
}
