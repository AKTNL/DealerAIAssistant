package com.brand.agentpoc.ai;

import java.util.LinkedHashMap;
import java.util.Map;

final class ToolFilterSupport {

    private ToolFilterSupport() {
    }

    static Map<String, String> newFilters() {
        return new LinkedHashMap<>();
    }

    static void put(Map<String, String> filters, String key, String value) {
        if (value == null) {
            return;
        }

        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            filters.put(key, trimmed);
        }
    }

    static void put(Map<String, String> filters, String key, Integer value) {
        if (value != null) {
            filters.put(key, String.valueOf(value));
        }
    }

    static void put(Map<String, String> filters, String key, Boolean value) {
        if (value != null) {
            filters.put(key, String.valueOf(value));
        }
    }

    static void requireText(String paramName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(paramName + " is required.");
        }
    }

    static void requireInteger(String paramName, Integer value) {
        if (value == null) {
            throw new IllegalArgumentException(paramName + " is required.");
        }
    }

    static void requireBoolean(String paramName, Boolean value) {
        if (value == null) {
            throw new IllegalArgumentException(paramName + " is required.");
        }
    }
}
