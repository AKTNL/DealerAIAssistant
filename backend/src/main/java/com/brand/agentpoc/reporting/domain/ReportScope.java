package com.brand.agentpoc.reporting.domain;

import java.util.Set;
import java.util.stream.Collectors;

public record ReportScope(String type, String id) {

    public ReportScope {
        type = normalize(type, "GLOBAL").toUpperCase(java.util.Locale.ROOT);
        id = normalize(id, "");
        if (!Set.of("GLOBAL", "ORGANIZATION").contains(type)) {
            throw new IllegalArgumentException("Unsupported report scope type.");
        }
        if ("GLOBAL".equals(type) && !id.isBlank()) {
            throw new IllegalArgumentException("GLOBAL report scope cannot have a scope id.");
        }
        if ("ORGANIZATION".equals(type)) {
            if (id.isBlank() || id.length() > 128 || !id.matches("[0-9]+(?:,[0-9]+)*")) {
                throw new IllegalArgumentException("ORGANIZATION report scope id is invalid.");
            }
        }
    }

    public static ReportScope global() {
        return new ReportScope("GLOBAL", "");
    }

    public static ReportScope organization(Set<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("Organization report scope requires at least one grant node.");
        }
        String id = nodeIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return new ReportScope("ORGANIZATION", id);
    }

    public Set<Long> organizationNodeIds() {
        if (!"ORGANIZATION".equals(type)) {
            return Set.of();
        }
        return java.util.Arrays.stream(id.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
