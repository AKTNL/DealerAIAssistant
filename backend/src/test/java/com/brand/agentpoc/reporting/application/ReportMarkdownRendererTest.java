package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.reporting.domain.ReportType;
import org.junit.jupiter.api.Test;

class ReportMarkdownRendererTest {

    @Test
    void parsesSupportedReportTypeAliases() {
        assertThat(ReportType.parse("日报")).isEqualTo(ReportType.DAILY);
        assertThat(ReportType.parse("weekly")).isEqualTo(ReportType.WEEKLY);
        assertThat(ReportType.parse("月报")).isEqualTo(ReportType.MONTHLY);
        assertThat(ReportType.parse("专题报告")).isEqualTo(ReportType.TOPIC);
    }
}
