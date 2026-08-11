package com.brand.agentpoc.organization.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRoleGrantRepository extends JpaRepository<OrganizationRoleGrantEntity, Long> {
    List<OrganizationRoleGrantEntity> findByRoleIdIn(Collection<Long> roleIds);

    List<OrganizationRoleGrantEntity> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
