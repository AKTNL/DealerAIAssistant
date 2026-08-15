package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.observability.domain.CorrelationField;
import com.brand.agentpoc.observability.domain.OperationalEvent;
import com.brand.agentpoc.observability.domain.OperationalOutcome;
import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
import com.brand.agentpoc.reporting.application.ReportCollaborationNotificationService;
import com.brand.agentpoc.reporting.application.ReportCollaborationNotificationService.NotificationView;
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
public class ReportCollaborationNotificationRunner {

    static final int MAX_NOTIFICATIONS_PER_CYCLE = 50;
    private static final Logger log = LoggerFactory.getLogger(ReportCollaborationNotificationRunner.class);

    private final ReportCollaborationNotificationService notificationService;
    private final Clock clock;
    private final String workerId;
    private final OperationalTelemetry operationalTelemetry;

    public ReportCollaborationNotificationRunner(
            ReportCollaborationNotificationService notificationService,
            Clock clock
    ) {
        this(notificationService, clock, "collaboration-notification-worker-" + UUID.randomUUID(),
                OperationalTelemetry.noop());
    }

    @Autowired
    public ReportCollaborationNotificationRunner(
            ReportCollaborationNotificationService notificationService,
            Clock clock,
            OperationalTelemetry operationalTelemetry
    ) {
        this(notificationService, clock, "collaboration-notification-worker-" + UUID.randomUUID(),
                operationalTelemetry);
    }

    ReportCollaborationNotificationRunner(
            ReportCollaborationNotificationService notificationService,
            Clock clock,
            String workerId
    ) {
        this(notificationService, clock, workerId, OperationalTelemetry.noop());
    }

    ReportCollaborationNotificationRunner(
            ReportCollaborationNotificationService notificationService,
            Clock clock,
            String workerId,
            OperationalTelemetry operationalTelemetry
    ) {
        this.notificationService = notificationService;
        this.clock = clock;
        this.workerId = workerId;
        this.operationalTelemetry = operationalTelemetry;
    }

    @Scheduled(fixedDelayString = "${app.reporting.jobs.poll-interval:30s}")
    public void runScheduledCycle() {
        try {
            int executed = runOnce();
            if (executed > 0) {
                log.info("Report collaboration notification cycle completed: workerId={}, executed={}",
                        workerId, executed);
            }
        } catch (RuntimeException exception) {
            log.warn("Report collaboration notification cycle failed: workerId={}, reason={}",
                    workerId, exception.getClass().getSimpleName());
        }
    }

    int runOnce() {
        int executed = 0;
        Optional<NotificationView> claimed = notificationService.claimNext(workerId, clock.instant());
        while (claimed.isPresent() && executed < MAX_NOTIFICATIONS_PER_CYCLE) {
            executeClaimed(claimed.orElseThrow());
            executed++;
            claimed = executed < MAX_NOTIFICATIONS_PER_CYCLE
                    ? notificationService.claimNext(workerId, clock.instant())
                    : Optional.empty();
        }
        return executed;
    }

    private void executeClaimed(NotificationView notification) {
        operationalTelemetry.observeVoid(OperationalEvent.REPORT_COLLABORATION_NOTIFICATION, context -> {
            context.correlate(CorrelationField.NOTIFICATION_ID, notification.id());
            context.correlate(CorrelationField.EVENT_ID, notification.eventId());
            NotificationView completed = notificationService.executeClaimed(
                    notification.id(), workerId, clock.instant());
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
