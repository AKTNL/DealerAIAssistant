package com.brand.agentpoc.dto.metrics;

import java.util.List;
import java.util.Map;

public record OpportunityMetrics(
        int totalOpportunities,
        int wonCount,
        int lostCount,
        int openCount,
        double winRate,
        Map<String, Long> stageDistribution,
        List<LossReason> topLossReasons
) {
    public record LossReason(String reason, long count) {}
}
