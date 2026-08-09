package com.brand.agentpoc.agent.application;

import com.brand.agentpoc.service.AnalyticsMetadata;

public record AgentScenarioAnalysis(
        String scenario,
        String scope,
        String groundedReference,
        String fallbackReply,
        AnalyticsMetadata metadata
) {
}
