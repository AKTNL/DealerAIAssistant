package com.brand.agentpoc.tenant.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    List<TenantEntity> findByTenantKeyIgnoreCase(String tenantKey);

    List<TenantEntity> findByEnabledTrueOrderByIdAsc();

    default Optional<TenantEntity> findOneByTenantKeyIgnoreCase(String tenantKey) {
        return findByTenantKeyIgnoreCase(tenantKey).stream().findFirst();
    }
}
