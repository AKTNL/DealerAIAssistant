package com.brand.agentpoc.tenant.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembershipEntity, Long> {

    List<TenantMembershipEntity> findByUserIdAndEnabledTrue(Long userId);

    List<TenantMembershipEntity> findByUserId(Long userId);

    List<TenantMembershipEntity> findByTenantIdAndEnabledTrue(Long tenantId);

    List<TenantMembershipEntity> findByTenantId(Long tenantId);

    List<TenantMembershipEntity> findByTenantIdAndUserId(Long tenantId, Long userId);
}
