package com.brand.agentpoc.organization.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUserGrantRepository extends JpaRepository<OrganizationUserGrantEntity, Long> {
    List<OrganizationUserGrantEntity> findByUserId(Long userId);

    List<OrganizationUserGrantEntity> findByTenantIdAndUserId(Long tenantId, Long userId);

    void deleteByUserId(Long userId);

    void deleteByTenantIdAndUserId(Long tenantId, Long userId);
}
