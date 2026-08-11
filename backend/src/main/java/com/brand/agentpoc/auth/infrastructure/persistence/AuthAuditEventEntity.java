package com.brand.agentpoc.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "auth_audit_events",
        indexes = {
            @Index(name = "idx_auth_audit_created", columnList = "created_at,id"),
            @Index(name = "idx_auth_audit_actor", columnList = "actor_user_id,created_at")
        }
)
public class AuthAuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", length = 128)
    private String targetId;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "detail_code", length = 128)
    private String detailCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthAuditEventEntity() {
    }

    public AuthAuditEventEntity(
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            String outcome,
            String traceId,
            String detailCode,
            Instant createdAt
    ) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.traceId = traceId;
        this.detailCode = detailCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getDetailCode() {
        return detailCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
