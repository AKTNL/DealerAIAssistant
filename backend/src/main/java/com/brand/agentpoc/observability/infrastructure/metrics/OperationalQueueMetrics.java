package com.brand.agentpoc.observability.infrastructure.metrics;

import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportDeliveryRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationalQueueMetrics {

    static final String JOB_BACKLOG = "agentpoc.report.job.backlog";
    static final String JOB_RETRY = "agentpoc.report.job.retry";
    static final String JOB_FAILURE = "agentpoc.report.job.failure";
    static final String DELIVERY_BACKLOG = "agentpoc.report.delivery.backlog";
    static final String DELIVERY_RETRY = "agentpoc.report.delivery.retry";
    static final String DELIVERY_FAILURE = "agentpoc.report.delivery.failure";
    static final String REFRESH_AVAILABLE = "agentpoc.operational.queue.refresh.available";

    private static final Logger log = LoggerFactory.getLogger(OperationalQueueMetrics.class);
    private static final List<ReportGenerationJobStatus> JOB_BACKLOG_STATUSES = List.of(
            ReportGenerationJobStatus.READY,
            ReportGenerationJobStatus.RETRY_WAIT
    );
    private static final List<ReportDeliveryStatus> DELIVERY_BACKLOG_STATUSES = List.of(
            ReportDeliveryStatus.READY,
            ReportDeliveryStatus.SENDING,
            ReportDeliveryStatus.RETRY_WAIT
    );
    private static final List<ReportDeliveryStatus> DELIVERY_FAILURE_STATUSES = List.of(
            ReportDeliveryStatus.PERMANENT_FAILURE,
            ReportDeliveryStatus.UNKNOWN
    );

    private final ReportGenerationJobRepository jobRepository;
    private final ReportDeliveryRepository deliveryRepository;
    private volatile QueueSnapshot snapshot = QueueSnapshot.empty();

    public OperationalQueueMetrics(
            ReportGenerationJobRepository jobRepository,
            ReportDeliveryRepository deliveryRepository,
            MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.deliveryRepository = deliveryRepository;
        registerGauges(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.observability.queue-refresh-interval:30s}",
            initialDelayString = "${app.observability.queue-refresh-initial-delay:0s}"
    )
    public void refresh() {
        try {
            snapshot = new QueueSnapshot(
                    true,
                    jobRepository.countByStatusIn(JOB_BACKLOG_STATUSES),
                    jobRepository.countByStatus(ReportGenerationJobStatus.RETRY_WAIT),
                    jobRepository.countByStatus(ReportGenerationJobStatus.PERMANENT_FAILURE),
                    deliveryRepository.countByStatusIn(DELIVERY_BACKLOG_STATUSES),
                    deliveryRepository.countByStatus(ReportDeliveryStatus.RETRY_WAIT),
                    deliveryRepository.countByStatusIn(DELIVERY_FAILURE_STATUSES)
            );
        } catch (RuntimeException exception) {
            snapshot = snapshot.unavailable();
            log.warn("Operational queue metric refresh failed: reason={}",
                    exception.getClass().getSimpleName());
        }
    }

    public QueueSnapshot current() {
        return snapshot;
    }

    private void registerGauges(MeterRegistry meterRegistry) {
        gauge(meterRegistry, JOB_BACKLOG, "success", value -> value.jobBacklog());
        gauge(meterRegistry, JOB_RETRY, "error", value -> value.jobRetry());
        gauge(meterRegistry, JOB_FAILURE, "error", value -> value.jobFailure());
        gauge(meterRegistry, DELIVERY_BACKLOG, "success", value -> value.deliveryBacklog());
        gauge(meterRegistry, DELIVERY_RETRY, "error", value -> value.deliveryRetry());
        gauge(meterRegistry, DELIVERY_FAILURE, "error", value -> value.deliveryFailure());
        gauge(meterRegistry, REFRESH_AVAILABLE, "success", value -> value.available() ? 1L : 0L);
    }

    private void gauge(
            MeterRegistry meterRegistry,
            String name,
            String outcome,
            java.util.function.ToLongFunction<QueueSnapshot> value
    ) {
        Gauge.builder(name, this, metrics -> value.applyAsLong(metrics.current()))
                .tag("app.component", "reporting")
                .tag("app.outcome", outcome)
                .register(meterRegistry);
    }

    public record QueueSnapshot(
            boolean available,
            long jobBacklog,
            long jobRetry,
            long jobFailure,
            long deliveryBacklog,
            long deliveryRetry,
            long deliveryFailure
    ) {
        private static QueueSnapshot empty() {
            return new QueueSnapshot(false, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        private QueueSnapshot unavailable() {
            return new QueueSnapshot(
                    false,
                    jobBacklog,
                    jobRetry,
                    jobFailure,
                    deliveryBacklog,
                    deliveryRetry,
                    deliveryFailure
            );
        }
    }
}
