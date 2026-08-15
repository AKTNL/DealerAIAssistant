package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.observability.infrastructure.OperationalTelemetry;
import com.brand.agentpoc.reporting.application.ReportCollaborationNotificationService;
import com.brand.agentpoc.reporting.application.ReportCollaborationNotificationService.NotificationView;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ReportCollaborationNotificationRunnerTest {

    @Test
    void cycleRecordsNotificationTelemetryWithoutIdentifierTags() {
        Instant now = Instant.parse("2026-08-14T02:30:00Z");
        ReportCollaborationNotificationService service = mock(ReportCollaborationNotificationService.class);
        NotificationView notification = notification(now);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(service.claimNext("worker-a", now)).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(notification) : Optional.empty());
        when(service.executeClaimed(31L, "worker-a", now)).thenReturn(notification);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        ReportCollaborationNotificationRunner runner = new ReportCollaborationNotificationRunner(
                service,
                Clock.fixed(now, ZoneOffset.UTC),
                "worker-a",
                new OperationalTelemetry(observationRegistry)
        );

        int executed = runner.runOnce();

        assertThat(executed).isEqualTo(1);
        verify(service).executeClaimed(31L, "worker-a", now);
        assertThat(meterRegistry.get("agentpoc.report.collaboration.notification")
                .tag("app.outcome", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("agentpoc.report.collaboration.notification").timer().getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain("app.notification.id", "app.event.id");
    }

    private NotificationView notification(Instant now) {
        return new NotificationView(
                31L, 41L, 3L, ReportDeliveryStatus.SENDING,
                1, 4, null, null, now, now, 1L);
    }
}
