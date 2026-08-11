package com.brand.agentpoc.organization.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationDealerMappingRepository extends JpaRepository<OrganizationDealerMappingEntity, Long> {
    List<OrganizationDealerMappingEntity> findByDealerCodeIgnoreCase(String dealerCode);

    List<OrganizationDealerMappingEntity> findByOrganizationNodeId(Long organizationNodeId);
}
