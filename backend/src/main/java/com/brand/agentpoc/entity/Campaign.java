package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String campaignId;

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
    private String campaignType;

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false)
    private Integer actualOpportunityCount;

    @Column(nullable = false)
    private Integer totalNewCustomerTarget;

    protected Campaign() {
    }

    public Campaign(
            String campaignId,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String productModel,
            String campaignType,
            LocalDate createdDate,
            Integer actualOpportunityCount,
            Integer totalNewCustomerTarget
    ) {
        this.campaignId = campaignId;
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.productModel = productModel;
        this.campaignType = campaignType;
        this.createdDate = createdDate;
        this.actualOpportunityCount = actualOpportunityCount;
        this.totalNewCustomerTarget = totalNewCustomerTarget;
    }

    public Long getId() {
        return id;
    }

    public String getCampaignId() {
        return campaignId;
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

    public String getCampaignType() {
        return campaignType;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public Integer getActualOpportunityCount() {
        return actualOpportunityCount;
    }

    public Integer getTotalNewCustomerTarget() {
        return totalNewCustomerTarget;
    }
}
