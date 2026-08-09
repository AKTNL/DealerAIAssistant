package com.brand.agentpoc.agent.domain;

import java.util.Arrays;
import java.util.List;

public enum AgentToolName {

    GET_DASHBOARD_SUMMARY("getDashboardSummary"),
    QUERY_METRIC("queryMetric"),
    QUERY_DETAILS("queryDetails"),
    RUN_SCENARIO_ANALYSIS("runScenarioAnalysis"),
    RETRIEVE_KNOWLEDGE("retrieveKnowledge");

    private final String wireName;

    AgentToolName(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static AgentToolName fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(tool -> tool.wireName.equals(wireName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Agent tool is not allowed."));
    }

    public static List<String> wireNames() {
        return Arrays.stream(values()).map(AgentToolName::wireName).toList();
    }
}
