package com.brand.agentpoc.dto.detail;

public record TargetDetail(
        String dealerCode, String dealerName, String city, String dealerGroupName,
        String productModel, int targetYear, int targetMonth,
        int asKTarget, int opportunityWonCount
) {}
