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

public interface ReportCollaborationNotificationRepository
        extends JpaRepository<ReportCollaborationNotificationEntity, Long> {

    Optional<ReportCollaborationNotificationEntity> findByDeliveryKey(String deliveryKey);

    List<ReportCollaborationNotificationEntity> findTop50ByStatusOrderByCreatedAtAscIdAsc(
            ReportDeliveryStatus status
    );

    List<ReportCollaborationNotificationEntity>
            findTop50ByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAscIdAsc(
                    ReportDeliveryStatus status,
                    Instant now
            );

    List<ReportCollaborationNotificationEntity>
            findTop50ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAscIdAsc(
                    ReportDeliveryStatus status,
                    Instant now
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from ReportCollaborationNotificationEntity notification where notification.id = :id")
    Optional<ReportCollaborationNotificationEntity> findByIdForUpdate(@Param("id") Long id);
}
