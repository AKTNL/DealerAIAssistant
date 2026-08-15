package com.brand.agentpoc.modelusage.domain;

import java.util.Objects;

public record ModelUsageContext(
        Long tenantId,
        Long userId,
        ModelUsageScenario scenario,
        String provider,
        String model,
        String traceId,
        boolean cacheHit
) {
    public ModelUsageContext {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        scenario = Objects.requireNonNull(scenario, "scenario is required");
        provider = normalized(provider, "unknown", 64);
        model = normalized(model, "unknown", 128);
        traceId = normalized(traceId, "unavailable", 128);
    }

    private static String normalized(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
