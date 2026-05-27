package com.brand.agentpoc.dto.metrics;

public record CampaignMetrics(
        int totalCampaigns,
        double averageAttainment,
        BestCampaign bestCampaign,
        int totalActualOpportunities,
        int totalTarget
) {
    public record BestCampaign(String campaignId, String campaignName, double attainmentRate) {}
}
