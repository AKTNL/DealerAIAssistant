package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import java.time.LocalDate;

@Entity
@Table(name = "leads")
public class Lead implements BatchScoped, DealerScoped, TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String leadId;

    @Column(nullable = false, length = 64)
    private String dealerCode;

    @Column(nullable = false, length = 128)
    private String dealerName;

    @Column(nullable = false, length = 64)
    private String city;

    @Column(nullable = false, length = 128)
    private String dealerGroupName;

    @Column(nullable = false, length = 64)
    private String leadSource;

    @Column(nullable = false, length = 64)
    private String stageName;

    @Column(nullable = false, length = 64)
    private String productModel;

    @Column
    private LocalDate createdDate;

    @Column(nullable = false)
    private Boolean converted;

    @Column(nullable = false, length = 64)
    private String importBatchId;

    protected Lead() {
    }

    public Lead(
            String leadId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String leadSource,
            String stageName,
            String productModel,
            LocalDate createdDate,
            Boolean converted
    ) {
        this(
                leadId,
                dealerCode,
                dealerName,
                city,
                dealerGroupName,
                leadSource,
                stageName,
                productModel,
                createdDate,
                converted,
                BatchScoped.LEGACY_BATCH_ID
        );
    }

    public Lead(
            String leadId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String leadSource,
            String stageName,
            String productModel,
            LocalDate createdDate,
            Boolean converted,
            String importBatchId
    ) {
        this(leadId, dealerCode, dealerName, city, dealerGroupName, leadSource, stageName, productModel,
                createdDate, converted, importBatchId, TenantScoped.DEFAULT_TENANT_ID);
    }

    public Lead(
            String leadId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String leadSource,
            String stageName,
            String productModel,
            LocalDate createdDate,
            Boolean converted,
            String importBatchId,
            Long tenantId
    ) {
        this.leadId = leadId;
        this.tenantId = requireTenantId(tenantId);
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.leadSource = leadSource;
        this.stageName = stageName;
        this.productModel = productModel;
        this.createdDate = createdDate;
        this.converted = converted;
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

    public String getLeadId() {
        return leadId;
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

    public String getLeadSource() {
        return leadSource;
    }

    public String getStageName() {
        return stageName;
    }

    public String getProductModel() {
        return productModel;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public Boolean getConverted() {
        return converted;
    }

    @Override
    public String getImportBatchId() {
        return importBatchId;
    }
}
