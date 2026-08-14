package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.reporting.application.ReportGenerationJobService;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService.JobView;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ReportGenerationJobRunner(ReportGenerationJobService jobService, Clock clock) {
        this(jobService, clock, "report-worker-" + UUID.randomUUID());
    }

    ReportGenerationJobRunner(ReportGenerationJobService jobService, Clock clock, String workerId) {
        this.jobService = jobService;
        this.clock = clock;
        this.workerId = workerId;
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
            jobService.executeClaimed(claimed.orElseThrow().id(), workerId, clock.instant());
            executed++;
            claimed = executed < MAX_JOBS_PER_CYCLE
                    ? jobService.claimNext(workerId, clock.instant())
                    : Optional.empty();
        }
        return new CycleResult(materialized, cancelled, executed);
    }

    record CycleResult(int materialized, int cancelled, int executed) {
    }
}
