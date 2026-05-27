package com.brand.agentpoc.service.analytics;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AnalyticsCalculator {

    public int aggregateValue(List<Map<String, Object>> items, String fieldName, int fallback) {
        return items.stream()
                .map(item -> item.get(fieldName))
                .filter(Objects::nonNull)
                .mapToInt(this::toInt)
                .max()
                .orElse(fallback);
    }

    public int aggregateValue(DataQueryResponse response, String fieldName) {
        Object metadataValue = response.metadata().get(fieldName);
        if (metadataValue != null) {
            return toInt(metadataValue);
        }
        return aggregateValue(response.items(), fieldName, response.count());
    }

    public String stringValue(Map<String, Object> item, String fieldName) {
        Object value = item.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    public int intValue(Map<String, Object> item, String fieldName) {
        return toInt(item.get(fieldName));
    }

    public int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean booleanValue(Map<String, Object> item, String fieldName) {
        Object value = item.get(fieldName);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return numerator * 100.0 / denominator;
    }

    public String formatPercent(double value) {
        return String.format(java.util.Locale.US, "%.1f%%", value);
    }

    public String describeDeltaToAverage(double deltaToAverage) {
        if (Math.abs(deltaToAverage) < 0.05) {
            return "与平均值基本持平";
        }
        if (deltaToAverage > 0.0) {
            return "较平均值低 %.1f 个百分点".formatted(deltaToAverage);
        }
        return "较平均值高 %.1f 个百分点".formatted(Math.abs(deltaToAverage));
    }
}
