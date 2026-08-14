package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.reporting.application.ReportGenerationJobService;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService.JobView;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ReportGenerationJobRunnerTest {

    @Test
    void cycleMaterializesCancelsClaimsAndExecutes() {
        Instant now = Instant.parse("2026-08-14T01:30:00Z");
        ReportGenerationJobService service = mock(ReportGenerationJobService.class);
        JobView job = job(now);
        when(service.materializeDueSubscriptions(now)).thenReturn(List.of(job));
        when(service.cancelPendingJobsForInactiveSubscriptions(now)).thenReturn(1);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(service.claimNext("worker-a", now)).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(job) : Optional.empty());
        ReportGenerationJobRunner runner = new ReportGenerationJobRunner(
                service, Clock.fixed(now, ZoneOffset.UTC), "worker-a");

        ReportGenerationJobRunner.CycleResult result = runner.runOnce();

        assertThat(result.materialized()).isEqualTo(1);
        assertThat(result.cancelled()).isEqualTo(1);
        assertThat(result.executed()).isEqualTo(1);
        verify(service).executeClaimed(11L, "worker-a", now);
    }

    private JobView job(Instant now) {
        return new JobView(
                11L, 9L, 7L, 2L, now, "9:" + now, "daily",
                new ReportScope("ORGANIZATION", "10"), "en", "",
                ReportGenerationJobStatus.RUNNING, 1, 3, "worker-a", now.plusSeconds(300),
                null, null, "trace-job", null, now, now, 1L);
    }
}
