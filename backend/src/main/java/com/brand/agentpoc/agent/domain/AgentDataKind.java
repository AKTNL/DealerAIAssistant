package com.brand.agentpoc.agent.domain;

import java.util.Locale;

public enum AgentDataKind {

    TARGET,
    OPPORTUNITY,
    LEAD,
    TASK,
    CAMPAIGN;

    public static AgentDataKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("metric or dataset is required.");
        }

        String normalized = value.trim()
                .replaceAll("[-_\\s]", "")
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "target", "targets", "targetachievement" -> TARGET;
            case "opportunity", "opportunities", "opportunityfunnel" -> OPPORTUNITY;
            case "lead", "leads", "leadsource" -> LEAD;
            case "task", "tasks", "salesfollowup" -> TASK;
            case "campaign", "campaigns", "campaignperformance" -> CAMPAIGN;
            default -> throw new IllegalArgumentException("Unsupported metric or dataset.");
        };
    }
}
