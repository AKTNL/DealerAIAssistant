package com.brand.agentpoc.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.dto.response.CurrentDateResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CurrentDateToolsTest {

    @Test
    void returnsTheCurrentDateWithYearMonthAndQuarter() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC);
        CurrentDateTools tools = new CurrentDateTools(clock);

        CurrentDateResponse response = tools.getCurrentDate();

        assertThat(response.currentDate()).isEqualTo("2026-05-11");
        assertThat(response.currentYear()).isEqualTo(2026);
        assertThat(response.currentMonth()).isEqualTo(5);
        assertThat(response.currentQuarter()).isEqualTo(2);
    }
}
