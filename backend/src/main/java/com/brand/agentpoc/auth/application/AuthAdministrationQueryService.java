package com.brand.agentpoc.auth.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
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
    private final TenantMembershipRepository membershipRepository;

    public AuthAdministrationQueryService(
            AuthUserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthAuditEventRepository auditEventRepository,
            AuthSessionService sessionService,
            AuthAuditService auditService,
            Clock clock,
            TenantMembershipRepository membershipRepository
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<SessionView> listUserSessions(AuthPrincipal actor, Long userId) {
        requireExclusiveTenantMember(actor, userId);
        Instant now = Instant.now(clock);
        return sessionRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(AuthSessionEntity::getIssuedAt).reversed())
                .map(session -> SessionView.from(session, now))
                .toList();
    }

    @Transactional
    public List<SessionView> revokeUserSessions(AuthPrincipal actor, Long userId, String traceId) {
        requireExclusiveTenantMember(actor, userId);
        sessionService.revokeAllForUser(userId, "administrator_revoked");
        auditService.record(actor.tenantId(), actor.userId(), "USER_SESSIONS_REVOKE", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "all_sessions_revoked");
        return listUserSessions(actor, userId);
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> listAuditEvents(AuthPrincipal actor) {
        if (actor == null || !actor.hasTenantContext()) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant access denied.");
        }
        return auditEventRepository.findTop100ByTenantIdOrderByCreatedAtDescIdDesc(actor.tenantId()).stream()
                .limit(AUDIT_EVENT_LIMIT)
                .map(AuditEventView::from)
                .toList();
    }

    public List<AuditEventView> listAuditEvents() {
        return auditEventRepository.findTop100ByOrderByCreatedAtDescIdDesc().stream()
                .limit(AUDIT_EVENT_LIMIT)
                .map(AuditEventView::from)
                .toList();
    }

    private void requireExclusiveTenantMember(AuthPrincipal actor, Long userId) {
        if (actor == null || !actor.hasTenantContext()) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant access denied.");
        }
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Unknown user.");
        }
        if (membershipRepository.findByTenantIdAndUserId(actor.tenantId(), userId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user.");
        }
        if (membershipRepository.findByUserId(userId).size() != 1) {
            throw new IllegalStateException("Shared identity requires platform administration.");
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
