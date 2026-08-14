package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportGenerationJobRepository extends JpaRepository<ReportGenerationJobEntity, Long> {

    Optional<ReportGenerationJobEntity> findByIdempotencyKey(String idempotencyKey);

    List<ReportGenerationJobEntity> findTop50ByStatusOrderByScheduledAtAscIdAsc(
            ReportGenerationJobStatus status
    );

    List<ReportGenerationJobEntity> findTop50ByStatusAndNextRetryAtLessThanEqualOrderByScheduledAtAscIdAsc(
            ReportGenerationJobStatus status,
            Instant now
    );

    List<ReportGenerationJobEntity> findTop50ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAscIdAsc(
            ReportGenerationJobStatus status,
            Instant now
    );

    List<ReportGenerationJobEntity> findBySubscriptionIdAndStatusIn(
            Long subscriptionId,
            List<ReportGenerationJobStatus> statuses
    );

    List<ReportGenerationJobEntity> findTop100ByTenantIdAndCreatorUserIdOrderByCreatedAtDescIdDesc(
            Long tenantId,
            Long creatorUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ReportGenerationJobEntity job where job.id = :id")
    Optional<ReportGenerationJobEntity> findByIdForUpdate(@Param("id") Long id);
}
