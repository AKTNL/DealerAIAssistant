package com.brand.agentpoc.service;

import java.util.List;

public record AnalyticsMetadata(
        String scenarioLabel,
        String scopeLabel,
        String metricLens,
        List<String> dataSources,
        List<String> limitations,
        String confidence
) {

    public AnalyticsMetadata {
        scenarioLabel = safe(scenarioLabel);
        scopeLabel = safe(scopeLabel);
        metricLens = safe(metricLens);
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        confidence = safe(confidence);
    }

    public static AnalyticsMetadata empty() {
        return new AnalyticsMetadata("", "", "", List.of(), List.of(), "");
    }

    public boolean isEmpty() {
        return scenarioLabel.isBlank()
                && scopeLabel.isBlank()
                && metricLens.isBlank()
                && dataSources.isEmpty()
                && limitations.isEmpty()
                && confidence.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
