package com.brand.agentpoc.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsCalculatorTest {

    private final AnalyticsCalculator calculator = new AnalyticsCalculator();

    @Test
    void percentageHandlesZeroDenominatorAndFormatsPercent() {
        assertThat(calculator.percentage(3, 4)).isEqualTo(75.0);
        assertThat(calculator.percentage(3, 0)).isZero();
        assertThat(calculator.formatPercent(75)).isEqualTo("75.0%");
    }

    @Test
    void aggregateValuePrefersMetadataAndFallsBackToItems() {
        DataQueryResponse metadataResponse = new DataQueryResponse(
                "tasks",
                Map.of(),
                2,
                List.of(Map.of("totalTaskCount", "5")),
                Map.of("totalTaskCount", 9)
        );
        DataQueryResponse itemResponse = new DataQueryResponse(
                "tasks",
                Map.of(),
                2,
                List.of(Map.of("totalTaskCount", "5"), Map.of("totalTaskCount", 7)),
                Map.of()
        );

        assertThat(calculator.aggregateValue(metadataResponse, "totalTaskCount")).isEqualTo(9);
        assertThat(calculator.aggregateValue(itemResponse, "totalTaskCount")).isEqualTo(7);
    }

    @Test
    void typedItemReadersHandleMissingAndStringValues() {
        Map<String, Object> item = Map.of(
                "count", "12",
                "enabled", "true",
                "name", "Dealer A"
        );

        assertThat(calculator.intValue(item, "count")).isEqualTo(12);
        assertThat(calculator.intValue(item, "missing")).isZero();
        assertThat(calculator.booleanValue(item, "enabled")).isTrue();
        assertThat(calculator.stringValue(item, "name")).isEqualTo("Dealer A");
    }
}
