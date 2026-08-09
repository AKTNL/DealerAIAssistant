package com.brand.agentpoc.agent.domain;

import java.util.EnumSet;
import java.util.Set;

public final class AgentExecutionPolicy {

    public static final int DEFAULT_MAX_TOOL_CALLS = 4;
    public static final int DEFAULT_MAX_PAGE_SIZE = 50;

    private final Set<AgentToolName> allowedTools;
    private final int maxToolCalls;
    private final int maxPageSize;

    public AgentExecutionPolicy(
            Set<AgentToolName> allowedTools,
            int maxToolCalls,
            int maxPageSize
    ) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            throw new IllegalArgumentException("At least one agent tool must be allowed.");
        }
        if (maxToolCalls < 1 || maxPageSize < 1) {
            throw new IllegalArgumentException("Agent limits must be positive.");
        }
        this.allowedTools = Set.copyOf(allowedTools);
        this.maxToolCalls = maxToolCalls;
        this.maxPageSize = maxPageSize;
    }

    public static AgentExecutionPolicy defaultPolicy() {
        return new AgentExecutionPolicy(
                EnumSet.allOf(AgentToolName.class),
                DEFAULT_MAX_TOOL_CALLS,
                DEFAULT_MAX_PAGE_SIZE
        );
    }

    public Set<AgentToolName> allowedTools() {
        return allowedTools;
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public int maxPageSize() {
        return maxPageSize;
    }

    public AgentToolName requireAllowed(String wireName) {
        AgentToolName toolName = AgentToolName.fromWireName(wireName);
        if (!allowedTools.contains(toolName)) {
            throw new IllegalArgumentException("Agent tool is not allowed.");
        }
        return toolName;
    }

    public void validatePage(Integer page, Integer pageSize) {
        if (page == null || page < 1) {
            throw new IllegalArgumentException("page must be at least 1.");
        }
        if (pageSize == null || pageSize < 1 || pageSize > maxPageSize) {
            throw new IllegalArgumentException("pageSize is outside the allowed range.");
        }
    }
}
