package com.brand.agentpoc.service.analytics;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsChartRenderer {

    private final ReportRenderer reportRenderer;

    public AnalyticsChartRenderer(ReportRenderer reportRenderer) {
        this.reportRenderer = reportRenderer;
    }

    public enum ChartEntityType {
        DEALER,
        CAMPAIGN,
        STAGE,
        SOURCE
    }

    public String xyChart(
            String title,
            String xLabel,
            String yLabel,
            List<String> labels,
            List<Double> values,
            Double averageLine
    ) {
        return reportRenderer.chartJsonBar(title, chartMetricName(yLabel), chartMetricType(yLabel), labels, values, averageLine);
    }

    public String xyChart(
            String title,
            String xLabel,
            String yLabel,
            ChartEntityType entityType,
            List<String> labels,
            List<Double> values,
            Double averageLine
    ) {
        return reportRenderer.chartJsonBar(
                title,
                chartMetricName(yLabel),
                chartMetricType(yLabel),
                rendererChartEntityType(entityType),
                labels,
                values,
                averageLine
        );
    }

    public String pieChart(String title, Map<String, Double> slices) {
        return reportRenderer.chartJsonPie(title, slices);
    }

    public String pieChart(String title, ChartEntityType entityType, Map<String, Double> slices) {
        return reportRenderer.chartJsonPie(title, rendererChartEntityType(entityType), slices);
    }

    public String fallbackBars(List<String> labels, List<Double> values, double maxValue) {
        return reportRenderer.fallbackBars(labels, values, maxValue);
    }

    public String fallbackBars(ChartEntityType entityType, List<String> labels, List<Double> values, double maxValue) {
        return reportRenderer.fallbackBars(rendererChartEntityType(entityType), labels, values, maxValue);
    }

    private ReportRenderer.ChartEntityType rendererChartEntityType(ChartEntityType entityType) {
        return ReportRenderer.ChartEntityType.valueOf(entityType.name());
    }

    private String chartMetricName(String yLabel) {
        return String.valueOf(yLabel == null ? "" : yLabel)
                .replaceAll("\\s*\\([^)]*\\)", "")
                .trim();
    }

    private String chartMetricType(String yLabel) {
        String label = String.valueOf(yLabel == null ? "" : yLabel).toLowerCase(Locale.ROOT);
        return label.contains("%") || label.contains("rate") || label.contains("\u7387")
                ? "percentage"
                : "absolute";
    }
}
