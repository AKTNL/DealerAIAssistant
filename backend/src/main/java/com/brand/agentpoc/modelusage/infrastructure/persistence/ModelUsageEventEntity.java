package com.brand.agentpoc.modelusage.infrastructure.persistence;

import com.brand.agentpoc.modelusage.domain.ModelCostSource;
import com.brand.agentpoc.modelusage.domain.ModelTokenState;
import com.brand.agentpoc.modelusage.domain.ModelUsageScenario;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
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
        name = "model_usage_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_model_usage_events_call", columnNames = "call_key"),
        indexes = {
            @Index(name = "idx_model_usage_events_tenant_time", columnList = "tenant_id,occurred_at,id"),
            @Index(name = "idx_model_usage_events_scenario", columnList = "tenant_id,scenario,occurred_at"),
            @Index(name = "idx_model_usage_events_model", columnList = "tenant_id,provider_key,model_name,occurred_at")
        }
)
public class ModelUsageEventEntity implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_key", nullable = false, length = 64)
    private String callKey;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "provider_key", nullable = false, length = 64)
    private String providerKey;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModelUsageScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModelUsageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_state", nullable = false, length = 16)
    private ModelTokenState tokenState;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit;

    @Column(name = "price_version_id")
    private Long priceVersionId;

    @Column(name = "price_version_key", length = 128)
    private String priceVersionKey;

    @Column(name = "input_price_per_million", precision = 20, scale = 8)
    private BigDecimal inputPricePerMillion;

    @Column(name = "output_price_per_million", precision = 20, scale = 8)
    private BigDecimal outputPricePerMillion;

    @Column(name = "estimated_cost", precision = 20, scale = 8)
    private BigDecimal estimatedCost;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_source", nullable = false, length = 32)
    private ModelCostSource costSource;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ModelUsageEventEntity() {
    }

    public ModelUsageEventEntity(
            String callKey,
            Long tenantId,
            Long userId,
            String providerKey,
            String modelName,
            ModelUsageScenario scenario,
            ModelUsageStatus status,
            ModelTokenState tokenState,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            long durationMs,
            String traceId,
            boolean cacheHit,
            ModelPriceVersionEntity price,
            BigDecimal estimatedCost,
            Instant occurredAt
    ) {
        this.callKey = callKey;
        this.tenantId = tenantId;
        this.userId = userId;
        this.providerKey = providerKey;
        this.modelName = modelName;
        this.scenario = scenario;
        this.status = status;
        this.tokenState = tokenState;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.durationMs = Math.max(0L, durationMs);
        this.traceId = traceId;
        this.cacheHit = cacheHit;
        this.priceVersionId = price == null ? null : price.getId();
        this.priceVersionKey = price == null ? null : price.getVersionKey();
        this.inputPricePerMillion = price == null ? null : price.getInputPricePerMillion();
        this.outputPricePerMillion = price == null ? null : price.getOutputPricePerMillion();
        this.estimatedCost = estimatedCost;
        this.currency = price == null ? null : price.getCurrency();
        this.costSource = estimatedCost == null ? ModelCostSource.UNKNOWN : ModelCostSource.CATALOG_ESTIMATE;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getCallKey() { return callKey; }
    @Override public Long getTenantId() { return tenantId; }
    public Long getUserId() { return userId; }
    public String getProviderKey() { return providerKey; }
    public String getModelName() { return modelName; }
    public ModelUsageScenario getScenario() { return scenario; }
    public ModelUsageStatus getStatus() { return status; }
    public ModelTokenState getTokenState() { return tokenState; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public Long getDurationMs() { return durationMs; }
    public String getTraceId() { return traceId; }
    public Boolean getCacheHit() { return cacheHit; }
    public Long getPriceVersionId() { return priceVersionId; }
    public String getPriceVersionKey() { return priceVersionKey; }
    public BigDecimal getInputPricePerMillion() { return inputPricePerMillion; }
    public BigDecimal getOutputPricePerMillion() { return outputPricePerMillion; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getCurrency() { return currency; }
    public ModelCostSource getCostSource() { return costSource; }
    public Instant getOccurredAt() { return occurredAt; }
}
