package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
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
        this.batchKey = batchKey;
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
