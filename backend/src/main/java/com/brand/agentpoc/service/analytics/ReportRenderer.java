package com.brand.agentpoc.service.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportRenderer {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final Map<String, String> TABLE_LABELS_ZH = Map.ofEntries(
            Map.entry("Metric", "\u6307\u6807"),
            Map.entry("Value", "\u6570\u503c"),
            Map.entry("Dealers covered", "\u8986\u76d6\u95e8\u5e97\u6570"),
            Map.entry("Lowest achievement", "\u6700\u4f4e\u8fbe\u6210\u95e8\u5e97"),
            Map.entry("Highest achievement", "\u6700\u9ad8\u8fbe\u6210\u95e8\u5e97"),
            Map.entry("Average achievement", "\u5e73\u5747\u8fbe\u6210\u7387"),
            Map.entry("Total opportunities", "\u5546\u673a\u603b\u6570"),
            Map.entry("Won opportunities", "\u5df2\u8d62\u5355\u5546\u673a"),
            Map.entry("Lost opportunities", "\u5df2\u4e22\u5355\u5546\u673a"),
            Map.entry("High-probability open opportunities", "\u9ad8\u6982\u7387\u672a\u6210\u4ea4\u5546\u673a"),
            Map.entry("Primary lead source", "\u4e3b\u8981\u7ebf\u7d22\u6765\u6e90"),
            Map.entry("Total tasks", "\u4efb\u52a1\u603b\u6570"),
            Map.entry("Completed tasks", "\u5df2\u5b8c\u6210\u4efb\u52a1"),
            Map.entry("Open tasks", "\u672a\u5b8c\u6210\u4efb\u52a1"),
            Map.entry("Overdue tasks", "\u903e\u671f\u4efb\u52a1"),
            Map.entry("Highest backlog dealer", "\u79ef\u538b\u6700\u9ad8\u95e8\u5e97"),
            Map.entry("Highest activity dealer", "\u6d3b\u8dc3\u5ea6\u6700\u9ad8\u95e8\u5e97"),
            Map.entry("Completion rate", "\u5b8c\u6210\u7387"),
            Map.entry("Follow-up activity", "\u8ddf\u8fdb\u6d3b\u8dc3\u5ea6"),
            Map.entry("Campaign count", "\u6d3b\u52a8\u6570\u91cf"),
            Map.entry("Best campaign", "\u6700\u4f73\u6d3b\u52a8"),
            Map.entry("Average attainment", "\u5e73\u5747\u8fbe\u6210\u7387"),
            Map.entry("Total leads", "\u7ebf\u7d22\u603b\u6570"),
            Map.entry("Highest-volume source", "\u7ebf\u7d22\u91cf\u6700\u9ad8\u6765\u6e90"),
            Map.entry("Best conversion source", "\u8f6c\u5316\u7387\u6700\u9ad8\u6765\u6e90"),
            Map.entry("Best dealer achievement rate", "\u6700\u4f73\u95e8\u5e97\u8fbe\u6210\u7387"),
            Map.entry("Lowest dealer achievement rate", "\u6700\u4f4e\u95e8\u5e97\u8fbe\u6210\u7387"),
            Map.entry("Dealers compared", "\u5bf9\u6bd4\u95e8\u5e97\u6570"),
            Map.entry("Available sample records", "\u53ef\u7528\u6837\u672c\u8bb0\u5f55"),
            Map.entry("Requested scope", "\u8bf7\u6c42\u8303\u56f4"),
            Map.entry("Requested topic", "\u8bf7\u6c42\u4e3b\u9898"),
            Map.entry("Data quality", "\u6570\u636e\u8d28\u91cf"),
            Map.entry("Observed rows", "\u89c2\u6d4b\u884c\u6570"),
            Map.entry("Primary numerator", "\u4e3b\u8981\u5206\u5b50"),
            Map.entry("Primary denominator", "\u4e3b\u8981\u5206\u6bcd"),
            Map.entry("Excluded units", "\u5df2\u6392\u9664\u5355\u5143"),
            Map.entry("Achievement gap (high - low)", "\u8fbe\u6210\u7387\u5dee\u8ddd\uff08\u6700\u9ad8-\u6700\u4f4e\uff09"),
            Map.entry("Campaigns below average", "\u4f4e\u4e8e\u5e73\u5747\u7684\u6d3b\u52a8\u6570"),
            Map.entry("Unique sources", "\u552f\u4e00\u6765\u6e90\u6570"),
            Map.entry("Gap (best - lowest)", "\u5dee\u8ddd\uff08\u6700\u4f73-\u6700\u4f4e\uff09"),
            Map.entry("Overdue rate", "\u903e\u671f\u7387")
    );

    public enum ChartEntityType {
        DEALER,
        CAMPAIGN,
        STAGE,
        SOURCE,
        GENERIC
    }

    public String htmlTable(List<String[]> rows, String language) {
        StringBuilder table = new StringBuilder();
        table.append("<table>\n");
        table.append("    <thead>\n");
        table.append("        <tr>\n");
        table.append("            <th>").append(escapeHtml(localizeTableLabel("Metric", language))).append("</th>\n");
        table.append("            <th>").append(escapeHtml(localizeTableLabel("Value", language))).append("</th>\n");
        table.append("        </tr>\n");
        table.append("    </thead>\n");
        table.append("    <tbody>\n");
        for (String[] row : rows) {
            table.append("        <tr>\n");
            table.append("            <td>").append(escapeHtml(localizeTableLabel(row[0], language))).append("</td>\n");
            table.append("            <td>").append(escapeHtml(row[1])).append("</td>\n");
            table.append("        </tr>\n");
        }
        table.append("    </tbody>\n");
        table.append("</table>");
        return table.toString();
    }

    public String fallbackSummaryTable(List<String[]> rows, String language) {
        boolean isZh = "zh".equals(language);
        StringBuilder table = new StringBuilder();
        table.append("<table>\n<thead><tr>");
        table.append(isZh
                ? "<th>指标</th><th>数值</th><th>范围</th><th>对比基准</th>"
                : "<th>Metric</th><th>Value</th><th>Scope</th><th>Benchmark</th>");
        table.append("</tr></thead>\n<tbody>\n");
        for (String[] row : rows) {
            table.append("<tr>");
            table.append("<td>").append(escapeHtml(localizeTableLabel(row[0], language))).append("</td>");
            table.append("<td>").append(escapeHtml(row[1])).append("</td>");
            table.append("<td>").append(isZh ? "当前范围" : "Current scope").append("</td>");
            table.append("<td>").append("—").append("</td>");
            table.append("</tr>\n");
        }
        table.append("</tbody>\n</table>");
        return table.toString();
    }

    public String localizeTableLabel(String label, String language) {
        if (!"zh".equals(language)) {
            return label;
        }
        return TABLE_LABELS_ZH.getOrDefault(label, label);
    }

    public String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public String bulletList(List<String> items) {
        return items.stream()
                .map(item -> "- " + escapeHtml(item))
                .collect(Collectors.joining("\n"));
    }

    public String followUpQuestions(List<String> questions) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(i + 1).append(". ").append(escapeHtml(questions.get(i)));
        }
        return builder.toString();
    }

    public String chartJsonBar(
            String title,
            String metric,
            String metricType,
            List<String> categories,
            List<Double> values,
            Double averageLine
    ) {
        return chartJsonBar(title, metric, metricType, ChartEntityType.GENERIC, categories, values, averageLine);
    }

    public String chartJsonBar(
            String title,
            String metric,
            String metricType,
            ChartEntityType entityType,
            List<String> categories,
            List<Double> values,
            Double averageLine
    ) {
        List<String> safeCategories = chartLabels(entityType, categories == null ? List.of() : categories);
        List<Double> safeValues = normalizeChartValues(values, safeCategories.size());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("type", "bar");
        model.put("title", title == null ? "" : title);
        model.put("metric", metric == null ? "" : metric);
        if (metricType != null && !metricType.isBlank()) {
            model.put("metricType", metricType);
        }

        if (safeCategories.size() < 2 || isEmptyOrAllZero(safeValues)) {
            model.put("categories", List.of());
            model.put("values", List.of());
            model.put("emptyMessage", resolveEmptyMessage(title, metric));
        } else {
            model.put("categories", safeCategories);
            model.put("values", safeValues.stream().map(value -> value == null ? 0.0 : value).toList());
            if (averageLine != null && Double.isFinite(averageLine)) {
                model.put("averageLine", averageLine);
            }
        }

        return chartJsonFence(model);
    }

    public String chartJsonPie(String title, Map<String, Double> slices) {
        return chartJsonPie(title, ChartEntityType.GENERIC, slices);
    }

    public String chartJsonPie(String title, ChartEntityType entityType, Map<String, Double> slices) {
        Map<String, Double> safeSlices = chartSlices(entityType, slices == null ? Map.of() : slices);
        List<Map.Entry<String, Double>> sorted = safeSlices.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()))
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .toList();

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("type", "pie");
        model.put("title", title == null ? "" : title);

        if (sorted.isEmpty() || sorted.stream().allMatch(entry -> isZero(entry.getValue()))) {
            model.put("slices", List.of());
            model.put("emptyMessage", resolveEmptyMessage(title, null));
            return chartJsonFence(model);
        }

        List<Map.Entry<String, Double>> visible = sorted;
        if (sorted.size() > 6) {
            List<Map.Entry<String, Double>> top = new ArrayList<>(sorted.subList(0, 5));
            double other = sorted.subList(5, sorted.size()).stream()
                    .mapToDouble(Map.Entry::getValue)
                    .sum();
            top.add(Map.entry(otherSliceLabel(title), other));
            visible = top;
        }

        List<Map<String, Object>> chartSlices = visible.stream()
                .map(entry -> {
                    Map<String, Object> slice = new LinkedHashMap<>();
                    slice.put("name", entry.getKey());
                    slice.put("value", entry.getValue());
                    return slice;
                })
                .toList();
        model.put("slices", chartSlices);

        return chartJsonFence(model);
    }

    public String fallbackBars(List<String> labels, List<Double> values, double maxValue) {
        return fallbackBars(ChartEntityType.GENERIC, labels, values, maxValue);
    }

    public String fallbackBars(ChartEntityType entityType, List<String> labels, List<Double> values, double maxValue) {
        List<String> safeLabels = chartLabels(entityType, labels);
        StringBuilder sb = new StringBuilder();
        sb.append("```\n");
        int maxBarLen = 30;
        for (int i = 0; i < safeLabels.size(); i++) {
            String label = escapeHtml(safeLabels.get(i));
            double value = values.get(i);
            int barLen = maxValue > 0 ? (int) Math.round((value / maxValue) * maxBarLen) : 0;
            String bar = "█".repeat(Math.max(0, barLen));
            sb.append(String.format("%-20s %s %.1f\n", label, bar, value));
        }
        sb.append("```");
        return sb.toString();
    }

    public List<String> chartLabels(ChartEntityType entityType, List<String> rawLabels) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < rawLabels.size(); i++) {
            String base = shortenChartLabel(entityType, rawLabels.get(i), i + 1);
            int count = counts.getOrDefault(base, 0) + 1;
            counts.put(base, count);
            labels.add(count == 1 ? base : base + " #" + count);
        }
        return labels;
    }

    private String shortenChartLabel(ChartEntityType entityType, String rawLabel, int ordinal) {
        String cleaned = String.valueOf(rawLabel == null ? "" : rawLabel)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[,\\[\\]\"<>]+", " ")
                .replaceAll("\\p{Cntrl}", "")
                .trim()
                .replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return "Item " + ordinal;
        }
        int limit = switch (entityType) {
            case DEALER -> 11;
            case CAMPAIGN, GENERIC -> 12;
            case STAGE, SOURCE -> 14;
        };
        if (cleaned.length() <= limit) {
            return cleaned;
        }
        if (entityType == ChartEntityType.CAMPAIGN) {
            return "..." + cleaned.substring(Math.max(0, cleaned.length() - (limit - 3)));
        }
        return cleaned.substring(0, Math.max(1, limit - 3)) + "...";
    }

    private Map<String, Double> chartSlices(ChartEntityType entityType, Map<String, Double> rawSlices) {
        List<String> labels = chartLabels(entityType, new ArrayList<>(rawSlices.keySet()));
        List<Double> values = rawSlices.values().stream().toList();
        Map<String, Double> slices = new LinkedHashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            slices.put(labels.get(i), values.get(i));
        }
        return slices;
    }

    private String sanitizeMermaidLabel(String name) {
        if (name == null) {
            return "";
        }
        return name.replace("\"", "\\\"")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String chartJsonFence(Map<String, Object> model) {
        try {
            return "```chart-json\n" + JSON_MAPPER.writeValueAsString(model) + "\n```";
        } catch (JsonProcessingException e) {
            return "```chart-empty\nreason: SERIALIZATION_ERROR\ntitle: Chart unavailable\nbody: Failed to serialize chart data.\n```";
        }
    }

    private List<Double> normalizeChartValues(List<Double> values, int size) {
        List<Double> normalized = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Double value = values != null && i < values.size() ? values.get(i) : null;
            normalized.add(value != null && Double.isFinite(value) ? value : null);
        }
        return normalized;
    }

    private boolean isEmptyOrAllZero(List<Double> values) {
        return values == null
                || values.isEmpty()
                || values.stream().allMatch(value -> value == null || isZero(value));
    }

    private boolean isZero(double value) {
        return Math.abs(value) < 0.000001;
    }

    private String resolveEmptyMessage(String title, String metric) {
        String subject = title != null && !title.isBlank() ? title : metric;
        if (subject == null || subject.isBlank()) {
            subject = "chart";
        }

        if (containsHan(title) || containsHan(metric)) {
            return "\u5f53\u524d\u8303\u56f4\u5185\u6682\u65e0\u6709\u6548\u7684" + subject + "\u6570\u636e";
        }

        return "No usable data for " + subject;
    }

    private boolean containsHan(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String otherSliceLabel(String title) {
        return containsHan(title) ? "\u5176\u4ed6" : "Other";
    }
}
