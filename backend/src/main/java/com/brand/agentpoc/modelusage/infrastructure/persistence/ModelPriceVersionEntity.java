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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "model_price_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_model_price_versions_key",
                columnNames = {"tenant_id", "version_key"}
        ),
        indexes = @Index(
                name = "idx_model_price_versions_lookup",
                columnList = "tenant_id,provider_key,model_name,effective_from,id"
        )
)
public class ModelPriceVersionEntity implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "provider_key", nullable = false, length = 64)
    private String providerKey;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "version_key", nullable = false, length = 128)
    private String versionKey;

    @Column(name = "input_price_per_million", nullable = false, precision = 20, scale = 8)
    private BigDecimal inputPricePerMillion;

    @Column(name = "output_price_per_million", nullable = false, precision = 20, scale = 8)
    private BigDecimal outputPricePerMillion;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModelPriceVersionEntity() {
    }

    public ModelPriceVersionEntity(
            Long tenantId,
            String providerKey,
            String modelName,
            String versionKey,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            String source,
            Instant effectiveFrom,
            Long createdBy,
            Instant createdAt
    ) {
        this.tenantId = tenantId;
        this.providerKey = providerKey;
        this.modelName = modelName;
        this.versionKey = versionKey;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
        this.currency = currency;
        this.source = source;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    @Override public Long getTenantId() { return tenantId; }
    public String getProviderKey() { return providerKey; }
    public String getModelName() { return modelName; }
    public String getVersionKey() { return versionKey; }
    public BigDecimal getInputPricePerMillion() { return inputPricePerMillion; }
    public BigDecimal getOutputPricePerMillion() { return outputPricePerMillion; }
    public String getCurrency() { return currency; }
    public String getSource() { return source; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
