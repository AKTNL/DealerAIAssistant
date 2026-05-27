package com.brand.agentpoc.dto.metrics;

import java.util.Map;

public record LeadMetrics(
        int totalLeads,
        int convertedCount,
        double conversionRate,
        Map<String, Long> sourceDistribution,
        SourceMetric bestConversionSource
) {
    public record SourceMetric(String source, double conversionRate) {}
}
