package com.brand.agentpoc.reporting.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ReportType {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    TOPIC("topic");

    private final String wireName;

    ReportType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static ReportType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reportType is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "daily", "日报" -> DAILY;
            case "weekly", "周报" -> WEEKLY;
            case "monthly", "月报" -> MONTHLY;
            case "topic", "专题", "专题报告" -> TOPIC;
            default -> throw new IllegalArgumentException(
                    "reportType must be daily, weekly, monthly, or topic.");
        };
    }
}
