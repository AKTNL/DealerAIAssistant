package com.brand.agentpoc.observability.infrastructure.health;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.observability.infrastructure.metrics.OperationalQueueMetrics;
import com.brand.agentpoc.observability.infrastructure.metrics.OperationalQueueMetrics.QueueSnapshot;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OperationalQueueHealthIndicator implements HealthIndicator {

    private static final String DEGRADED = "DEGRADED";

    private final OperationalQueueMetrics metrics;
    private final AppProperties.Observability properties;

    public OperationalQueueHealthIndicator(OperationalQueueMetrics metrics, AppProperties appProperties) {
        this.metrics = metrics;
        this.properties = appProperties.getObservability();
    }

    @Override
    public Health health() {
        QueueSnapshot snapshot = metrics.current();
        Health.Builder builder = details(snapshot);
        if (!snapshot.available()) {
            return builder.status(DEGRADED).withDetail("reason", "refresh_unavailable").build();
        }
        if (snapshot.jobBacklog() >= properties.getJobBacklogDegradedThreshold()) {
            return builder.status(DEGRADED).withDetail("reason", "job_backlog").build();
        }
        if (snapshot.deliveryBacklog() >= properties.getDeliveryBacklogDegradedThreshold()) {
            return builder.status(DEGRADED).withDetail("reason", "delivery_backlog").build();
        }
        long failureThreshold = properties.getPermanentFailureDegradedThreshold();
        if (snapshot.jobFailure() >= failureThreshold || snapshot.deliveryFailure() >= failureThreshold) {
            return builder.status(DEGRADED).withDetail("reason", "permanent_failure").build();
        }
        return builder.up().build();
    }

    private Health.Builder details(QueueSnapshot snapshot) {
        return Health.unknown()
                .withDetail("jobBacklog", snapshot.jobBacklog())
                .withDetail("jobRetry", snapshot.jobRetry())
                .withDetail("jobFailure", snapshot.jobFailure())
                .withDetail("deliveryBacklog", snapshot.deliveryBacklog())
                .withDetail("deliveryRetry", snapshot.deliveryRetry())
                .withDetail("deliveryFailure", snapshot.deliveryFailure());
    }
}
