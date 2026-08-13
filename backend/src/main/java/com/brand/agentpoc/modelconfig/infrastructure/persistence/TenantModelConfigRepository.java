package com.brand.agentpoc.modelconfig.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModelConfigRepository extends JpaRepository<TenantModelConfigEntity, Long> {

    List<TenantModelConfigEntity> findByTenantId(Long tenantId);
}
