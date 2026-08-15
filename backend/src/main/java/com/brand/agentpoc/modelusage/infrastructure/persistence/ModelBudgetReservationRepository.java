package com.brand.agentpoc.modelusage.infrastructure.persistence;

import com.brand.agentpoc.modelusage.domain.BudgetReservationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelBudgetReservationRepository extends JpaRepository<ModelBudgetReservationEntity, Long> {
    List<ModelBudgetReservationEntity> findByTenantIdAndStatusAndExpiresAtAfter(
            Long tenantId,
            BudgetReservationStatus status,
            Instant expiresAt
    );

    long deleteByTenantIdAndStatusAndExpiresAtLessThanEqual(
            Long tenantId,
            BudgetReservationStatus status,
            Instant expiresAt
    );
}
