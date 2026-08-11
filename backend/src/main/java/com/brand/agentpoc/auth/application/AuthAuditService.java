package com.brand.agentpoc.auth.application;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAuditService {

    private final AuthAuditEventRepository repository;
    private final Clock clock;

    public AuthAuditService(AuthAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            String outcome,
            String traceId,
            String detailCode
    ) {
        repository.save(new AuthAuditEventEntity(
                actorUserId,
                action,
                targetType,
                targetId,
                outcome,
                safeTraceId(traceId),
                detailCode,
                Instant.now(clock)
        ));
    }

    private String safeTraceId(String traceId) {
        return traceId == null || traceId.isBlank() ? "unavailable" : traceId.trim();
    }
}
