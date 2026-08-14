package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.reporting.application.ReportDeliveryService;
import com.brand.agentpoc.reporting.application.ReportDeliveryService.DeliveryView;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.reporting.jobs", name = "enabled", havingValue = "true")
public class ReportDeliveryRunner {

    static final int MAX_DELIVERIES_PER_CYCLE = 50;
    private static final Logger log = LoggerFactory.getLogger(ReportDeliveryRunner.class);

    private final ReportDeliveryService deliveryService;
    private final Clock clock;
    private final String workerId;

    public ReportDeliveryRunner(ReportDeliveryService deliveryService, Clock clock) {
        this(deliveryService, clock, "delivery-worker-" + UUID.randomUUID());
    }

    ReportDeliveryRunner(ReportDeliveryService deliveryService, Clock clock, String workerId) {
        this.deliveryService = deliveryService;
        this.clock = clock;
        this.workerId = workerId;
    }

    @Scheduled(fixedDelayString = "${app.reporting.jobs.poll-interval:30s}")
    public void runScheduledCycle() {
        try {
            int executed = runOnce();
            if (executed > 0) {
                log.info("Report delivery cycle completed: workerId={}, executed={}", workerId, executed);
            }
        } catch (RuntimeException exception) {
            log.warn("Report delivery cycle failed: workerId={}, reason={}",
                    workerId, exception.getClass().getSimpleName());
        }
    }

    int runOnce() {
        int executed = 0;
        Optional<DeliveryView> claimed = deliveryService.claimNext(workerId, clock.instant());
        while (claimed.isPresent() && executed < MAX_DELIVERIES_PER_CYCLE) {
            deliveryService.executeClaimed(claimed.orElseThrow().id(), workerId, clock.instant());
            executed++;
            claimed = executed < MAX_DELIVERIES_PER_CYCLE
                    ? deliveryService.claimNext(workerId, clock.instant())
                    : Optional.empty();
        }
        return executed;
    }
}
