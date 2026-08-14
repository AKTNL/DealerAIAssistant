package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportGenerationJobService {

    private static final int MAX_TRACE_ID_LENGTH = 128;
    public static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    public static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(5);
    public static final Duration SECOND_RETRY_DELAY = Duration.ofMinutes(30);
    public static final Duration THIRD_RETRY_DELAY = Duration.ofHours(2);

    private final ReportGenerationJobRepository jobRepository;
    private final ReportSubscriptionRepository subscriptionRepository;
    private final ReportSubscriptionService subscriptionService;
    private final ReportService reportService;
    private final ReportDeliveryService deliveryService;
    private final TenantMemberDirectory memberDirectory;
    private final TenantRepository tenantRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final AuthAuditService auditService;
    private final Clock clock;

    public ReportGenerationJobService(
            ReportGenerationJobRepository jobRepository,
            ReportSubscriptionRepository subscriptionRepository,
            ReportSubscriptionService subscriptionService,
            ReportService reportService,
            ReportDeliveryService deliveryService,
            TenantMemberDirectory memberDirectory,
            TenantRepository tenantRepository,
            OrganizationAuthorizationService organizationAuthorizationService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.deliveryService = deliveryService;
        this.memberDirectory = memberDirectory;
        this.tenantRepository = tenantRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Converts each currently due subscription into one durable window job and advances its cursor.
     * The subscription row is locked before the unique idempotency check so two instances cannot
     * advance the same cursor or create two jobs for one scheduled window.
     */
    public List<JobView> materializeDueSubscriptions(Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        List<JobView> materialized = new ArrayList<>();
        for (ReportSubscriptionEntity candidate : subscriptionRepository
                .findTop50ByEnabledTrueAndDeletedAtIsNullAndNextRunAtLessThanEqualOrderByNextRunAtAscIdAsc(now)) {
            if (candidate.getId() == null) {
                continue;
            }
            ReportSubscriptionEntity subscription = subscriptionRepository
                    .findByIdForUpdate(candidate.getId()).orElse(null);
            if (subscription == null || !Boolean.TRUE.equals(subscription.getEnabled())
                    || subscription.getDeletedAt() != null || subscription.getNextRunAt() == null
                    || subscription.getNextRunAt().isAfter(now)) {
                continue;
            }

            Instant scheduledAt = subscription.getNextRunAt();
            String idempotencyKey = idempotencyKey(subscription.getId(), scheduledAt);
            ReportGenerationJobEntity job = jobRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            boolean missed = Duration.between(scheduledAt, now)
                    .compareTo(Duration.ofMinutes(subscription.getMisfireGraceMinutes())) > 0;
            if (job == null) {
                job = new ReportGenerationJobEntity(
                        subscription.getId(),
                        subscription.getTenantId(),
                        subscription.getCreatorUserId(),
                        scheduledAt,
                        idempotencyKey,
                        subscription.getReportType(),
                        subscription.scope(),
                        subscription.getLanguage(),
                        subscription.getTopic(),
                        missed ? ReportGenerationJobStatus.SKIPPED : ReportGenerationJobStatus.READY,
                        traceId(),
                        now
                );
                if (missed) {
                    job.markSkipped("MISSED_WINDOW", now);
                }
                job = jobRepository.saveAndFlush(job);
                audit(subscription, job,
                        missed ? "REPORT_JOB_SKIPPED" : "REPORT_JOB_CREATED",
                        missed ? "MISSED_WINDOW" : "job_materialized");
            }

            Instant nextRunAt = missed
                    ? subscription.schedule().nextAfter(now)
                    : subscription.schedule().nextAfter(scheduledAt);
            subscription.advanceNextRunAt(nextRunAt, now);
            subscriptionRepository.saveAndFlush(subscription);
            materialized.add(toView(job));
        }
        return List.copyOf(materialized);
    }

    public int recoverExpiredLeases(Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        int recovered = 0;
        for (ReportGenerationJobEntity candidate : jobRepository
                .findTop50ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAscIdAsc(
                        ReportGenerationJobStatus.RUNNING, now)) {
            if (candidate.getId() == null) {
                continue;
            }
            ReportGenerationJobEntity job = jobRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (job != null && job.leaseExpired(now)) {
                job.recoverExpiredLease(now);
                jobRepository.saveAndFlush(job);
                boolean exhausted = job.getStatus() == ReportGenerationJobStatus.PERMANENT_FAILURE;
                audit(job,
                        exhausted ? "REPORT_JOB_FAILED" : "REPORT_JOB_LEASE_RECOVERED",
                        exhausted ? "RETRY_EXHAUSTED" : "lease_expired");
                recovered++;
            }
        }
        return recovered;
    }

    public Optional<JobView> claimNext(String workerId, Instant requestedNow) {
        String owner = normalizeWorkerId(workerId);
        Instant now = safeNow(requestedNow);
        recoverExpiredLeases(now);
        List<ReportGenerationJobEntity> candidates = new ArrayList<>();
        candidates.addAll(jobRepository.findTop50ByStatusOrderByScheduledAtAscIdAsc(ReportGenerationJobStatus.READY));
        candidates.addAll(jobRepository.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReportGenerationJobStatus.RETRY_WAIT, now));
        candidates.sort(Comparator.comparing(ReportGenerationJobEntity::getScheduledAt)
                .thenComparing(ReportGenerationJobEntity::getId));
        JobView claimedView = null;
        for (ReportGenerationJobEntity candidate : candidates) {
            if (claimedView == null) {
                claimedView = tryClaim(candidate, owner, now);
            }
        }
        return Optional.ofNullable(claimedView);
    }

    private JobView tryClaim(ReportGenerationJobEntity candidate, String owner, Instant now) {
        if (candidate.getId() == null) {
            return null;
        }
        ReportGenerationJobEntity job = jobRepository.findByIdForUpdate(candidate.getId()).orElse(null);
        if (job == null || !job.isClaimable(now)) {
            return null;
        }
        job.claim(owner, now, now.plus(LEASE_DURATION));
        ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
        audit(saved, "REPORT_JOB_CLAIMED", "job_claimed");
        return toView(saved);
    }

    public JobView executeClaimed(Long jobId, String workerId, Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        ReportGenerationJobEntity job = requireJob(jobId);
        if (job.getStatus() != ReportGenerationJobStatus.RUNNING || !job.ownedBy(workerId)) {
            throw new IllegalStateException("Report generation job is not owned by this worker.");
        }
        if (job.leaseExpired(now)) {
            job.recoverExpiredLease(now);
            jobRepository.saveAndFlush(job);
            audit(job, "REPORT_JOB_LEASE_RECOVERED", "lease_expired_before_execution");
            return toView(job);
        }

        try {
            if (tenantRepository.findById(job.getTenantId())
                    .filter(tenant -> Boolean.TRUE.equals(tenant.getEnabled())).isEmpty()) {
                job.cancel("TENANT_DISABLED", now);
                ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
                audit(saved, "REPORT_JOB_CANCELLED", "TENANT_DISABLED");
                return toView(saved);
            }
            ReportSubscriptionService.ExecutionEligibility eligibility =
                    subscriptionService.evaluateExecutionEligibility(job.getSubscriptionId());
            if (!eligibility.eligible()) {
                return rejectForEligibility(job, eligibility.reason(), now);
            }
            AuthPrincipal creator = memberDirectory.requireActivePrincipal(
                    job.getTenantId(), job.getCreatorUserId());
            OrganizationDataScope scope = organizationAuthorizationService.resolve(creator).dataScope();
            scope.requireDataAccess();
            ReportDraft draft = reportService.generate(new ReportGenerationRequest(
                    job.getReportType(), job.getLanguage(), job.getScope().type(), job.getScope().id(), job.getTopic()), scope);
            deliveryService.materialize(job, draft, now);
            job.markSucceeded(draft.id(), now);
            ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
            audit(saved, "REPORT_JOB_SUCCEEDED", "report_generated");
            return toView(saved);
        } catch (AccessDeniedException | IllegalArgumentException | NoSuchElementException exception) {
            job.markPermanentFailure(errorCode(exception), now);
            ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
            audit(saved, "REPORT_JOB_FAILED", saved.getErrorCode());
            return toView(saved);
        } catch (Exception exception) {
            if (isTransient(exception) && job.getAttempt() < job.getMaxAttempts()) {
                Duration delay = retryDelay(job.getAttempt());
                job.markRetry("TRANSIENT_FAILURE", now.plus(delay), now);
                ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
                audit(saved, "REPORT_JOB_RETRY", "retry_scheduled");
                return toView(saved);
            }
            job.markPermanentFailure(
                    isTransient(exception) ? "RETRY_EXHAUSTED" : "GENERATION_FAILED", now);
            ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
            audit(saved, "REPORT_JOB_FAILED", saved.getErrorCode());
            return toView(saved);
        }
    }

    public List<JobView> cancelForDisabledSubscription(Long subscriptionId, Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        List<JobView> cancelled = new ArrayList<>();
        for (ReportGenerationJobEntity candidate : jobRepository.findBySubscriptionIdAndStatusIn(
                subscriptionId, List.of(ReportGenerationJobStatus.READY, ReportGenerationJobStatus.RETRY_WAIT))) {
            if (candidate.getId() == null) {
                continue;
            }
            ReportGenerationJobEntity job = jobRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (job == null || (job.getStatus() != ReportGenerationJobStatus.READY
                    && job.getStatus() != ReportGenerationJobStatus.RETRY_WAIT)) {
                continue;
            }
            job.cancel("SUBSCRIPTION_DISABLED", now);
            ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
            audit(saved, "REPORT_JOB_CANCELLED", "SUBSCRIPTION_DISABLED");
            cancelled.add(toView(saved));
        }
        return List.copyOf(cancelled);
    }

    public int cancelPendingJobsForInactiveSubscriptions(Instant requestedNow) {
        Instant now = safeNow(requestedNow);
        List<ReportGenerationJobEntity> pending = new ArrayList<>();
        pending.addAll(jobRepository.findTop50ByStatusOrderByScheduledAtAscIdAsc(ReportGenerationJobStatus.READY));
        pending.addAll(jobRepository.findTop50ByStatusOrderByScheduledAtAscIdAsc(
                ReportGenerationJobStatus.RETRY_WAIT));
        int cancelled = 0;
        for (ReportGenerationJobEntity candidate : pending) {
            if (candidate.getId() != null && cancelIfSubscriptionInactive(candidate.getId(), now)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancelIfSubscriptionInactive(Long jobId, Instant now) {
        ReportGenerationJobEntity job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || (job.getStatus() != ReportGenerationJobStatus.READY
                && job.getStatus() != ReportGenerationJobStatus.RETRY_WAIT)) {
            return false;
        }
        ReportSubscriptionEntity subscription = subscriptionRepository.findById(job.getSubscriptionId()).orElse(null);
        if (subscription != null && Boolean.TRUE.equals(subscription.getEnabled())
                && subscription.getDeletedAt() == null) {
            return false;
        }
        job.cancel("SUBSCRIPTION_DISABLED", now);
        ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
        audit(saved, "REPORT_JOB_CANCELLED", "SUBSCRIPTION_DISABLED");
        return true;
    }

    @Transactional(readOnly = true)
    public List<JobView> list(AuthPrincipal actor, OrganizationDataScope dataScope) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        return jobRepository.findTop100ByTenantIdAndCreatorUserIdOrderByCreatedAtDescIdDesc(
                        actor.tenantId(), actor.userId()).stream()
                .map(this::toView)
                .toList();
    }

    public JobView manualRetry(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            Long jobId,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        ReportGenerationJobEntity job = requireJob(jobId);
        if (!actor.tenantId().equals(job.getTenantId()) || !actor.userId().equals(job.getCreatorUserId())) {
            throw new NoSuchElementException("Report generation job was not found.");
        }
        job.manualRetry(safeNow(null), safeTraceId(traceId));
        ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
        audit(saved, "REPORT_JOB_REPLAY", "manual_retry");
        return toView(saved);
    }

    private JobView rejectForEligibility(
            ReportGenerationJobEntity job,
            String reason,
            Instant now
    ) {
        String code = switch (reason) {
            case "subscription_deleted", "subscription_disabled" -> "SUBSCRIPTION_DISABLED";
            case "report_permission_revoked" -> "REPORT_PERMISSION_REVOKED";
            case "organization_scope_revoked" -> "ORGANIZATION_SCOPE_REVOKED";
            case "membership_or_recipient_revoked" -> "MEMBERSHIP_OR_RECIPIENT_REVOKED";
            default -> "EXECUTION_NOT_ELIGIBLE";
        };
        if ("SUBSCRIPTION_DISABLED".equals(code)) {
            job.cancel(code, now);
            audit(job, "REPORT_JOB_CANCELLED", code);
        } else {
            job.markPermanentFailure(code, now);
            audit(job, "REPORT_JOB_FAILED", code);
        }
        ReportGenerationJobEntity saved = jobRepository.saveAndFlush(job);
        return toView(saved);
    }

    private ReportGenerationJobEntity requireJob(Long jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required.");
        }
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new NoSuchElementException("Report generation job was not found."));
    }

    private void requireActor(AuthPrincipal actor, OrganizationDataScope dataScope, PermissionKey permission) {
        if (actor == null || !actor.enabled() || !actor.hasTenantContext() || !actor.hasPermission(permission)) {
            throw new AccessDeniedException("Report generation job access denied.");
        }
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireTenant();
        if (!actor.tenantId().equals(requiredScope.tenantId())) {
            throw new AccessDeniedException("Report generation job tenant scope does not match the current tenant.");
        }
    }

    private void audit(ReportSubscriptionEntity subscription, ReportGenerationJobEntity job,
                       String action, String detailCode) {
        auditService.record(subscription.getTenantId(), subscription.getCreatorUserId(), action,
                "REPORT_GENERATION_JOB", String.valueOf(job.getId()), "SUCCESS", job.getTraceId(), detailCode);
    }

    private void audit(ReportGenerationJobEntity job, String action, String detailCode) {
        auditService.record(job.getTenantId(), job.getCreatorUserId(), action,
                "REPORT_GENERATION_JOB", String.valueOf(job.getId()), "SUCCESS", job.getTraceId(), detailCode);
    }

    private JobView toView(ReportGenerationJobEntity job) {
        return new JobView(
                job.getId(), job.getSubscriptionId(), job.getTenantId(), job.getCreatorUserId(),
                job.getScheduledAt(), job.getIdempotencyKey(), job.getReportType(), job.getScope(),
                job.getLanguage(), job.getTopic(), job.getStatus(), job.getAttempt(), job.getMaxAttempts(),
                job.getLeaseOwner(), job.getLeaseExpiresAt(), job.getNextRetryAt(), job.getErrorCode(),
                job.getTraceId(), job.getReportDraftId(), job.getCreatedAt(), job.getUpdatedAt(), job.getVersion());
    }

    private String idempotencyKey(Long subscriptionId, Instant scheduledAt) {
        return subscriptionId + ":" + scheduledAt.toString();
    }

    private String errorCode(Exception exception) {
        if (exception instanceof AccessDeniedException) {
            return "EXECUTION_NOT_AUTHORIZED";
        }
        if (exception instanceof NoSuchElementException) {
            return "SUBSCRIPTION_NOT_FOUND";
        }
        return "VALIDATION_FAILED";
    }

    private boolean isTransient(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TransientDataAccessException
                    || current instanceof QueryTimeoutException
                    || current instanceof SocketTimeoutException
                    || (current instanceof DataAccessException
                        && !(current instanceof DataIntegrityViolationException))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration retryDelay(int attempt) {
        return switch (attempt) {
            case 1 -> FIRST_RETRY_DELAY;
            case 2 -> SECOND_RETRY_DELAY;
            default -> THIRD_RETRY_DELAY;
        };
    }

    private Instant safeNow(Instant requestedNow) {
        return requestedNow == null ? clock.instant() : requestedNow;
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

    private String traceId() {
        return "report-job-" + UUID.randomUUID();
    }

    private String safeTraceId(String value) {
        if (value == null || value.isBlank()) {
            return traceId();
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_TRACE_ID_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TRACE_ID_LENGTH);
    }

    public record JobView(
            Long id,
            Long subscriptionId,
            Long tenantId,
            Long creatorUserId,
            Instant scheduledAt,
            String idempotencyKey,
            String reportType,
            com.brand.agentpoc.reporting.domain.ReportScope scope,
            String language,
            String topic,
            ReportGenerationJobStatus status,
            int attempt,
            int maxAttempts,
            String leaseOwner,
            Instant leaseExpiresAt,
            Instant nextRetryAt,
            String errorCode,
            String traceId,
            String reportDraftId,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }
}
