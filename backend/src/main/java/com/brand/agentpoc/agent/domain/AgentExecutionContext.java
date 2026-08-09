package com.brand.agentpoc.agent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AgentExecutionContext {

    private final AgentRequestScope scope;
    private final AgentExecutionPolicy policy;
    private final String traceId;
    private final List<TraceEntry> traceEntries = new ArrayList<>();
    private int usedToolCalls;

    public AgentExecutionContext(
            AgentRequestScope scope,
            AgentExecutionPolicy policy,
            String traceId
    ) {
        this.scope = scope == null ? AgentRequestScope.unauthenticated("") : scope;
        this.policy = policy == null ? AgentExecutionPolicy.defaultPolicy() : policy;
        this.traceId = normalizeTraceId(traceId);
    }

    public AgentRequestScope scope() {
        return scope;
    }

    public AgentExecutionPolicy policy() {
        return policy;
    }

    public String traceId() {
        return traceId;
    }

    public synchronized void acquire(AgentToolName toolName) {
        if (!policy.allowedTools().contains(toolName)) {
            throw new IllegalArgumentException("Agent tool is not allowed.");
        }
        if (usedToolCalls >= policy.maxToolCalls()) {
            throw new IllegalStateException("Agent tool call budget exceeded.");
        }
        usedToolCalls++;
    }

    public synchronized void record(AgentToolName toolName, TraceStatus status, String reason) {
        traceEntries.add(new TraceEntry(toolName.wireName(), status, safeReason(reason)));
    }

    public synchronized List<TraceEntry> traceEntries() {
        return List.copyOf(traceEntries);
    }

    private String normalizeTraceId(String value) {
        if (value != null && value.matches("[A-Za-z0-9_-]{1,64}")) {
            return value;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String safeReason(String reason) {
        return reason != null && reason.matches("[a-z_]{1,64}") ? reason : "unspecified";
    }

    public enum TraceStatus {
        SUCCESS,
        REJECTED,
        FAILED
    }

    public record TraceEntry(String tool, TraceStatus status, String reason) {
    }
}
