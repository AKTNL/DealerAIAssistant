package com.brand.agentpoc.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportSubscriptionScheduleTest {

    @Test
    void calculatesDailyWeeklyAndMonthlyNextRuns() {
        Instant reference = Instant.parse("2026-08-14T00:30:00Z");

        assertThat(ReportSubscriptionSchedule.parse(
                "DAILY", "09:00", "Asia/Shanghai", null, null).nextAfter(reference))
                .isEqualTo(Instant.parse("2026-08-14T01:00:00Z"));
        assertThat(ReportSubscriptionSchedule.parse(
                "WEEKLY", "09:00", "Asia/Shanghai", 1, null).nextAfter(reference))
                .isEqualTo(Instant.parse("2026-08-17T01:00:00Z"));
        assertThat(ReportSubscriptionSchedule.parse(
                "MONTHLY", "09:00", "Asia/Shanghai", null, 15).nextAfter(reference))
                .isEqualTo(Instant.parse("2026-08-15T01:00:00Z"));
    }

    @Test
    void resolvesDstGapToFirstValidTimeAndOverlapToEarlierOffset() {
        ReportSubscriptionSchedule gap = ReportSubscriptionSchedule.parse(
                "DAILY", "02:30", "America/New_York", null, null);
        ReportSubscriptionSchedule overlap = ReportSubscriptionSchedule.parse(
                "DAILY", "01:30", "America/New_York", null, null);

        assertThat(gap.nextAfter(Instant.parse("2026-03-07T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
        assertThat(overlap.nextAfter(Instant.parse("2026-10-31T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    void rejectsInvalidTimeZonesSelectorsAndSubMinuteTimes() {
        assertThatThrownBy(() -> ReportSubscriptionSchedule.parse(
                "DAILY", "09:00", "GMT+08:00", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSubscriptionSchedule.parse(
                "WEEKLY", "09:00", "Asia/Shanghai", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSubscriptionSchedule.parse(
                "MONTHLY", "09:00", "Asia/Shanghai", null, 31))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSubscriptionSchedule.parse(
                "DAILY", "09:00:30", "Asia/Shanghai", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
