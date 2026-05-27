package com.brand.agentpoc.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolFilterSupportTest {

    @Test
    void putAddsTrimmedTextAndSkipsBlankValues() {
        Map<String, String> filters = ToolFilterSupport.newFilters();

        ToolFilterSupport.put(filters, "city", " Beijing ");
        ToolFilterSupport.put(filters, "dealerCode", " ");
        ToolFilterSupport.put(filters, "stageName", (String) null);

        assertThat(filters).containsExactly(Map.entry("city", "Beijing"));
    }

    @Test
    void putAddsIntegerAndBooleanValues() {
        Map<String, String> filters = ToolFilterSupport.newFilters();

        ToolFilterSupport.put(filters, "targetYear", 2026);
        ToolFilterSupport.put(filters, "isConverted", true);
        ToolFilterSupport.put(filters, "targetMonth", (Integer) null);

        assertThat(filters).containsExactly(
                Map.entry("targetYear", "2026"),
                Map.entry("isConverted", "true")
        );
    }

    @Test
    void requireTextRejectsNullAndBlankValues() {
        assertThatThrownBy(() -> ToolFilterSupport.requireText("raw", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("raw is required.");

        assertThatThrownBy(() -> ToolFilterSupport.requireText("raw", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("raw is required.");
    }

    @Test
    void requireIntegerAndBooleanRejectMissingValues() {
        assertThatThrownBy(() -> ToolFilterSupport.requireInteger("targetYear", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetYear is required.");

        assertThatThrownBy(() -> ToolFilterSupport.requireBoolean("raw", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("raw is required.");
    }
}
