package com.brand.agentpoc.reporting.domain;

public record ReportScope(String type, String id) {

    public ReportScope {
        type = normalize(type, "GLOBAL").toUpperCase(java.util.Locale.ROOT);
        id = normalize(id, "");
        if (!"GLOBAL".equals(type)) {
            throw new IllegalArgumentException("Only GLOBAL report scope is supported.");
        }
        if (!id.isBlank()) {
            throw new IllegalArgumentException("GLOBAL report scope cannot have a scope id.");
        }
    }

    public static ReportScope global() {
        return new ReportScope("GLOBAL", "");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
