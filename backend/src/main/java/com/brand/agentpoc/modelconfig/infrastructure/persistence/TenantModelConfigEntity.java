package com.brand.agentpoc.modelconfig.infrastructure.persistence;

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
import java.time.Instant;

@Entity
@Table(
        name = "tenant_model_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_model_configs_tenant",
                columnNames = "tenant_id"
        ),
        indexes = @Index(name = "idx_tenant_model_configs_updated", columnList = "tenant_id,updated_at")
)
public class TenantModelConfigEntity implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "allowed_hosts", nullable = false, length = 1024)
    private String allowedHosts;

    @Column(name = "secret_ciphertext", nullable = false, length = 2048)
    private String secretCiphertext;

    @Column(name = "secret_version", nullable = false)
    private Integer secretVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected TenantModelConfigEntity() {
    }

    public TenantModelConfigEntity(
            Long tenantId,
            String baseUrl,
            String modelName,
            String allowedHosts,
            String secretCiphertext,
            int secretVersion,
            Instant now
        ) {
        this.tenantId = requireTenant(tenantId);
        this.createdAt = now == null ? Instant.now() : now;
        this.baseUrl = required(baseUrl, "baseUrl");
        this.modelName = required(modelName, "modelName");
        this.allowedHosts = required(allowedHosts, "allowedHosts");
        this.secretCiphertext = required(secretCiphertext, "secretCiphertext");
        this.secretVersion = secretVersion;
        this.updatedAt = this.createdAt;
    }

    public void update(
            String baseUrl,
            String modelName,
            String allowedHosts,
            String secretCiphertext,
            int secretVersion,
            Instant now
    ) {
        this.baseUrl = required(baseUrl, "baseUrl");
        this.modelName = required(modelName, "modelName");
        this.allowedHosts = required(allowedHosts, "allowedHosts");
        this.secretCiphertext = required(secretCiphertext, "secretCiphertext");
        this.secretVersion = secretVersion;
        this.updatedAt = now == null ? Instant.now() : now;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public String getAllowedHosts() {
        return allowedHosts;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public Integer getSecretVersion() {
        return secretVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    private static Long requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        return tenantId;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }
}
