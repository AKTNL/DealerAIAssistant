package com.brand.agentpoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dealer_targets")
public class Target {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(nullable = false)
    private Integer targetYear;

    @Column(nullable = false)
    private Integer targetMonth;

    @Column(nullable = false)
    private Integer asKTarget;

    @Column(nullable = false)
    private Integer opportunityWonCount;

    protected Target() {
    }

    public Target(
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String productModel,
            Integer targetYear,
            Integer targetMonth,
            Integer asKTarget,
            Integer opportunityWonCount
    ) {
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.productModel = productModel;
        this.targetYear = targetYear;
        this.targetMonth = targetMonth;
        this.asKTarget = asKTarget;
        this.opportunityWonCount = opportunityWonCount;
    }

    public Long getId() {
        return id;
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

    public Integer getTargetYear() {
        return targetYear;
    }

    public Integer getTargetMonth() {
        return targetMonth;
    }

    public Integer getAsKTarget() {
        return asKTarget;
    }

    public Integer getOpportunityWonCount() {
        return opportunityWonCount;
    }
}
