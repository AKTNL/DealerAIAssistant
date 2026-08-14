package com.brand.agentpoc.reporting.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSmtpConfigRepository extends JpaRepository<TenantSmtpConfigEntity, Long> {

    List<TenantSmtpConfigEntity> findByTenantId(Long tenantId);
}
