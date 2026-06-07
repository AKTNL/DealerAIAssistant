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

    @Column(nullable = false, length = 256)
    private String campaignName;

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
    private String eventType;

    @Column(nullable = false, length = 64)
    private String campaignType;

    @Column(nullable = false)
    private LocalDate createdDate;

    @Column(nullable = false)
    private Integer targetOpportunityAmount;

    @Column(nullable = false)
    private Integer actualOpportunityCount;

    @Column(nullable = false)
    private Integer targetOrderAmount;

    @Column(nullable = false)
    private Integer wonOpportunityCount;

    @Column(nullable = false)
    private Integer leadCount;

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
        this(
                campaignId,
                campaignId,
                dealerCode,
                dealerName,
                city,
                dealerGroupName,
                productModel,
                "Event",
                campaignType,
                createdDate,
                totalNewCustomerTarget,
                actualOpportunityCount,
                0,
                0,
                0,
                totalNewCustomerTarget
        );
    }

    public Campaign(
            String campaignId,
            String campaignName,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String productModel,
            String eventType,
            String campaignType,
            LocalDate createdDate,
            Integer targetOpportunityAmount,
            Integer actualOpportunityCount,
            Integer targetOrderAmount,
            Integer wonOpportunityCount,
            Integer leadCount,
            Integer totalNewCustomerTarget
    ) {
        this.campaignId = campaignId;
        this.campaignName = defaultText(campaignName, campaignId);
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.productModel = productModel;
        this.eventType = defaultText(eventType, "0");
        this.campaignType = campaignType;
        this.createdDate = createdDate;
        this.targetOpportunityAmount = defaultNumber(targetOpportunityAmount);
        this.actualOpportunityCount = defaultNumber(actualOpportunityCount);
        this.targetOrderAmount = defaultNumber(targetOrderAmount);
        this.wonOpportunityCount = defaultNumber(wonOpportunityCount);
        this.leadCount = defaultNumber(leadCount);
        this.totalNewCustomerTarget = defaultNumber(totalNewCustomerTarget);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    public Long getId() {
        return id;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public String getCampaignName() {
        return campaignName;
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

    public String getEventType() {
        return eventType;
    }

    public String getCampaignType() {
        return campaignType;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public Integer getTargetOpportunityAmount() {
        return targetOpportunityAmount;
    }

    public Integer getActualOpportunityCount() {
        return actualOpportunityCount;
    }

    public Integer getTargetOrderAmount() {
        return targetOrderAmount;
    }

    public Integer getWonOpportunityCount() {
        return wonOpportunityCount;
    }

    public Integer getLeadCount() {
        return leadCount;
    }

    public Integer getTotalNewCustomerTarget() {
        return totalNewCustomerTarget;
    }
}
