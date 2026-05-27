package com.brand.agentpoc.dto.detail;

public record LeadDetail(
        String leadId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String leadSource, String stageName,
        String productModel, String createdDate, boolean isConverted
) {}
