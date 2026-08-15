package com.brand.agentpoc.modelusage.infrastructure.persistence;

import com.brand.agentpoc.tenant.domain.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "model_budget_policies",
        uniqueConstraints = @UniqueConstraint(name = "uq_model_budget_policies_tenant", columnNames = "tenant_id"),
        indexes = @Index(name = "idx_model_budget_policies_updated", columnList = "tenant_id,updated_at")
)
public class ModelBudgetPolicyEntity implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "monthly_limit", nullable = false, precision = 20, scale = 8)
    private BigDecimal monthlyLimit;

    @Column(name = "soft_threshold_percent", nullable = false)
    private Integer softThresholdPercent;

    @Column(name = "hard_limit_enabled", nullable = false)
    private Boolean hardLimitEnabled;

    @Column(name = "fail_open", nullable = false)
    private Boolean failOpen;

    @Column(name = "reservation_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal reservationAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ModelBudgetPolicyEntity() {
    }

    public ModelBudgetPolicyEntity(
            Long tenantId,
            BigDecimal monthlyLimit,
            int softThresholdPercent,
            boolean hardLimitEnabled,
            boolean failOpen,
            BigDecimal reservationAmount,
            String currency,
            Instant now
    ) {
        this.tenantId = tenantId;
        this.createdAt = now;
        this.monthlyLimit = monthlyLimit;
        this.softThresholdPercent = softThresholdPercent;
        this.hardLimitEnabled = hardLimitEnabled;
        this.failOpen = failOpen;
        this.reservationAmount = reservationAmount;
        this.currency = currency;
        this.updatedAt = now;
    }

    public void update(
            BigDecimal newMonthlyLimit,
            int newSoftThresholdPercent,
            boolean newHardLimitEnabled,
            boolean newFailOpen,
            BigDecimal newReservationAmount,
            String newCurrency,
            Instant now
    ) {
        monthlyLimit = newMonthlyLimit;
        softThresholdPercent = newSoftThresholdPercent;
        hardLimitEnabled = newHardLimitEnabled;
        failOpen = newFailOpen;
        reservationAmount = newReservationAmount;
        currency = newCurrency;
        updatedAt = now;
    }

    public Long getId() { return id; }
    @Override public Long getTenantId() { return tenantId; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public Integer getSoftThresholdPercent() { return softThresholdPercent; }
    public Boolean getHardLimitEnabled() { return hardLimitEnabled; }
    public Boolean getFailOpen() { return failOpen; }
    public BigDecimal getReservationAmount() { return reservationAmount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
