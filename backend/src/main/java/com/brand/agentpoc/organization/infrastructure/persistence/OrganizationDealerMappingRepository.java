package com.brand.agentpoc.organization.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationDealerMappingRepository extends JpaRepository<OrganizationDealerMappingEntity, Long> {
    List<OrganizationDealerMappingEntity> findByDealerCodeIgnoreCase(String dealerCode);

    List<OrganizationDealerMappingEntity> findByOrganizationNodeId(Long organizationNodeId);

    List<OrganizationDealerMappingEntity> findByTenantId(Long tenantId);

    List<OrganizationDealerMappingEntity> findByTenantIdAndDealerCodeIgnoreCase(Long tenantId, String dealerCode);

    List<OrganizationDealerMappingEntity> findByTenantIdAndOrganizationNodeId(Long tenantId, Long organizationNodeId);
}
