package com.brand.agentpoc.dto.detail;

public record CampaignDetail(
        String campaignId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String productModel, String campaignType,
        String createdDate, int actualOpportunityCount, int totalNewCustomerTarget
) {}
