package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportCollaborationEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "report_collaboration_events",
        indexes = @Index(
                name = "idx_report_collaboration_events_timeline",
                columnList = "tenant_id,report_draft_id,created_at,id")
)
public class ReportCollaborationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collaboration_id", nullable = false)
    private Long collaborationId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "report_draft_id", nullable = false, length = 128)
    private String reportDraftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ReportCollaborationEventType eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 128)
    private String actorUsername;

    @Column(name = "actor_display_name", nullable = false, length = 128)
    private String actorDisplayName;

    @Column(name = "previous_value", length = 256)
    private String previousValue;

    @Column(name = "current_value", length = 256)
    private String currentValue;

    @Column(name = "comment_body", columnDefinition = "TEXT")
    private String commentBody;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportCollaborationEventEntity() {
    }

    public ReportCollaborationEventEntity(
            ReportCollaborationEntity collaboration,
            ReportCollaborationEventType eventType,
            Long actorUserId,
            String actorUsername,
            String actorDisplayName,
            String previousValue,
            String currentValue,
            String commentBody,
            String traceId,
            Instant createdAt
    ) {
        if (collaboration == null || collaboration.getId() == null) {
            throw new IllegalArgumentException("Persisted collaboration is required.");
        }
        this.collaborationId = collaboration.getId();
        this.tenantId = collaboration.getTenantId();
        this.reportDraftId = collaboration.getReportDraftId();
        this.eventType = required(eventType, "eventType");
        this.actorUserId = actorUserId;
        this.actorUsername = requiredText(actorUsername, "actorUsername");
        this.actorDisplayName = requiredText(actorDisplayName, "actorDisplayName");
        this.previousValue = bounded(previousValue, 256);
        this.currentValue = bounded(currentValue, 256);
        this.commentBody = commentBody;
        this.traceId = requiredText(traceId, "traceId");
        this.createdAt = required(createdAt, "createdAt");
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

    private static String bounded(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public Long getId() { return id; }
    public Long getCollaborationId() { return collaborationId; }
    public Long getTenantId() { return tenantId; }
    public String getReportDraftId() { return reportDraftId; }
    public ReportCollaborationEventType getEventType() { return eventType; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorUsername() { return actorUsername; }
    public String getActorDisplayName() { return actorDisplayName; }
    public String getPreviousValue() { return previousValue; }
    public String getCurrentValue() { return currentValue; }
    public String getCommentBody() { return commentBody; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
}
