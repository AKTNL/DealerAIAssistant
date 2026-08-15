package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.ReportCollaborationStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
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
import java.util.Objects;

@Entity
@Table(
        name = "report_collaborations",
        indexes = {
                @Index(name = "idx_report_collaborations_filter",
                        columnList = "tenant_id,status,assignee_user_id,updated_at,id"),
                @Index(name = "idx_report_collaborations_scope",
                        columnList = "tenant_id,scope_type,updated_at,id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_report_collaborations_report",
                columnNames = {"tenant_id", "report_draft_id"}
        )
)
public class ReportCollaborationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "report_draft_id", nullable = false, length = 128)
    private String reportDraftId;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_id", nullable = false, length = 2048)
    private String scopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportCollaborationStatus status;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "assignee_username", length = 128)
    private String assigneeUsername;

    @Column(name = "assignee_display_name", length = 128)
    private String assigneeDisplayName;

    @Column(name = "activity_count", nullable = false)
    private Long activityCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReportCollaborationEntity() {
    }

    public ReportCollaborationEntity(ReportDraft draft) {
        Objects.requireNonNull(draft, "draft is required.");
        tenantId = draft.tenantId();
        reportDraftId = draft.id();
        scopeType = draft.scope().type();
        scopeId = draft.scope().id();
        status = ReportCollaborationStatus.OPEN;
        activityCount = 0L;
        createdAt = draft.generatedAt();
        updatedAt = draft.generatedAt();
    }

    public void changeStatus(ReportCollaborationStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("The requested report status transition is not allowed.");
        }
        status = target;
        touch(now);
    }

    public void assign(Long userId, String username, String displayName, Instant now) {
        requireMutable();
        if (userId == null) {
            assigneeUserId = null;
            assigneeUsername = null;
            assigneeDisplayName = null;
        } else {
            assigneeUserId = userId;
            assigneeUsername = requiredText(username, "assignee username");
            assigneeDisplayName = requiredText(displayName, "assignee display name");
        }
        touch(now);
    }

    public void addCommentActivity(Instant now) {
        requireMutable();
        touch(now);
    }

    public ReportScope scope() {
        return new ReportScope(scopeType, scopeId);
    }

    private void requireMutable() {
        if (status.terminal()) {
            throw new IllegalStateException("A terminal report collaboration cannot be changed.");
        }
    }

    private void touch(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now is required.");
        activityCount++;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getReportDraftId() { return reportDraftId; }
    public ReportCollaborationStatus getStatus() { return status; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public String getAssigneeUsername() { return assigneeUsername; }
    public String getAssigneeDisplayName() { return assigneeDisplayName; }
    public Long getActivityCount() { return activityCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
