package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportDeliveryRepository extends JpaRepository<ReportDeliveryEntity, Long> {

    Optional<ReportDeliveryEntity> findByDeliveryKey(String deliveryKey);

    List<ReportDeliveryEntity> findTop50ByStatusOrderByCreatedAtAscIdAsc(ReportDeliveryStatus status);

    List<ReportDeliveryEntity> findTop50ByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAscIdAsc(
            ReportDeliveryStatus status,
            Instant now
    );

    List<ReportDeliveryEntity> findTop50ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAscIdAsc(
            ReportDeliveryStatus status,
            Instant now
    );

    List<ReportDeliveryEntity> findTop100ByTenantIdAndCreatorUserIdOrderByCreatedAtDescIdDesc(
            Long tenantId,
            Long creatorUserId
    );

    List<ReportDeliveryEntity> findByReportJobIdOrderByRecipientUserIdAsc(Long reportJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from ReportDeliveryEntity delivery where delivery.id = :id")
    Optional<ReportDeliveryEntity> findByIdForUpdate(@Param("id") Long id);
}
