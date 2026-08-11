package com.brand.agentpoc.organization.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationNodeRepository extends JpaRepository<OrganizationNodeEntity, Long> {
    List<OrganizationNodeEntity> findByNodeKeyIgnoreCase(String nodeKey);

    List<OrganizationNodeEntity> findByParentId(Long parentId);
}
