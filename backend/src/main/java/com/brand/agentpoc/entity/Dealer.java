package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.brand.agentpoc.tenant.domain.TenantScoped;

@Entity
@Table(name = "dealers")
public class Dealer implements BatchScoped, DealerScoped, TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String dealerCode;

    @Column(nullable = false, length = 128)
    private String dealerName;

    @Column(nullable = false, length = 64)
    private String city;

    @Column(nullable = false, length = 128)
    private String dealerGroupName;

    @Column(nullable = false, length = 64)
    private String importBatchId;

    protected Dealer() {
    }

    public Dealer(String dealerCode, String dealerName, String city, String dealerGroupName) {
        this(dealerCode, dealerName, city, dealerGroupName, BatchScoped.LEGACY_BATCH_ID);
    }

    public Dealer(String dealerCode, String dealerName, String city, String dealerGroupName, String importBatchId) {
        this(dealerCode, dealerName, city, dealerGroupName, importBatchId, TenantScoped.DEFAULT_TENANT_ID);
    }

    public Dealer(
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String importBatchId,
            Long tenantId
    ) {
        this.dealerCode = dealerCode;
        this.tenantId = requireTenantId(tenantId);
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.importBatchId = defaultBatchId(importBatchId);
    }

    private static String defaultBatchId(String value) {
        return value == null || value.isBlank() ? BatchScoped.LEGACY_BATCH_ID : value;
    }

    private static Long requireTenantId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    public String getDealerCode() {
        return dealerCode;
    }

    public String getDealerName() {
        return dealerName;
    }

    public String getCity() {
        return city;
    }

    public String getDealerGroupName() {
        return dealerGroupName;
    }

    @Override
    public String getImportBatchId() {
        return importBatchId;
    }
}
