package com.brand.agentpoc.observability.domain;

import java.util.Locale;
import java.util.Set;

public final class TelemetryFieldPolicy {

    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of(
            "password", "secret", "token", "api_key", "apikey", "prompt", "payload",
            "request_body", "response_body", "tool_argument", "model_output", "session_family"
    );

    private TelemetryFieldPolicy() {
    }

    public static boolean metricTagAllowed(CorrelationField field) {
        return false;
    }

    public static boolean traceAttributeAllowed(CorrelationField field) {
        return field != null && field.traceAttributeAllowed();
    }

    public static boolean forbiddenKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    public static String normalizeCorrelationValue(CorrelationField field, Object value) {
        if (!traceAttributeAllowed(field) || value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isBlank() || normalized.length() > field.maxLength()) {
            return null;
        }
        return normalized;
    }
}
