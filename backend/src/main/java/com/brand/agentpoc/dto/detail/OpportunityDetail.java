package com.brand.agentpoc.dto.detail;

public record OpportunityDetail(
        String opportunityId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String productModel, String stageName,
        String leadSource, String createdDate, int probability
) {}
