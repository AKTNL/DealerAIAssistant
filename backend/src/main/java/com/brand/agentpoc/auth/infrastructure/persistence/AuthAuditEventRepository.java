package com.brand.agentpoc.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEventEntity, Long> {
}
