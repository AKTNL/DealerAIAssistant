package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
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
        name = "report_deliveries",
        indexes = {
                @Index(name = "idx_report_deliveries_claim", columnList = "status,next_retry_at,created_at,id"),
                @Index(name = "idx_report_deliveries_tenant", columnList = "tenant_id,creator_user_id,created_at,id"),
                @Index(name = "idx_report_deliveries_job", columnList = "report_job_id,recipient_user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_report_deliveries_job_channel_recipient",
                        columnNames = {"report_job_id", "channel_key", "recipient_user_id"}
                ),
                @UniqueConstraint(name = "uq_report_deliveries_key", columnNames = "delivery_key")
        }
)
public class ReportDeliveryEntity {

    public static final int DEFAULT_MAX_ATTEMPTS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_job_id", nullable = false)
    private Long reportJobId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "report_draft_id", nullable = false, length = 128)
    private String reportDraftId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "channel_key", nullable = false, length = 32)
    private String channelKey;

    @Column(name = "delivery_key", nullable = false, length = 192)
    private String deliveryKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportDeliveryStatus status;

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

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReportDeliveryEntity() {
    }

    public ReportDeliveryEntity(
            Long reportJobId,
            Long subscriptionId,
            Long tenantId,
            Long creatorUserId,
            String reportDraftId,
            Long recipientUserId,
            String channelKey,
            String deliveryKey,
            Instant now
    ) {
        this.reportJobId = required(reportJobId, "reportJobId");
        this.subscriptionId = required(subscriptionId, "subscriptionId");
        this.tenantId = required(tenantId, "tenantId");
        this.creatorUserId = required(creatorUserId, "creatorUserId");
        this.reportDraftId = requiredText(reportDraftId, "reportDraftId");
        this.recipientUserId = required(recipientUserId, "recipientUserId");
        this.channelKey = requiredText(channelKey, "channelKey");
        this.deliveryKey = requiredText(deliveryKey, "deliveryKey");
        this.status = ReportDeliveryStatus.READY;
        this.attempt = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.createdAt = required(now, "createdAt");
        this.updatedAt = this.createdAt;
    }

    public boolean isClaimable(Instant now) {
        if (now == null || attempt >= maxAttempts) {
            return false;
        }
        if (status == ReportDeliveryStatus.READY) {
            return true;
        }
        return status == ReportDeliveryStatus.RETRY_WAIT
                && (nextRetryAt == null || !nextRetryAt.isAfter(now));
    }

    public void claim(String owner, Instant now, Instant expiresAt) {
        if (!isClaimable(now)) {
            throw new IllegalStateException("Report delivery is not claimable.");
        }
        status = ReportDeliveryStatus.SENDING;
        attempt++;
        leaseOwner = requiredText(owner, "leaseOwner");
        leaseExpiresAt = required(expiresAt, "leaseExpiresAt");
        updatedAt = required(now, "updatedAt");
    }

    public boolean ownedBy(String owner) {
        return owner != null && owner.equals(leaseOwner);
    }

    public boolean leaseExpired(Instant now) {
        return status == ReportDeliveryStatus.SENDING
                && leaseExpiresAt != null
                && now != null
                && !leaseExpiresAt.isAfter(now);
    }

    public void recoverExpiredLease(Instant now) {
        if (!leaseExpired(now)) {
            return;
        }
        status = ReportDeliveryStatus.UNKNOWN;
        errorCode = "LEASE_EXPIRED_OUTCOME_UNKNOWN";
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void markSucceeded(String providerMessageId, Instant now) {
        requireSending();
        status = ReportDeliveryStatus.SUCCEEDED;
        this.providerMessageId = blankToNull(providerMessageId);
        errorCode = null;
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void markRetry(String errorCode, Instant retryAt, Instant now) {
        requireSending();
        if (attempt >= maxAttempts) {
            markPermanentFailure("RETRY_EXHAUSTED", now);
            return;
        }
        status = ReportDeliveryStatus.RETRY_WAIT;
        this.errorCode = safeErrorCode(errorCode);
        nextRetryAt = required(retryAt, "nextRetryAt");
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void markPermanentFailure(String errorCode, Instant now) {
        requireSending();
        status = ReportDeliveryStatus.PERMANENT_FAILURE;
        this.errorCode = safeErrorCode(errorCode);
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void markUnknown(String errorCode, Instant now) {
        requireSending();
        status = ReportDeliveryStatus.UNKNOWN;
        this.errorCode = safeErrorCode(errorCode);
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void cancel(String errorCode, Instant now) {
        if (status == ReportDeliveryStatus.SUCCEEDED || status == ReportDeliveryStatus.UNKNOWN) {
            return;
        }
        status = ReportDeliveryStatus.CANCELLED;
        this.errorCode = safeErrorCode(errorCode);
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    public void manualRetry(Instant now) {
        if (status != ReportDeliveryStatus.PERMANENT_FAILURE) {
            throw new IllegalStateException("Only an explicit delivery failure can be retried.");
        }
        resetForReplay(now);
    }

    public void forceReplay(Instant now) {
        if (status != ReportDeliveryStatus.UNKNOWN) {
            throw new IllegalStateException("Only an unknown delivery can be force replayed.");
        }
        resetForReplay(now);
    }

    private void resetForReplay(Instant now) {
        status = ReportDeliveryStatus.READY;
        attempt = 0;
        errorCode = null;
        providerMessageId = null;
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "updatedAt");
    }

    private void requireSending() {
        if (status != ReportDeliveryStatus.SENDING) {
            throw new IllegalStateException("Report delivery is not sending.");
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private String safeErrorCode(String value) {
        String normalized = value == null ? "UNKNOWN_FAILURE" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.matches("[A-Z][A-Z0-9_]{0,63}") ? normalized : "UNKNOWN_FAILURE";
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public Long getReportJobId() { return reportJobId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getTenantId() { return tenantId; }
    public Long getCreatorUserId() { return creatorUserId; }
    public String getReportDraftId() { return reportDraftId; }
    public Long getRecipientUserId() { return recipientUserId; }
    public String getChannelKey() { return channelKey; }
    public String getDeliveryKey() { return deliveryKey; }
    public ReportDeliveryStatus getStatus() { return status; }
    public Integer getAttempt() { return attempt; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getErrorCode() { return errorCode; }
    public String getProviderMessageId() { return providerMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
