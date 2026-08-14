package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryRequest;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryResult;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportDeliveryEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportDeliveryRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.io.Serial;
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
public class ReportDeliveryService {

    public static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(5);
    private static final Duration SECOND_RETRY_DELAY = Duration.ofMinutes(30);
    private static final Duration THIRD_RETRY_DELAY = Duration.ofHours(2);
    private static final int MAX_SUBJECT_LENGTH = 240;
    private static final String EMAIL_CHANNEL = "email";

    private final ReportDeliveryRepository deliveryRepository;
    private final ReportSubscriptionRepository subscriptionRepository;
    private final ReportSubscriptionService subscriptionService;
    private final ReportDraftStore draftStore;
    private final ReportDeliveryPort deliveryPort;
    private final TenantMemberDirectory memberDirectory;
    private final TenantRepository tenantRepository;
    private final AuthAuditService auditService;
    private final Clock clock;

    public ReportDeliveryService(
            ReportDeliveryRepository deliveryRepository,
            ReportSubscriptionRepository subscriptionRepository,
            ReportSubscriptionService subscriptionService,
            ReportDraftStore draftStore,
            ReportDeliveryPort deliveryPort,
            TenantMemberDirectory memberDirectory,
            TenantRepository tenantRepository,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.draftStore = draftStore;
        this.deliveryPort = deliveryPort;
        this.memberDirectory = memberDirectory;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<DeliveryView> materialize(
            ReportGenerationJobEntity job,
            ReportDraft draft,
            Instant requestedNow
    ) {
        if (job == null || job.getId() == null || draft == null) {
            throw new IllegalArgumentException("Completed report job and draft are required.");
        }
        if (!job.getTenantId().equals(draft.tenantId())) {
            throw new AccessDeniedException("Report draft tenant does not match the delivery job.");
        }
        ReportSubscriptionEntity subscription = subscriptionRepository.findById(job.getSubscriptionId())
                .orElseThrow(() -> new NoSuchElementException("Report subscription was not found."));
        if (!job.getTenantId().equals(subscription.getTenantId())
                || !EMAIL_CHANNEL.equals(subscription.getChannelKey())) {
            throw new IllegalStateException("Report subscription channel is not executable.");
        }
        Instant now = safeNow(requestedNow);
        List<DeliveryView> deliveries = new ArrayList<>();
        for (Long recipientUserId : subscription.getRecipientUserIds().stream().sorted().toList()) {
            String key = deliveryKey(job.getId(), recipientUserId);
            ReportDeliveryEntity delivery = deliveryRepository.findByDeliveryKey(key).orElse(null);
            if (delivery == null) {
                delivery = new ReportDeliveryEntity(
                        job.getId(), subscription.getId(), job.getTenantId(), job.getCreatorUserId(),
                        draft.id(), recipientUserId, EMAIL_CHANNEL, key, now);
                try {
                    delivery = deliveryRepository.saveAndFlush(delivery);
                    audit(delivery, "REPORT_DELIVERY_CREATED", "delivery_materialized");
                } catch (DataIntegrityViolationException exception) {
                    delivery = deliveryRepository.findByDeliveryKey(key).orElseThrow(() -> exception);
                }
            }
            deliveries.add(toView(delivery));
        }
        return List.copyOf(deliveries);
    }

    public int recoverExpiredLeases(Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        int recovered = 0;
        for (ReportDeliveryEntity candidate : deliveryRepository
                .findTop50ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAscIdAsc(
                        ReportDeliveryStatus.SENDING, now)) {
            if (candidate.getId() == null) {
                continue;
            }
            ReportDeliveryEntity delivery = deliveryRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (delivery != null && delivery.leaseExpired(now)) {
                delivery.recoverExpiredLease(now);
                ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
                audit(saved, "REPORT_DELIVERY_UNKNOWN", saved.getErrorCode());
                recovered++;
            }
        }
        return recovered;
    }

    public Optional<DeliveryView> claimNext(String workerId, Instant requestedNow) {
        String owner = normalizeWorkerId(workerId);
        Instant now = safeNow(requestedNow);
        recoverExpiredLeases(now);
        List<ReportDeliveryEntity> candidates = new ArrayList<>();
        candidates.addAll(deliveryRepository.findTop50ByStatusOrderByCreatedAtAscIdAsc(ReportDeliveryStatus.READY));
        candidates.addAll(deliveryRepository
                .findTop50ByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAscIdAsc(
                        ReportDeliveryStatus.RETRY_WAIT, now));
        candidates.sort(Comparator.comparing(ReportDeliveryEntity::getCreatedAt)
                .thenComparing(ReportDeliveryEntity::getId));
        for (ReportDeliveryEntity candidate : candidates) {
            DeliveryView claimed = tryClaim(candidate, owner, now);
            if (claimed != null) {
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }

    private DeliveryView tryClaim(ReportDeliveryEntity candidate, String owner, Instant now) {
        if (candidate.getId() == null) {
            return null;
        }
        ReportDeliveryEntity delivery = deliveryRepository.findByIdForUpdate(candidate.getId()).orElse(null);
        if (delivery == null || !delivery.isClaimable(now)) {
            return null;
        }
        delivery.claim(owner, now, now.plus(LEASE_DURATION));
        ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
        audit(saved, "REPORT_DELIVERY_CLAIMED", "delivery_claimed");
        return toView(saved);
    }

    public DeliveryView executeClaimed(Long deliveryId, String workerId, Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        ReportDeliveryEntity delivery = requireDelivery(deliveryId);
        if (delivery.getStatus() != ReportDeliveryStatus.SENDING || !delivery.ownedBy(workerId)) {
            throw new IllegalStateException("Report delivery is not owned by this worker.");
        }
        if (delivery.leaseExpired(now)) {
            delivery.recoverExpiredLease(now);
            ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
            audit(saved, "REPORT_DELIVERY_UNKNOWN", saved.getErrorCode());
            return toView(saved);
        }

        DeliveryRequest request;
        try {
            request = prepareRequest(delivery);
        } catch (InactiveDeliveryException exception) {
            delivery.cancel(exception.errorCode(), now);
            ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
            audit(saved, "REPORT_DELIVERY_CANCELLED", saved.getErrorCode());
            return toView(saved);
        } catch (RuntimeException exception) {
            delivery.markPermanentFailure("DELIVERY_PREPARATION_FAILED", now);
            ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
            audit(saved, "REPORT_DELIVERY_FAILED", saved.getErrorCode());
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
        applyResult(delivery, result, now);
        ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
        auditResult(saved);
        return toView(saved);
    }

    private DeliveryRequest prepareRequest(ReportDeliveryEntity delivery) {
        boolean tenantEnabled = tenantRepository.findById(delivery.getTenantId())
                .map(tenant -> Boolean.TRUE.equals(tenant.getEnabled()))
                .orElse(false);
        if (!tenantEnabled) {
            throw new InactiveDeliveryException("TENANT_DISABLED");
        }
        ReportSubscriptionEntity subscription = subscriptionRepository.findById(delivery.getSubscriptionId())
                .orElseThrow(() -> new InactiveDeliveryException("SUBSCRIPTION_NOT_FOUND"));
        if (!delivery.getTenantId().equals(subscription.getTenantId())
                || subscription.getDeletedAt() != null
                || !Boolean.TRUE.equals(subscription.getEnabled())) {
            throw new InactiveDeliveryException("SUBSCRIPTION_DISABLED");
        }
        if (!EMAIL_CHANNEL.equals(subscription.getChannelKey())
                || !subscription.getRecipientUserIds().contains(delivery.getRecipientUserId())) {
            throw new InactiveDeliveryException("RECIPIENT_REMOVED");
        }
        ReportSubscriptionService.ExecutionEligibility eligibility =
                subscriptionService.evaluateExecutionEligibility(subscription.getId());
        if (!eligibility.eligible()) {
            throw new InactiveDeliveryException(eligibilityErrorCode(eligibility.reason()));
        }
        AuthPrincipal recipient = memberDirectory.requireActivePrincipal(
                delivery.getTenantId(), delivery.getRecipientUserId());
        if (!recipient.hasPermission(PermissionKey.REPORT_READ)) {
            throw new InactiveDeliveryException("RECIPIENT_PERMISSION_REVOKED");
        }
        String recipientEmail;
        try {
            recipientEmail = memberDirectory.requireEmail(delivery.getTenantId(), delivery.getRecipientUserId());
        } catch (AccessDeniedException | IllegalStateException exception) {
            throw new InactiveDeliveryException("RECIPIENT_EMAIL_UNAVAILABLE");
        }
        ReportDraft draft = draftStore.findByTenantIdAndId(delivery.getTenantId(), delivery.getReportDraftId())
                .orElseThrow(() -> new IllegalStateException("Report draft was not found."));
        return new DeliveryRequest(
                delivery.getTenantId(), recipientEmail, subject(draft), body(draft), delivery.getDeliveryKey());
    }

    private void applyResult(ReportDeliveryEntity delivery, DeliveryResult result, Instant now) {
        switch (result.outcome()) {
            case SUCCEEDED -> delivery.markSucceeded(result.providerMessageId(), now);
            case RETRYABLE_FAILURE -> delivery.markRetry(
                    result.errorCode(), retryAt(delivery, result.retryAt(), now), now);
            case PERMANENT_FAILURE -> delivery.markPermanentFailure(result.errorCode(), now);
            case UNKNOWN -> delivery.markUnknown(result.errorCode(), now);
        }
    }

    private Instant retryAt(ReportDeliveryEntity delivery, Instant requestedRetryAt, Instant now) {
        if (requestedRetryAt != null && requestedRetryAt.isAfter(now)) {
            return requestedRetryAt;
        }
        Duration delay = switch (delivery.getAttempt()) {
            case 1 -> FIRST_RETRY_DELAY;
            case 2 -> SECOND_RETRY_DELAY;
            default -> THIRD_RETRY_DELAY;
        };
        int jitterSeconds = Math.floorMod(
                java.util.Objects.hash(delivery.getDeliveryKey(), delivery.getAttempt()), 60);
        return now.plus(delay).plusSeconds(jitterSeconds);
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> list(AuthPrincipal actor, OrganizationDataScope dataScope) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        return deliveryRepository.findTop100ByTenantIdAndCreatorUserIdOrderByCreatedAtDescIdDesc(
                        actor.tenantId(), actor.userId()).stream()
                .map(this::toView)
                .toList();
    }

    public DeliveryView manualRetry(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            Long deliveryId,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        ReportDeliveryEntity delivery = requireOwned(actor, deliveryId);
        delivery.manualRetry(safeNow(null));
        ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
        auditService.record(saved.getTenantId(), actor.userId(), "REPORT_DELIVERY_REPLAY",
                "REPORT_DELIVERY", String.valueOf(saved.getId()), "SUCCESS", traceId, "manual_retry");
        return toView(saved);
    }

    public DeliveryView forceReplay(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            Long deliveryId,
            boolean acknowledgeDuplicateRisk,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        if (!acknowledgeDuplicateRisk) {
            throw new IllegalArgumentException("Duplicate delivery risk must be acknowledged.");
        }
        ReportDeliveryEntity delivery = requireOwned(actor, deliveryId);
        delivery.forceReplay(safeNow(null));
        ReportDeliveryEntity saved = deliveryRepository.saveAndFlush(delivery);
        auditService.record(saved.getTenantId(), actor.userId(), "REPORT_DELIVERY_FORCE_REPLAY",
                "REPORT_DELIVERY", String.valueOf(saved.getId()), "SUCCESS", traceId,
                "duplicate_risk_acknowledged");
        return toView(saved);
    }

    private ReportDeliveryEntity requireOwned(AuthPrincipal actor, Long deliveryId) {
        ReportDeliveryEntity delivery = requireDelivery(deliveryId);
        if (!actor.tenantId().equals(delivery.getTenantId())
                || !actor.userId().equals(delivery.getCreatorUserId())) {
            throw new NoSuchElementException("Report delivery was not found.");
        }
        return delivery;
    }

    private ReportDeliveryEntity requireDelivery(Long deliveryId) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("deliveryId is required.");
        }
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new NoSuchElementException("Report delivery was not found."));
    }

    private void requireActor(AuthPrincipal actor, OrganizationDataScope dataScope, PermissionKey permission) {
        if (actor == null || !actor.enabled() || !actor.hasTenantContext() || !actor.hasPermission(permission)) {
            throw new AccessDeniedException("Report delivery access denied.");
        }
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireTenant();
        if (!actor.tenantId().equals(requiredScope.tenantId())) {
            throw new AccessDeniedException("Report delivery tenant scope does not match the current tenant.");
        }
    }

    private String subject(ReportDraft draft) {
        String value = "[Dealer AI] " + draft.title();
        return value.length() <= MAX_SUBJECT_LENGTH ? value : value.substring(0, MAX_SUBJECT_LENGTH);
    }

    private String body(ReportDraft draft) {
        return "Title: " + draft.title()
                + "\nReport type: " + draft.reportType().wireName()
                + "\nGenerated at: " + DateTimeFormatter.ISO_INSTANT.format(draft.generatedAt())
                + "\n\n" + draft.markdown();
    }

    private String eligibilityErrorCode(String reason) {
        return switch (reason) {
            case "subscription_deleted", "subscription_disabled" -> "SUBSCRIPTION_DISABLED";
            case "report_permission_revoked" -> "CREATOR_PERMISSION_REVOKED";
            case "organization_scope_revoked" -> "ORGANIZATION_SCOPE_REVOKED";
            case "recipient_email_missing" -> "RECIPIENT_EMAIL_UNAVAILABLE";
            case "unsupported_channel" -> "DELIVERY_CHANNEL_UNSUPPORTED";
            default -> "RECIPIENT_INELIGIBLE";
        };
    }

    private String deliveryKey(Long jobId, Long recipientUserId) {
        return "report-delivery:" + jobId + ":" + EMAIL_CHANNEL + ":" + recipientUserId;
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

    private void audit(ReportDeliveryEntity delivery, String action, String detailCode) {
        auditService.record(delivery.getTenantId(), delivery.getCreatorUserId(), action,
                "REPORT_DELIVERY", String.valueOf(delivery.getId()), "SUCCESS",
                "report-delivery-" + delivery.getId(), detailCode);
    }

    private void auditResult(ReportDeliveryEntity delivery) {
        switch (delivery.getStatus()) {
            case SUCCEEDED -> audit(delivery, "REPORT_DELIVERY_SUCCEEDED", "smtp_accepted");
            case RETRY_WAIT -> audit(delivery, "REPORT_DELIVERY_RETRY", delivery.getErrorCode());
            case PERMANENT_FAILURE -> audit(delivery, "REPORT_DELIVERY_FAILED", delivery.getErrorCode());
            case UNKNOWN -> audit(delivery, "REPORT_DELIVERY_UNKNOWN", delivery.getErrorCode());
            default -> audit(delivery, "REPORT_DELIVERY_UPDATED", delivery.getStatus().name());
        }
    }

    private DeliveryView toView(ReportDeliveryEntity delivery) {
        return new DeliveryView(
                delivery.getId(), delivery.getReportJobId(), delivery.getSubscriptionId(),
                delivery.getRecipientUserId(), delivery.getChannelKey(), delivery.getStatus(),
                delivery.getAttempt(), delivery.getMaxAttempts(), delivery.getNextRetryAt(),
                delivery.getErrorCode(), delivery.getCreatedAt(), delivery.getUpdatedAt(), delivery.getVersion());
    }

    public record DeliveryView(
            Long id,
            Long reportJobId,
            Long subscriptionId,
            Long recipientUserId,
            String channelKey,
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

    private static final class InactiveDeliveryException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String errorCode;

        private InactiveDeliveryException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        private String errorCode() {
            return errorCode;
        }
    }
}
