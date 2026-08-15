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
        name = "report_collaboration_notifications",
        indexes = {
                @Index(name = "idx_report_collaboration_notifications_claim",
                        columnList = "status,next_retry_at,created_at,id"),
                @Index(name = "idx_report_collaboration_notifications_tenant",
                        columnList = "tenant_id,created_at,id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_report_collaboration_notifications_event_recipient",
                        columnNames = {"event_id", "recipient_user_id"}
                ),
                @UniqueConstraint(
                        name = "uq_report_collaboration_notifications_key",
                        columnNames = "delivery_key"
                )
        }
)
public class ReportCollaborationNotificationEntity {

    public static final int DEFAULT_MAX_ATTEMPTS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collaboration_id", nullable = false)
    private Long collaborationId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

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

    protected ReportCollaborationNotificationEntity() {
    }

    public ReportCollaborationNotificationEntity(
            ReportCollaborationEntity collaboration,
            ReportCollaborationEventEntity event,
            Long recipientUserId,
            String deliveryKey,
            Instant now
    ) {
        if (collaboration == null || collaboration.getId() == null || event == null || event.getId() == null) {
            throw new IllegalArgumentException("Persisted collaboration event is required.");
        }
        this.collaborationId = collaboration.getId();
        this.eventId = event.getId();
        this.tenantId = collaboration.getTenantId();
        this.recipientUserId = required(recipientUserId, "recipientUserId");
        this.channelKey = "email";
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
            throw new IllegalStateException("Collaboration notification is not claimable.");
        }
        status = ReportDeliveryStatus.SENDING;
        attempt++;
        leaseOwner = requiredText(owner, "leaseOwner");
        leaseExpiresAt = required(expiresAt, "leaseExpiresAt");
        updatedAt = required(now, "now");
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
        updatedAt = required(now, "now");
    }

    public void markSucceeded(String providerId, Instant now) {
        requireSending();
        status = ReportDeliveryStatus.SUCCEEDED;
        providerMessageId = blankToNull(providerId);
        errorCode = null;
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "now");
    }

    public void markRetry(String code, Instant retryAt, Instant now) {
        requireSending();
        if (attempt >= maxAttempts) {
            markPermanentFailure("RETRY_EXHAUSTED", now);
            return;
        }
        status = ReportDeliveryStatus.RETRY_WAIT;
        errorCode = safeErrorCode(code);
        nextRetryAt = required(retryAt, "retryAt");
        clearLease();
        updatedAt = required(now, "now");
    }

    public void markPermanentFailure(String code, Instant now) {
        requireSending();
        status = ReportDeliveryStatus.PERMANENT_FAILURE;
        errorCode = safeErrorCode(code);
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "now");
    }

    public void markUnknown(String code, Instant now) {
        requireSending();
        status = ReportDeliveryStatus.UNKNOWN;
        errorCode = safeErrorCode(code);
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "now");
    }

    public void cancel(String code, Instant now) {
        if (status == ReportDeliveryStatus.SUCCEEDED || status == ReportDeliveryStatus.UNKNOWN) {
            return;
        }
        status = ReportDeliveryStatus.CANCELLED;
        errorCode = safeErrorCode(code);
        nextRetryAt = null;
        clearLease();
        updatedAt = required(now, "now");
    }

    private void requireSending() {
        if (status != ReportDeliveryStatus.SENDING) {
            throw new IllegalStateException("Collaboration notification is not sending.");
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private static String safeErrorCode(String value) {
        String normalized = value == null
                ? "UNKNOWN_FAILURE"
                : value.trim().toUpperCase(java.util.Locale.ROOT);
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
    public Long getCollaborationId() { return collaborationId; }
    public Long getEventId() { return eventId; }
    public Long getTenantId() { return tenantId; }
    public Long getRecipientUserId() { return recipientUserId; }
    public String getDeliveryKey() { return deliveryKey; }
    public ReportDeliveryStatus getStatus() { return status; }
    public Integer getAttempt() { return attempt; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
