package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import java.time.Instant;

@Entity
@Table(
        name = "import_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_import_batches_tenant_batch_key",
                columnNames = {"tenant_id", "batch_key"}
        )
)
public class ImportBatch implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String batchKey;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(nullable = false, length = 32)
    private String scopeType;

    @Column(length = 128)
    private String scopeId;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private Boolean fallbackActive;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant activatedAt;

    @Column(nullable = false, length = 512)
    private String message;

    protected ImportBatch() {
    }

    public ImportBatch(
            String batchKey,
            String source,
            String scopeType,
            String scopeId,
            boolean active,
            boolean fallbackActive,
            Instant createdAt,
            Instant activatedAt,
            String message
    ) {
        this(batchKey, source, scopeType, scopeId, active, fallbackActive, createdAt, activatedAt, message,
                TenantScoped.DEFAULT_TENANT_ID);
    }

    public ImportBatch(
            String batchKey,
            String source,
            String scopeType,
            String scopeId,
            boolean active,
            boolean fallbackActive,
            Instant createdAt,
            Instant activatedAt,
            String message,
            Long tenantId
    ) {
        this.batchKey = batchKey;
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        this.tenantId = tenantId;
        this.source = defaultText(source, "unknown");
        this.scopeType = defaultText(scopeType, "GLOBAL");
        this.scopeId = scopeId;
        this.active = active;
        this.fallbackActive = fallbackActive;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.activatedAt = activatedAt;
        this.message = defaultText(message, "");
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    public String getBatchKey() {
        return batchKey;
    }

    public String getSource() {
        return source;
    }

    public String getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getFallbackActive() {
        return fallbackActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public String getMessage() {
        return message;
    }
}
