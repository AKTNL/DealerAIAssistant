package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryRequest;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryResult;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationNotificationEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationNotificationRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportCollaborationNotificationService {

    public static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(5);
    private static final Duration SECOND_RETRY_DELAY = Duration.ofMinutes(30);
    private static final Duration THIRD_RETRY_DELAY = Duration.ofHours(2);
    private static final int MAX_SUBJECT_LENGTH = 240;

    private final ReportCollaborationNotificationRepository notificationRepository;
    private final ReportCollaborationRepository collaborationRepository;
    private final ReportCollaborationEventRepository eventRepository;
    private final ReportDraftStore draftStore;
    private final ReportDeliveryPort deliveryPort;
    private final TenantMemberDirectory memberDirectory;
    private final TenantRepository tenantRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final AuthAuditService auditService;
    private final Clock clock;

    public ReportCollaborationNotificationService(
            ReportCollaborationNotificationRepository notificationRepository,
            ReportCollaborationRepository collaborationRepository,
            ReportCollaborationEventRepository eventRepository,
            ReportDraftStore draftStore,
            ReportDeliveryPort deliveryPort,
            TenantMemberDirectory memberDirectory,
            TenantRepository tenantRepository,
            OrganizationAuthorizationService authorizationService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.collaborationRepository = collaborationRepository;
        this.eventRepository = eventRepository;
        this.draftStore = draftStore;
        this.deliveryPort = deliveryPort;
        this.memberDirectory = memberDirectory;
        this.tenantRepository = tenantRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<NotificationView> materialize(
            ReportCollaborationEntity collaboration,
            ReportCollaborationEventEntity event,
            Long actorUserId,
            Instant requestedNow
    ) {
        if (collaboration == null || collaboration.getId() == null || event == null || event.getId() == null) {
            throw new IllegalArgumentException("Persisted collaboration event is required.");
        }
        Long recipientUserId = collaboration.getAssigneeUserId();
        if (recipientUserId == null || recipientUserId.equals(actorUserId)) {
            return Optional.empty();
        }
        String key = "report-collaboration:" + event.getId() + ":email:" + recipientUserId;
        ReportCollaborationNotificationEntity existing = notificationRepository.findByDeliveryKey(key).orElse(null);
        if (existing != null) {
            return Optional.of(toView(existing));
        }
        try {
            ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(
                    new ReportCollaborationNotificationEntity(
                            collaboration, event, recipientUserId, key, safeNow(requestedNow)));
            audit(saved, "REPORT_COLLABORATION_NOTIFICATION_CREATED", "notification_materialized");
            return Optional.of(toView(saved));
        } catch (DataIntegrityViolationException exception) {
            return notificationRepository.findByDeliveryKey(key).map(this::toView);
        }
    }

    public int recoverExpiredLeases(Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        int recovered = 0;
        for (ReportCollaborationNotificationEntity candidate : notificationRepository
                .findTop50ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAscIdAsc(
                        ReportDeliveryStatus.SENDING, now)) {
            ReportCollaborationNotificationEntity notification = candidate.getId() == null
                    ? null : notificationRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (notification != null && notification.leaseExpired(now)) {
                notification.recoverExpiredLease(now);
                ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(notification);
                audit(saved, "REPORT_COLLABORATION_NOTIFICATION_UNKNOWN", saved.getErrorCode());
                recovered++;
            }
        }
        return recovered;
    }

    public Optional<NotificationView> claimNext(String workerId, Instant requestedNow) {
        String owner = normalizeWorkerId(workerId);
        Instant now = safeNow(requestedNow);
        recoverExpiredLeases(now);
        List<ReportCollaborationNotificationEntity> candidates = new ArrayList<>();
        candidates.addAll(notificationRepository
                .findTop50ByStatusOrderByCreatedAtAscIdAsc(ReportDeliveryStatus.READY));
        candidates.addAll(notificationRepository
                .findTop50ByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAscIdAsc(
                        ReportDeliveryStatus.RETRY_WAIT, now));
        candidates.sort(Comparator.comparing(ReportCollaborationNotificationEntity::getCreatedAt)
                .thenComparing(ReportCollaborationNotificationEntity::getId));
        for (ReportCollaborationNotificationEntity candidate : candidates) {
            NotificationView claimed = tryClaim(candidate, owner, now);
            if (claimed != null) {
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }

    public NotificationView executeClaimed(Long notificationId, String workerId, Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        ReportCollaborationNotificationEntity notification = requireNotification(notificationId);
        if (notification.getStatus() != ReportDeliveryStatus.SENDING || !notification.ownedBy(workerId)) {
            throw new IllegalStateException("Collaboration notification is not owned by this worker.");
        }
        if (notification.leaseExpired(now)) {
            notification.recoverExpiredLease(now);
            ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(notification);
            audit(saved, "REPORT_COLLABORATION_NOTIFICATION_UNKNOWN", saved.getErrorCode());
            return toView(saved);
        }

        DeliveryRequest request;
        try {
            request = prepareRequest(notification);
        } catch (InactiveNotificationException exception) {
            notification.cancel(exception.errorCode(), now);
            ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(notification);
            audit(saved, "REPORT_COLLABORATION_NOTIFICATION_CANCELLED", saved.getErrorCode());
            return toView(saved);
        } catch (RuntimeException exception) {
            notification.markPermanentFailure("NOTIFICATION_PREPARATION_FAILED", now);
            ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(notification);
            audit(saved, "REPORT_COLLABORATION_NOTIFICATION_FAILED", saved.getErrorCode());
            return toView(saved);
        }

        DeliveryResult result;
        try {
            result = deliveryPort.deliver(request);
        } catch (RuntimeException exception) {
            result = DeliveryResult.unknown("SMTP_OUTCOME_UNKNOWN");
        }
        if (result == null || result.outcome() == null) {
            result = DeliveryResult.unknown("SMTP_OUTCOME_UNKNOWN");
        }
        applyResult(notification, result, now);
        ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(notification);
        auditResult(saved);
        return toView(saved);
    }

    private NotificationView tryClaim(
            ReportCollaborationNotificationEntity candidate,
            String owner,
            Instant now
    ) {
        if (candidate.getId() == null) {
            return null;
        }
        ReportCollaborationNotificationEntity notification = notificationRepository
                .findByIdForUpdate(candidate.getId()).orElse(null);
        if (notification == null || !notification.isClaimable(now)) {
            return null;
        }
        notification.claim(owner, now, now.plus(LEASE_DURATION));
        ReportCollaborationNotificationEntity saved = notificationRepository.saveAndFlush(notification);
        audit(saved, "REPORT_COLLABORATION_NOTIFICATION_CLAIMED", "notification_claimed");
        return toView(saved);
    }

    private DeliveryRequest prepareRequest(ReportCollaborationNotificationEntity notification) {
        boolean tenantEnabled = tenantRepository.findById(notification.getTenantId())
                .map(tenant -> Boolean.TRUE.equals(tenant.getEnabled()))
                .orElse(false);
        if (!tenantEnabled) {
            throw new InactiveNotificationException("TENANT_DISABLED");
        }
        ReportCollaborationEntity collaboration = collaborationRepository
                .findById(notification.getCollaborationId())
                .orElseThrow(() -> new InactiveNotificationException("COLLABORATION_NOT_FOUND"));
        if (!notification.getTenantId().equals(collaboration.getTenantId())
                || !notification.getRecipientUserId().equals(collaboration.getAssigneeUserId())) {
            throw new InactiveNotificationException("ASSIGNEE_CHANGED");
        }
        ReportCollaborationEventEntity event = eventRepository.findById(notification.getEventId())
                .orElseThrow(() -> new InactiveNotificationException("EVENT_NOT_FOUND"));
        ReportDraft draft = draftStore.findByTenantIdAndId(
                        notification.getTenantId(), collaboration.getReportDraftId())
                .orElseThrow(() -> new InactiveNotificationException("REPORT_NOT_FOUND"));
        AuthPrincipal recipient;
        try {
            recipient = memberDirectory.requireActivePrincipal(
                    notification.getTenantId(), notification.getRecipientUserId());
        } catch (AccessDeniedException exception) {
            throw new InactiveNotificationException("RECIPIENT_INACTIVE");
        }
        if (!recipient.hasPermission(PermissionKey.REPORT_READ)) {
            throw new InactiveNotificationException("RECIPIENT_PERMISSION_REVOKED");
        }
        OrganizationDataScope recipientScope = authorizationService.resolve(recipient).dataScope();
        if (!ReportAccessPolicy.canRead(draft, recipientScope)) {
            throw new InactiveNotificationException("ORGANIZATION_SCOPE_REVOKED");
        }
        String recipientEmail;
        try {
            recipientEmail = memberDirectory.requireEmail(
                    notification.getTenantId(), notification.getRecipientUserId());
        } catch (AccessDeniedException | IllegalStateException exception) {
            throw new InactiveNotificationException("RECIPIENT_EMAIL_UNAVAILABLE");
        }
        return new DeliveryRequest(
                notification.getTenantId(), recipientEmail, subject(draft), body(draft, event),
                notification.getDeliveryKey());
    }

    private void applyResult(
            ReportCollaborationNotificationEntity notification,
            DeliveryResult result,
            Instant now
    ) {
        switch (result.outcome()) {
            case SUCCEEDED -> notification.markSucceeded(result.providerMessageId(), now);
            case RETRYABLE_FAILURE -> notification.markRetry(
                    result.errorCode(), retryAt(notification, result.retryAt(), now), now);
            case PERMANENT_FAILURE -> notification.markPermanentFailure(result.errorCode(), now);
            case UNKNOWN -> notification.markUnknown(result.errorCode(), now);
        }
    }

    private Instant retryAt(
            ReportCollaborationNotificationEntity notification,
            Instant requestedRetryAt,
            Instant now
    ) {
        if (requestedRetryAt != null && requestedRetryAt.isAfter(now)) {
            return requestedRetryAt;
        }
        Duration delay = switch (notification.getAttempt()) {
            case 1 -> FIRST_RETRY_DELAY;
            case 2 -> SECOND_RETRY_DELAY;
            default -> THIRD_RETRY_DELAY;
        };
        int jitterSeconds = Math.floorMod(
                java.util.Objects.hash(notification.getDeliveryKey(), notification.getAttempt()), 60);
        return now.plus(delay).plusSeconds(jitterSeconds);
    }

    private ReportCollaborationNotificationEntity requireNotification(Long notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId is required.");
        }
        return notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Collaboration notification was not found."));
    }

    private String subject(ReportDraft draft) {
        String value = "[Dealer AI] Report collaboration: " + draft.title();
        return value.length() <= MAX_SUBJECT_LENGTH ? value : value.substring(0, MAX_SUBJECT_LENGTH);
    }

    private String body(ReportDraft draft, ReportCollaborationEventEntity event) {
        StringBuilder body = new StringBuilder()
                .append("Report: ").append(draft.title()).append('\n')
                .append("Event: ").append(event.getEventType().name()).append('\n')
                .append("Actor: ").append(event.getActorDisplayName()).append('\n')
                .append("Occurred at: ").append(DateTimeFormatter.ISO_INSTANT.format(event.getCreatedAt()));
        if (event.getPreviousValue() != null || event.getCurrentValue() != null) {
            body.append("\nChange: ")
                    .append(event.getPreviousValue() == null ? "-" : event.getPreviousValue())
                    .append(" -> ")
                    .append(event.getCurrentValue() == null ? "-" : event.getCurrentValue());
        }
        if (event.getCommentBody() != null) {
            body.append("\n\nComment:\n").append(event.getCommentBody());
        }
        return body.toString();
    }

    private String normalizeWorkerId(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required.");
        }
        String normalized = workerId.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("workerId is too long.");
        }
        return normalized;
    }

    private Instant safeNow(Instant requestedNow) {
        return requestedNow == null ? clock.instant() : requestedNow;
    }

    private void audit(
            ReportCollaborationNotificationEntity notification,
            String action,
            String detailCode
    ) {
        auditService.record(
                notification.getTenantId(), notification.getRecipientUserId(), action,
                "REPORT_COLLABORATION_NOTIFICATION", String.valueOf(notification.getId()),
                "SUCCESS", "report-collaboration-notification-" + notification.getId(), detailCode);
    }

    private void auditResult(ReportCollaborationNotificationEntity notification) {
        switch (notification.getStatus()) {
            case SUCCEEDED -> audit(notification, "REPORT_COLLABORATION_NOTIFICATION_SUCCEEDED", "smtp_accepted");
            case RETRY_WAIT -> audit(notification, "REPORT_COLLABORATION_NOTIFICATION_RETRY",
                    notification.getErrorCode());
            case PERMANENT_FAILURE -> audit(notification, "REPORT_COLLABORATION_NOTIFICATION_FAILED",
                    notification.getErrorCode());
            case UNKNOWN -> audit(notification, "REPORT_COLLABORATION_NOTIFICATION_UNKNOWN",
                    notification.getErrorCode());
            default -> audit(notification, "REPORT_COLLABORATION_NOTIFICATION_UPDATED",
                    notification.getStatus().name());
        }
    }

    private NotificationView toView(ReportCollaborationNotificationEntity notification) {
        return new NotificationView(
                notification.getId(), notification.getEventId(), notification.getRecipientUserId(),
                notification.getStatus(), notification.getAttempt(), notification.getMaxAttempts(),
                notification.getNextRetryAt(), notification.getErrorCode(), notification.getCreatedAt(),
                notification.getUpdatedAt(), notification.getVersion());
    }

    public record NotificationView(
            Long id,
            Long eventId,
            Long recipientUserId,
            ReportDeliveryStatus status,
            int attempt,
            int maxAttempts,
            Instant nextRetryAt,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    private static final class InactiveNotificationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String errorCode;

        private InactiveNotificationException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        private String errorCode() {
            return errorCode;
        }
    }
}
