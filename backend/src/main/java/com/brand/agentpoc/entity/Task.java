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
@Table(name = "dealer_tasks")
public class Task implements BatchScoped, DealerScoped, TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String taskId;

    @Column(nullable = false, length = 64)
    private String dealerCode;

    @Column(nullable = false, length = 128)
    private String dealerName;

    @Column(nullable = false, length = 64)
    private String city;

    @Column(nullable = false, length = 128)
    private String dealerGroupName;

    @Column(nullable = false, length = 64)
    private String opportunityId;

    @Column(nullable = false, length = 128)
    private String subject;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false, length = 64)
    private String importBatchId;

    protected Task() {
    }

    public Task(
            String taskId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String opportunityId,
            String status,
            LocalDate createdDate
    ) {
        this(
                taskId,
                dealerCode,
                dealerName,
                city,
                dealerGroupName,
                opportunityId,
                "未知",
                status,
                createdDate,
                BatchScoped.LEGACY_BATCH_ID
        );
    }

    public Task(
            String taskId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String opportunityId,
            String subject,
            String status,
            LocalDate createdDate
    ) {
        this(
                taskId,
                dealerCode,
                dealerName,
                city,
                dealerGroupName,
                opportunityId,
                subject,
                status,
                createdDate,
                BatchScoped.LEGACY_BATCH_ID
        );
    }

    public Task(
            String taskId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String opportunityId,
            String subject,
            String status,
            LocalDate createdDate,
            String importBatchId
    ) {
        this(taskId, dealerCode, dealerName, city, dealerGroupName, opportunityId, subject, status,
                createdDate, importBatchId, TenantScoped.DEFAULT_TENANT_ID);
    }

    public Task(
            String taskId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String opportunityId,
            String subject,
            String status,
            LocalDate createdDate,
            String importBatchId,
            Long tenantId
    ) {
        this.taskId = taskId;
        this.tenantId = requireTenantId(tenantId);
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.opportunityId = opportunityId;
        this.subject = subject == null || subject.isBlank() ? "未知" : subject;
        this.status = status;
        this.createdDate = createdDate;
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

    public String getTaskId() {
        return taskId;
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

    public String getOpportunityId() {
        return opportunityId;
    }

    public String getSubject() {
        return subject;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    @Override
    public String getImportBatchId() {
        return importBatchId;
    }
}
