package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.observability.domain.CorrelationField;
import com.brand.agentpoc.observability.domain.OperationalEvent;
import com.brand.agentpoc.observability.domain.OperationalOutcome;
import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService.JobView;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.reporting.jobs", name = "enabled", havingValue = "true")
public class ReportGenerationJobRunner {

    static final int MAX_JOBS_PER_CYCLE = 50;
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationJobRunner.class);

    private final ReportGenerationJobService jobService;
    private final Clock clock;
    private final String workerId;
    private final OperationalTelemetry operationalTelemetry;

    public ReportGenerationJobRunner(ReportGenerationJobService jobService, Clock clock) {
        this(jobService, clock, "report-worker-" + UUID.randomUUID(), OperationalTelemetry.noop());
    }

    @Autowired
    public ReportGenerationJobRunner(
            ReportGenerationJobService jobService,
            Clock clock,
            OperationalTelemetry operationalTelemetry
    ) {
        this(jobService, clock, "report-worker-" + UUID.randomUUID(), operationalTelemetry);
    }

    ReportGenerationJobRunner(ReportGenerationJobService jobService, Clock clock, String workerId) {
        this(jobService, clock, workerId, OperationalTelemetry.noop());
    }

    ReportGenerationJobRunner(
            ReportGenerationJobService jobService,
            Clock clock,
            String workerId,
            OperationalTelemetry operationalTelemetry
    ) {
        this.jobService = jobService;
        this.clock = clock;
        this.workerId = workerId;
        this.operationalTelemetry = operationalTelemetry;
    }

    @Scheduled(fixedDelayString = "${app.reporting.jobs.poll-interval:30s}")
    public void runScheduledCycle() {
        try {
            CycleResult result = runOnce();
            if (result.materialized() > 0 || result.cancelled() > 0 || result.executed() > 0) {
                log.info("Report job cycle completed: workerId={}, materialized={}, cancelled={}, executed={}",
                        workerId, result.materialized(), result.cancelled(), result.executed());
            }
        } catch (RuntimeException exception) {
            log.warn("Report job cycle failed: workerId={}, reason={}",
                    workerId, exception.getClass().getSimpleName());
        }
    }

    CycleResult runOnce() {
        Instant now = clock.instant();
        int materialized = jobService.materializeDueSubscriptions(now).size();
        int cancelled = jobService.cancelPendingJobsForInactiveSubscriptions(now);
        int executed = 0;
        Optional<JobView> claimed = jobService.claimNext(workerId, clock.instant());
        while (claimed.isPresent() && executed < MAX_JOBS_PER_CYCLE) {
            executeClaimed(claimed.orElseThrow());
            executed++;
            claimed = executed < MAX_JOBS_PER_CYCLE
                    ? jobService.claimNext(workerId, clock.instant())
                    : Optional.empty();
        }
        return new CycleResult(materialized, cancelled, executed);
    }

    private void executeClaimed(JobView job) {
        operationalTelemetry.observeVoid(OperationalEvent.REPORT_JOB_EXECUTION, context -> {
            context.correlate(CorrelationField.JOB_ID, job.id());
            context.correlate(CorrelationField.SUBSCRIPTION_ID, job.subscriptionId());
            context.correlate(CorrelationField.TENANT_ID, job.tenantId());
            context.correlate(CorrelationField.USER_ID, job.creatorUserId());
            context.correlate(CorrelationField.CORRELATION_ID, job.traceId());
            JobView completed = jobService.executeClaimed(job.id(), workerId, clock.instant());
            context.correlate(CorrelationField.REPORT_ID, completed.reportDraftId());
            context.outcome(outcome(completed.status()));
        });
    }

    private OperationalOutcome outcome(ReportGenerationJobStatus status) {
        return switch (status) {
            case SKIPPED -> OperationalOutcome.SKIPPED;
            case CANCELLED -> OperationalOutcome.CANCELLED;
            case RETRY_WAIT, PERMANENT_FAILURE -> OperationalOutcome.ERROR;
            default -> OperationalOutcome.SUCCESS;
        };
    }

    record CycleResult(int materialized, int cancelled, int executed) {
    }
}
