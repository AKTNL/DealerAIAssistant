package com.brand.agentpoc.dto.detail;

public record CampaignDetail(
        String campaignId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String productModel, String campaignType,
        String createdDate, Integer actualOpportunityCount, Integer totalNewCustomerTarget
) {}
