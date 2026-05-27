package com.brand.agentpoc.dto.metrics;

public record TargetMetrics(
        int totalDealers,
        int totalAsKTarget,
        int totalOpportunityWon,
        double averageAchievementRate,
        DealerMetric lowestDealer,
        DealerMetric highestDealer
) {
    public record DealerMetric(String dealerCode, String dealerName, double achievementRate) {}
}
