package com.brand.agentpoc.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportRendererTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReportRenderer renderer = new ReportRenderer();

    @Test
    void htmlTableEscapesValuesAndLocalizesChineseLabels() {
        String table = renderer.htmlTable(
                List.<String[]>of(new String[]{"Total opportunities", "75% <strong>"}),
                "zh"
        );

        assertThat(table).contains("<th>指标</th>");
        assertThat(table).contains("<td>商机总数</td>");
        assertThat(table).contains("75% &lt;strong&gt;");
    }

    @Test
    void chartJsonBarOutputsStrictJsonWithSanitizedLabelsAndMetricType() throws Exception {
        String chart = renderer.chartJsonBar(
                "Dealer <Rank>",
                "Achievement Rate",
                "percentage",
                ReportRenderer.ChartEntityType.DEALER,
                List.of("Dealer <A>", "Dealer <A>"),
                List.of(72.5, 80.0),
                76.0
        );

        JsonNode json = parseChartJson(chart);

        assertThat(chart).startsWith("```chart-json\n");
        assertThat(chart).doesNotContain("```mermaid");
        assertThat(json.get("type").asText()).isEqualTo("bar");
        assertThat(json.get("title").asText()).isEqualTo("Dealer <Rank>");
        assertThat(json.get("metric").asText()).isEqualTo("Achievement Rate");
        assertThat(json.get("metricType").asText()).isEqualTo("percentage");
        assertThat(json.get("categories").get(0).asText()).isEqualTo("Dealer A");
        assertThat(json.get("categories").get(1).asText()).isEqualTo("Dealer A #2");
        assertThat(json.get("values").get(0).asDouble()).isEqualTo(72.5);
        assertThat(json.get("averageLine").asDouble()).isEqualTo(76.0);
    }

    @Test
    void chartJsonBarUsesEmptyStateForAllZeroOrSingleComparableLabel() throws Exception {
        String chart = renderer.chartJsonBar(
                "Target Achievement",
                "Attainment",
                "percentage",
                ReportRenderer.ChartEntityType.DEALER,
                List.of("Dealer A", "Dealer B"),
                List.of(0.0, 0.0),
                null
        );

        JsonNode json = parseChartJson(chart);

        assertThat(json.get("categories")).isEmpty();
        assertThat(json.get("values")).isEmpty();
        assertThat(json.get("emptyMessage").asText()).contains("Target Achievement");
    }

    @Test
    void chartJsonPieSortsLimitsAndMergesOverflowSlices() throws Exception {
        Map<String, Double> slices = new LinkedHashMap<>();
        slices.put("A", 7.0);
        slices.put("B", 6.0);
        slices.put("C", 5.0);
        slices.put("D", 4.0);
        slices.put("E", 3.0);
        slices.put("F", 2.0);
        slices.put("G", 1.0);

        String chart = renderer.chartJsonPie("Lead Sources", ReportRenderer.ChartEntityType.SOURCE, slices);

        JsonNode json = parseChartJson(chart);

        assertThat(json.get("type").asText()).isEqualTo("pie");
        assertThat(json.get("slices")).hasSize(6);
        assertThat(json.get("slices").get(0).get("name").asText()).isEqualTo("A");
        assertThat(json.get("slices").get(5).get("name").asText()).isEqualTo("Other");
        assertThat(json.get("slices").get(5).get("value").asDouble()).isEqualTo(3.0);
    }

    private static JsonNode parseChartJson(String chart) throws Exception {
        assertThat(chart).startsWith("```chart-json\n");
        assertThat(chart).endsWith("\n```");
        String json = chart.substring("```chart-json\n".length(), chart.length() - "\n```".length());
        return JSON.readTree(json);
    }
}
