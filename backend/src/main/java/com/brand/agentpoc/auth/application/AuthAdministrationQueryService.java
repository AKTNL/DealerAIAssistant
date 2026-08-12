package com.brand.agentpoc.auth.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAdministrationQueryService {

    private static final int AUDIT_EVENT_LIMIT = 100;

    private final AuthUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthAuditEventRepository auditEventRepository;
    private final AuthSessionService sessionService;
    private final AuthAuditService auditService;
    private final Clock clock;

    public AuthAdministrationQueryService(
            AuthUserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthAuditEventRepository auditEventRepository,
            AuthSessionService sessionService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SessionView> listUserSessions(Long userId) {
        requireUser(userId);
        Instant now = Instant.now(clock);
        return sessionRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(AuthSessionEntity::getIssuedAt).reversed())
                .map(session -> SessionView.from(session, now))
                .toList();
    }

    @Transactional
    public List<SessionView> revokeUserSessions(AuthPrincipal actor, Long userId, String traceId) {
        requireUser(userId);
        sessionService.revokeAllForUser(userId, "administrator_revoked");
        auditService.record(actor.userId(), "USER_SESSIONS_REVOKE", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "all_sessions_revoked");
        return listUserSessions(userId);
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> listAuditEvents() {
        return auditEventRepository.findTop100ByOrderByCreatedAtDescIdDesc().stream()
                .limit(AUDIT_EVENT_LIMIT)
                .map(AuditEventView::from)
                .toList();
    }

    private void requireUser(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Unknown user.");
        }
    }

    public record SessionView(
            Long id,
            Instant issuedAt,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            Instant rotatedAt,
            Instant revokedAt,
            String revocationReason,
            boolean active
    ) {
        private static SessionView from(AuthSessionEntity session, Instant now) {
            return new SessionView(
                    session.getId(),
                    session.getIssuedAt(),
                    session.getAccessExpiresAt(),
                    session.getRefreshExpiresAt(),
                    session.getRotatedAt(),
                    session.getRevokedAt(),
                    session.getRevocationReason(),
                    session.getRevokedAt() == null && session.getRefreshExpiresAt().isAfter(now)
            );
        }
    }

    public record AuditEventView(
            Long id,
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            String outcome,
            String traceId,
            String detailCode,
            Instant createdAt
    ) {
        private static AuditEventView from(AuthAuditEventEntity event) {
            return new AuditEventView(
                    event.getId(),
                    event.getActorUserId(),
                    event.getAction(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getOutcome(),
                    event.getTraceId(),
                    event.getDetailCode(),
                    event.getCreatedAt()
            );
        }
    }
}
