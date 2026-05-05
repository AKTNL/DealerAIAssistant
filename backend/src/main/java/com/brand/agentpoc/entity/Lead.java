package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "leads")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
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

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false)
    private Boolean converted;

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
        this.leadId = leadId;
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.leadSource = leadSource;
        this.stageName = stageName;
        this.productModel = productModel;
        this.createdDate = createdDate;
        this.converted = converted;
    }

    public Long getId() {
        return id;
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
}
