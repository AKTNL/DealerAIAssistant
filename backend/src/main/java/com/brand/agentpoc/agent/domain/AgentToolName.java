package com.brand.agentpoc.agent.domain;

import com.brand.agentpoc.auth.domain.PermissionKey;
import java.util.Arrays;
import java.util.List;

public enum AgentToolName {

    GET_DASHBOARD_SUMMARY("getDashboardSummary", PermissionKey.DASHBOARD_READ),
    QUERY_METRIC("queryMetric", PermissionKey.DATA_READ),
    QUERY_DETAILS("queryDetails", PermissionKey.DATA_READ),
    RUN_SCENARIO_ANALYSIS("runScenarioAnalysis", PermissionKey.DATA_READ),
    RETRIEVE_KNOWLEDGE("retrieveKnowledge", PermissionKey.KNOWLEDGE_QUERY),
    GENERATE_REPORT_DRAFT("generateReportDraft", PermissionKey.REPORT_GENERATE);

    private final String wireName;
    private final PermissionKey requiredPermission;

    AgentToolName(String wireName, PermissionKey requiredPermission) {
        this.wireName = wireName;
        this.requiredPermission = requiredPermission;
    }

    public String wireName() {
        return wireName;
    }

    public PermissionKey requiredPermission() {
        return requiredPermission;
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
