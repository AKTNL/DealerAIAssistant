package com.brand.agentpoc.modelusage.application;

import com.brand.agentpoc.modelusage.domain.BudgetReservationStatus;
import com.brand.agentpoc.modelusage.domain.ModelBudgetExceededException;
import com.brand.agentpoc.modelusage.domain.ModelBudgetUnavailableException;
import com.brand.agentpoc.modelusage.domain.ModelUsageContext;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetReservationEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetReservationRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelBudgetAdmissionService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final ModelBudgetPolicyRepository policyRepository;
    private final ModelBudgetReservationRepository reservationRepository;
    private final ModelUsageEventRepository eventRepository;
    private final Clock clock;

    public ModelBudgetAdmissionService(
            ModelBudgetPolicyRepository policyRepository,
            ModelBudgetReservationRepository reservationRepository,
            ModelUsageEventRepository eventRepository,
            Clock clock
    ) {
        this.policyRepository = policyRepository;
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public BudgetAdmission admit(String callKey, ModelUsageContext context) {
        List<ModelBudgetPolicyEntity> policies = policyRepository.findByTenantIdOrderByIdAsc(context.tenantId());
        if (policies.isEmpty() || !Boolean.TRUE.equals(policies.getFirst().getHardLimitEnabled())) {
            return BudgetAdmission.none();
        }
        if (policies.size() > 1) {
            throw new IllegalStateException("Tenant model budget policy is not unique.");
        }
        ModelBudgetPolicyEntity policy = policies.getFirst();
        try {
            BigDecimal reservationAmount = policy.getReservationAmount();
            if (reservationAmount == null || reservationAmount.signum() <= 0) {
                throw new IllegalStateException("Hard model budget requires a positive reservation amount.");
            }
            Instant now = Instant.now(clock);
            reservationRepository.deleteByTenantIdAndStatusAndExpiresAtLessThanEqual(
                    context.tenantId(), BudgetReservationStatus.ACTIVE, now);
            BigDecimal spent = monthlySpend(context.tenantId(), policy.getCurrency(), now);
            BigDecimal reserved = activeReserved(context.tenantId(), policy.getCurrency(), now);
            if (spent.add(reserved).add(reservationAmount).compareTo(policy.getMonthlyLimit()) > 0) {
                throw new ModelBudgetExceededException();
            }
            ModelBudgetReservationEntity saved = reservationRepository.saveAndFlush(
                    new ModelBudgetReservationEntity(
                            callKey,
                            context.tenantId(),
                            reservationAmount,
                            policy.getCurrency(),
                            now,
                            now.plus(RESERVATION_TTL)
                    )
            );
            return new BudgetAdmission(saved.getId());
        } catch (ModelBudgetExceededException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (Boolean.TRUE.equals(policy.getFailOpen())) {
                return BudgetAdmission.none();
            }
            throw new ModelBudgetUnavailableException(exception);
        }
    }

    private BigDecimal monthlySpend(Long tenantId, String currency, Instant now) {
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        Instant start = utc.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        return eventRepository
                .findByTenantIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                        tenantId, start, now.plusMillis(1))
                .stream()
                .filter(event -> currency.equalsIgnoreCase(event.getCurrency()))
                .map(ModelUsageEventEntity::getEstimatedCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal activeReserved(Long tenantId, String currency, Instant now) {
        return reservationRepository.findByTenantIdAndStatusAndExpiresAtAfter(
                        tenantId, BudgetReservationStatus.ACTIVE, now).stream()
                .filter(reservation -> currency.equalsIgnoreCase(reservation.getCurrency()))
                .map(ModelBudgetReservationEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record BudgetAdmission(Long reservationId) {
        public static BudgetAdmission none() {
            return new BudgetAdmission(null);
        }
    }
}
