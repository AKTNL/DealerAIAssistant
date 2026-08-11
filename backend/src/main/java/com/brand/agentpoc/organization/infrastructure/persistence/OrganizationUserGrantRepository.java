package com.brand.agentpoc.organization.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUserGrantRepository extends JpaRepository<OrganizationUserGrantEntity, Long> {
    List<OrganizationUserGrantEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
