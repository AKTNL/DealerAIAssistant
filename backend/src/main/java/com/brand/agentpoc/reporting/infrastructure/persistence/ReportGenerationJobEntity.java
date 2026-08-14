package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
        name = "report_generation_jobs",
        indexes = {
            @Index(name = "idx_report_generation_jobs_claim", columnList = "status,next_retry_at,scheduled_at,id"),
            @Index(name = "idx_report_generation_jobs_tenant", columnList = "tenant_id,created_at,id"),
            @Index(name = "idx_report_generation_jobs_subscription", columnList = "subscription_id,scheduled_at")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_report_generation_jobs_window",
                    columnNames = {"subscription_id", "scheduled_at"}
            ),
            @UniqueConstraint(
                    name = "uq_report_generation_jobs_idempotency",
                    columnNames = "idempotency_key"
            )
        }
)
public class ReportGenerationJobEntity {

    public static final int DEFAULT_MAX_ATTEMPTS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "idempotency_key", nullable = false, length = 192)
    private String idempotencyKey;

    @Column(name = "report_type", nullable = false, length = 16)
    private String reportType;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_id", nullable = false, length = 2048)
    private String scopeId;

    @Column(nullable = false, length = 8)
    private String language;

    @Column(nullable = false, length = 500)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportGenerationJobStatus status;

    @Column(nullable = false)
    private Integer attempt;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "report_draft_id", length = 128)
    private String reportDraftId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReportGenerationJobEntity() {
    }

    public ReportGenerationJobEntity(
            Long subscriptionId,
            Long tenantId,
            Long creatorUserId,
            Instant scheduledAt,
            String idempotencyKey,
            String reportType,
            ReportScope scope,
            String language,
            String topic,
            ReportGenerationJobStatus status,
            String traceId,
            Instant now
    ) {
        this.subscriptionId = required(subscriptionId, "subscriptionId");
        this.tenantId = required(tenantId, "tenantId");
        this.creatorUserId = required(creatorUserId, "creatorUserId");
        this.scheduledAt = required(scheduledAt, "scheduledAt");
        this.idempotencyKey = requiredText(idempotencyKey, "idempotencyKey");
        this.reportType = requiredText(reportType, "reportType");
        ReportScope safeScope = scope == null ? ReportScope.global() : scope;
        this.scopeType = requiredText(safeScope.type(), "scopeType");
        this.scopeId = requiredText(safeScope.id(), "scopeId");
        this.language = requiredText(language, "language");
        this.topic = topic == null ? "" : topic.trim();
        this.status = status == null ? ReportGenerationJobStatus.READY : status;
        this.attempt = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.traceId = requiredText(traceId, "traceId");
        this.createdAt = required(now, "createdAt");
        this.updatedAt = this.createdAt;
    }

    public void claim(String owner, Instant now, Instant leaseExpiresAt) {
        if (!isClaimable(now)) {
            throw new IllegalStateException("Report generation job is not claimable.");
        }
        this.status = ReportGenerationJobStatus.RUNNING;
        this.attempt++;
        this.leaseOwner = requiredText(owner, "leaseOwner");
        this.leaseExpiresAt = required(leaseExpiresAt, "leaseExpiresAt");
        this.updatedAt = required(now, "updatedAt");
    }

    public boolean isClaimable(Instant now) {
        if (now == null) {
            return false;
        }
        if (status == ReportGenerationJobStatus.READY) {
            return attempt < maxAttempts;
        }
        if (status == ReportGenerationJobStatus.RETRY_WAIT) {
            return attempt < maxAttempts && (nextRetryAt == null || !nextRetryAt.isAfter(now));
        }
        return status == ReportGenerationJobStatus.RUNNING
                && leaseExpiresAt != null
                && !leaseExpiresAt.isAfter(now);
    }

    public boolean ownedBy(String owner) {
        return owner != null && owner.equals(leaseOwner);
    }

    public boolean leaseExpired(Instant now) {
        return status == ReportGenerationJobStatus.RUNNING
                && leaseExpiresAt != null
                && now != null
                && !leaseExpiresAt.isAfter(now);
    }

    public void recoverExpiredLease(Instant now) {
        if (!leaseExpired(now)) {
            return;
        }
        if (attempt >= maxAttempts) {
            status = ReportGenerationJobStatus.PERMANENT_FAILURE;
            errorCode = "RETRY_EXHAUSTED";
        } else {
            status = ReportGenerationJobStatus.READY;
        }
        leaseOwner = null;
        leaseExpiresAt = null;
        updatedAt = required(now, "updatedAt");
    }

    public void markSucceeded(String draftId, Instant now) {
        requireRunning();
        status = ReportGenerationJobStatus.SUCCEEDED;
        reportDraftId = requiredText(draftId, "reportDraftId");
        clearLease();
        errorCode = null;
        nextRetryAt = null;
        updatedAt = required(now, "updatedAt");
    }

    public void markRetry(String errorCode, Instant nextRetryAt, Instant now) {
        requireRunning();
        if (attempt >= maxAttempts) {
            markPermanentFailure("RETRY_EXHAUSTED", now);
            return;
        }
        status = ReportGenerationJobStatus.RETRY_WAIT;
        this.errorCode = safeErrorCode(errorCode);
        this.nextRetryAt = required(nextRetryAt, "nextRetryAt");
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void markPermanentFailure(String errorCode, Instant now) {
        status = ReportGenerationJobStatus.PERMANENT_FAILURE;
        this.errorCode = safeErrorCode(errorCode);
        this.nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void markSkipped(String errorCode, Instant now) {
        if (status == ReportGenerationJobStatus.SUCCEEDED) {
            throw new IllegalStateException("Completed report generation job cannot be skipped.");
        }
        status = ReportGenerationJobStatus.SKIPPED;
        this.errorCode = safeErrorCode(errorCode);
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void cancel(String errorCode, Instant now) {
        if (status == ReportGenerationJobStatus.SUCCEEDED) {
            return;
        }
        status = ReportGenerationJobStatus.CANCELLED;
        this.errorCode = safeErrorCode(errorCode);
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void manualRetry(Instant now, String newTraceId) {
        if (status != ReportGenerationJobStatus.PERMANENT_FAILURE
                && status != ReportGenerationJobStatus.SKIPPED
                && status != ReportGenerationJobStatus.CANCELLED) {
            throw new IllegalStateException("Only a terminal report generation job can be retried.");
        }
        status = ReportGenerationJobStatus.READY;
        attempt = 0;
        errorCode = null;
        nextRetryAt = null;
        reportDraftId = null;
        traceId = requiredText(newTraceId, "traceId");
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    private void requireRunning() {
        if (status != ReportGenerationJobStatus.RUNNING) {
            throw new IllegalStateException("Report generation job is not running.");
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private String safeErrorCode(String value) {
        String normalized = value == null ? "UNKNOWN_FAILURE" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return "UNKNOWN_FAILURE";
        }
        return normalized;
    }

    private static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getTenantId() { return tenantId; }
    public Long getCreatorUserId() { return creatorUserId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getReportType() { return reportType; }
    public ReportScope getScope() { return new ReportScope(scopeType, scopeId); }
    public String getLanguage() { return language; }
    public String getTopic() { return topic; }
    public ReportGenerationJobStatus getStatus() { return status; }
    public Integer getAttempt() { return attempt; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getErrorCode() { return errorCode; }
    public String getTraceId() { return traceId; }
    public String getReportDraftId() { return reportDraftId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
