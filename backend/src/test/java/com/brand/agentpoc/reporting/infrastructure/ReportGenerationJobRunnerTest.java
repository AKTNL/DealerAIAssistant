package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;
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
        when(service.executeClaimed(11L, "worker-a", now)).thenReturn(job);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        ReportGenerationJobRunner runner = new ReportGenerationJobRunner(
                service,
                Clock.fixed(now, ZoneOffset.UTC),
                "worker-a",
                new OperationalTelemetry(observationRegistry)
        );

        ReportGenerationJobRunner.CycleResult result = runner.runOnce();

        assertThat(result.materialized()).isEqualTo(1);
        assertThat(result.cancelled()).isEqualTo(1);
        assertThat(result.executed()).isEqualTo(1);
        verify(service).executeClaimed(11L, "worker-a", now);
        assertThat(meterRegistry.get("agentpoc.report.job.execute")
                .tag("app.outcome", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("agentpoc.report.job.execute").timer().getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain(
                        "app.job.id", "app.subscription.id", "app.tenant.id", "app.user.id",
                        "app.correlation.id", "app.report.id");
    }

    @Test
    void permanentJobFailureRecordsErrorOutcome() {
        Instant now = Instant.parse("2026-08-14T01:30:00Z");
        ReportGenerationJobService service = mock(ReportGenerationJobService.class);
        JobView claimed = job(now);
        JobView failed = job(now, ReportGenerationJobStatus.PERMANENT_FAILURE);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(service.claimNext("worker-a", now)).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(claimed) : Optional.empty());
        when(service.executeClaimed(11L, "worker-a", now)).thenReturn(failed);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        ReportGenerationJobRunner runner = new ReportGenerationJobRunner(
                service,
                Clock.fixed(now, ZoneOffset.UTC),
                "worker-a",
                new OperationalTelemetry(observationRegistry)
        );

        runner.runOnce();

        assertThat(meterRegistry.get("agentpoc.report.job.execute")
                .tag("app.outcome", "error").timer().count()).isEqualTo(1);
    }

    private JobView job(Instant now) {
        return job(now, ReportGenerationJobStatus.RUNNING);
    }

    private JobView job(Instant now, ReportGenerationJobStatus status) {
        return new JobView(
                11L, 9L, 7L, 2L, now, "9:" + now, "daily",
                new ReportScope("ORGANIZATION", "10"), "en", "",
                status, 1, 3, "worker-a", now.plusSeconds(300),
                null, status == ReportGenerationJobStatus.PERMANENT_FAILURE ? "GENERATION_FAILED" : null,
                "trace-job", null, now, now, 1L);
    }
}
