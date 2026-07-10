package com.brand.agentpoc.service;

import java.util.List;

public record AnalyticsPlan(
        Scenario scenario,
        AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow,
        String scopeSummary,
        List<String> progressMessages,
        String visibleThinking,
        String groundedReference,
        String fallbackReply,
        AnalyticsMetadata metadata
) {

    public AnalyticsPlan {
        scenarioWorkflow = scenarioWorkflow == null
                ? AnalyticsScenarioCatalog.forScenario(scenario)
                : scenarioWorkflow;
        progressMessages = progressMessages == null ? List.of() : List.copyOf(progressMessages);
        metadata = metadata == null ? AnalyticsMetadata.empty() : metadata;
    }

    public AnalyticsPlan(
            Scenario scenario,
            AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow,
            String scopeSummary,
            List<String> progressMessages,
            String visibleThinking,
            String groundedReference,
            String fallbackReply
    ) {
        this(
                scenario,
                scenarioWorkflow,
                scopeSummary,
                progressMessages,
                visibleThinking,
                groundedReference,
                fallbackReply,
                AnalyticsMetadata.empty()
        );
    }

    public enum Scenario {
        TARGET_ACHIEVEMENT,
        OPPORTUNITY_FUNNEL,
        SALES_FOLLOW_UP,
        CAMPAIGN_PERFORMANCE,
        DEALER_BENCHMARK,
        LEAD_SOURCE,
        DEALER_BUSINESS_ACTIVITY,
        DATA_OVERVIEW
    }
}
