package com.brand.agentpoc.modelusage.infrastructure.persistence;

import com.brand.agentpoc.modelusage.domain.BudgetReservationStatus;
import com.brand.agentpoc.tenant.domain.TenantScoped;
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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "model_budget_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uq_model_budget_reservations_call", columnNames = "call_key"),
        indexes = @Index(
                name = "idx_model_budget_reservations_active",
                columnList = "tenant_id,status,expires_at,id"
        )
)
public class ModelBudgetReservationEntity implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_key", nullable = false, length = 64)
    private String callKey;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BudgetReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected ModelBudgetReservationEntity() {
    }

    public ModelBudgetReservationEntity(
            String callKey,
            Long tenantId,
            BigDecimal amount,
            String currency,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.callKey = callKey;
        this.tenantId = tenantId;
        this.amount = amount;
        this.currency = currency;
        this.status = BudgetReservationStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void close(Instant now) {
        status = BudgetReservationStatus.CLOSED;
        closedAt = now;
    }

    public Long getId() { return id; }
    public String getCallKey() { return callKey; }
    @Override public Long getTenantId() { return tenantId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BudgetReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
