package com.brand.agentpoc.dto.detail;

public record TaskDetail(
        String taskId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String opportunityId, String status, String createdDate
) {}
