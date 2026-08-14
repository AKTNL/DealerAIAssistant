package com.brand.agentpoc.reporting.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportSubscriptionRepository extends JpaRepository<ReportSubscriptionEntity, Long> {

    List<ReportSubscriptionEntity> findByTenantIdAndCreatorUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long tenantId,
            Long creatorUserId
    );

    Optional<ReportSubscriptionEntity> findByTenantIdAndIdAndCreatorUserIdAndDeletedAtIsNull(
            Long tenantId,
            Long id,
            Long creatorUserId
    );

    boolean existsByTenantIdAndCreatorUserIdAndActiveConfigurationKey(
            Long tenantId,
            Long creatorUserId,
            String activeConfigurationKey
    );

    boolean existsByTenantIdAndCreatorUserIdAndActiveConfigurationKeyAndIdNot(
            Long tenantId,
            Long creatorUserId,
            String activeConfigurationKey,
            Long id
    );

    List<ReportSubscriptionEntity>
            findTop50ByEnabledTrueAndDeletedAtIsNullAndNextRunAtLessThanEqualOrderByNextRunAtAscIdAsc(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from ReportSubscriptionEntity subscription where subscription.id = :id")
    Optional<ReportSubscriptionEntity> findByIdForUpdate(@Param("id") Long id);
}
