package com.brand.agentpoc.observability.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.observability.infrastructure.health.OperationalQueueHealthIndicator;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportDeliveryRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class OperationalQueueMetricsTest {

    @Test
    void refreshesLowCardinalityQueueGauges() {
        ReportGenerationJobRepository jobs = mock(ReportGenerationJobRepository.class);
        ReportDeliveryRepository deliveries = mock(ReportDeliveryRepository.class);
        when(jobs.countByStatusIn(anyList())).thenReturn(12L);
        when(jobs.countByStatus(ReportGenerationJobStatus.RETRY_WAIT)).thenReturn(3L);
        when(jobs.countByStatus(ReportGenerationJobStatus.PERMANENT_FAILURE)).thenReturn(1L);
        when(deliveries.countByStatusIn(anyList())).thenReturn(7L, 2L);
        when(deliveries.countByStatus(ReportDeliveryStatus.RETRY_WAIT)).thenReturn(4L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalQueueMetrics metrics = new OperationalQueueMetrics(jobs, deliveries, registry);

        metrics.refresh();

        assertThat(registry.get(OperationalQueueMetrics.JOB_BACKLOG).gauge().value()).isEqualTo(12.0);
        assertThat(registry.get(OperationalQueueMetrics.DELIVERY_FAILURE).gauge().value()).isEqualTo(2.0);
        assertThat(registry.get(OperationalQueueMetrics.REFRESH_AVAILABLE).gauge().value()).isEqualTo(1.0);
        assertThat(registry.get(OperationalQueueMetrics.JOB_BACKLOG).gauge().getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("app.component", "app.outcome");
    }

    @Test
    void queueFailuresDegradeDiagnosticsWithoutBecomingDown() {
        ReportGenerationJobRepository jobs = mock(ReportGenerationJobRepository.class);
        ReportDeliveryRepository deliveries = mock(ReportDeliveryRepository.class);
        when(jobs.countByStatusIn(anyList())).thenReturn(0L);
        when(deliveries.countByStatusIn(anyList())).thenReturn(0L, 1L);
        OperationalQueueMetrics metrics = new OperationalQueueMetrics(
                jobs,
                deliveries,
                new SimpleMeterRegistry()
        );
        metrics.refresh();

        var health = new OperationalQueueHealthIndicator(metrics, new AppProperties()).health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("reason", "permanent_failure");
    }

    @Test
    void refreshFailurePreservesCountsAndDegradesDiagnostics() {
        ReportGenerationJobRepository jobs = mock(ReportGenerationJobRepository.class);
        ReportDeliveryRepository deliveries = mock(ReportDeliveryRepository.class);
        when(jobs.countByStatusIn(anyList())).thenReturn(8L);
        when(jobs.countByStatus(ReportGenerationJobStatus.RETRY_WAIT)).thenReturn(2L);
        when(deliveries.countByStatusIn(anyList())).thenReturn(4L, 0L);
        OperationalQueueMetrics metrics = new OperationalQueueMetrics(jobs, deliveries, new SimpleMeterRegistry());
        metrics.refresh();
        reset(jobs);
        when(jobs.countByStatusIn(anyList())).thenThrow(new IllegalStateException("database unavailable"));

        metrics.refresh();

        assertThat(metrics.current().available()).isFalse();
        assertThat(metrics.current().jobBacklog()).isEqualTo(8L);
        assertThat(metrics.current().deliveryBacklog()).isEqualTo(4L);
        var health = new OperationalQueueHealthIndicator(metrics, new AppProperties()).health();
        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("reason", "refresh_unavailable");
    }
}
