package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "opportunities")
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String opportunityId;

    @Column(nullable = false, length = 64)
    private String dealerCode;

    @Column(nullable = false, length = 128)
    private String dealerName;

    @Column(nullable = false, length = 64)
    private String city;

    @Column(nullable = false, length = 128)
    private String dealerGroupName;

    @Column(nullable = false, length = 64)
    private String productModel;

    @Column(nullable = false, length = 64)
    private String stageName;

    @Column(nullable = false, length = 64)
    private String leadSource;

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false)
    private LocalDate expectedCloseDate;

    @Column(nullable = false)
    private Integer probability;

    protected Opportunity() {
    }

    public Opportunity(
            String opportunityId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String productModel,
            String stageName,
            String leadSource,
            LocalDate createdDate,
            LocalDate expectedCloseDate,
            Integer probability
    ) {
        this.opportunityId = opportunityId;
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.productModel = productModel;
        this.stageName = stageName;
        this.leadSource = leadSource;
        this.createdDate = createdDate;
        this.expectedCloseDate = expectedCloseDate;
        this.probability = probability;
    }

    public Long getId() {
        return id;
    }

    public String getOpportunityId() {
        return opportunityId;
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

    public String getProductModel() {
        return productModel;
    }

    public String getStageName() {
        return stageName;
    }

    public String getLeadSource() {
        return leadSource;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public Integer getProbability() {
        return probability;
    }
}
