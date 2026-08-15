package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.observability.domain.CorrelationField;
import com.brand.agentpoc.observability.domain.OperationalEvent;
import com.brand.agentpoc.observability.domain.OperationalOutcome;
import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
import com.brand.agentpoc.reporting.application.ReportDeliveryService;
import com.brand.agentpoc.reporting.application.ReportDeliveryService.DeliveryView;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import java.time.Clock;
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
public class ReportDeliveryRunner {

    static final int MAX_DELIVERIES_PER_CYCLE = 50;
    private static final Logger log = LoggerFactory.getLogger(ReportDeliveryRunner.class);

    private final ReportDeliveryService deliveryService;
    private final Clock clock;
    private final String workerId;
    private final OperationalTelemetry operationalTelemetry;

    public ReportDeliveryRunner(ReportDeliveryService deliveryService, Clock clock) {
        this(deliveryService, clock, "delivery-worker-" + UUID.randomUUID(), OperationalTelemetry.noop());
    }

    @Autowired
    public ReportDeliveryRunner(
            ReportDeliveryService deliveryService,
            Clock clock,
            OperationalTelemetry operationalTelemetry
    ) {
        this(deliveryService, clock, "delivery-worker-" + UUID.randomUUID(), operationalTelemetry);
    }

    ReportDeliveryRunner(ReportDeliveryService deliveryService, Clock clock, String workerId) {
        this(deliveryService, clock, workerId, OperationalTelemetry.noop());
    }

    ReportDeliveryRunner(
            ReportDeliveryService deliveryService,
            Clock clock,
            String workerId,
            OperationalTelemetry operationalTelemetry
    ) {
        this.deliveryService = deliveryService;
        this.clock = clock;
        this.workerId = workerId;
        this.operationalTelemetry = operationalTelemetry;
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
            executeClaimed(claimed.orElseThrow());
            executed++;
            claimed = executed < MAX_DELIVERIES_PER_CYCLE
                    ? deliveryService.claimNext(workerId, clock.instant())
                    : Optional.empty();
        }
        return executed;
    }

    private void executeClaimed(DeliveryView delivery) {
        operationalTelemetry.observeVoid(OperationalEvent.REPORT_DELIVERY, context -> {
            context.correlate(CorrelationField.DELIVERY_ID, delivery.id());
            context.correlate(CorrelationField.JOB_ID, delivery.reportJobId());
            DeliveryView completed = deliveryService.executeClaimed(delivery.id(), workerId, clock.instant());
            context.outcome(outcome(completed.status()));
        });
    }

    private OperationalOutcome outcome(ReportDeliveryStatus status) {
        return switch (status) {
            case CANCELLED -> OperationalOutcome.CANCELLED;
            case RETRY_WAIT, PERMANENT_FAILURE, UNKNOWN -> OperationalOutcome.ERROR;
            default -> OperationalOutcome.SUCCESS;
        };
    }
}
