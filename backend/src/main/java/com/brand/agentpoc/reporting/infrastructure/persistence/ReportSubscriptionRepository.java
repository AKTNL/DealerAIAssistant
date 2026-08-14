package com.brand.agentpoc.reporting.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
