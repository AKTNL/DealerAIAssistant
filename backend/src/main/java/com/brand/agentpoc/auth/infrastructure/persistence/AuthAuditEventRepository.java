package com.brand.agentpoc.auth.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEventEntity, Long> {
    List<AuthAuditEventEntity> findTop100ByOrderByCreatedAtDescIdDesc();

    List<AuthAuditEventEntity> findTop100ByTenantIdOrderByCreatedAtDescIdDesc(Long tenantId);
}
