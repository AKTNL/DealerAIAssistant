package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.reporting.application.ReportDeliveryService;
import com.brand.agentpoc.reporting.application.ReportDeliveryService.DeliveryView;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ReportDeliveryRunnerTest {

    @Test
    void cycleClaimsAndExecutesEachDeliveryOnce() {
        Instant now = Instant.parse("2026-08-14T02:00:00Z");
        ReportDeliveryService service = mock(ReportDeliveryService.class);
        DeliveryView delivery = delivery(now);
        AtomicBoolean firstClaim = new AtomicBoolean(true);
        when(service.claimNext("worker-a", now)).thenAnswer(invocation ->
                firstClaim.getAndSet(false) ? Optional.of(delivery) : Optional.empty());
        ReportDeliveryRunner runner = new ReportDeliveryRunner(
                service, Clock.fixed(now, ZoneOffset.UTC), "worker-a");

        int executed = runner.runOnce();

        assertThat(executed).isEqualTo(1);
        verify(service).executeClaimed(21L, "worker-a", now);
    }

    private DeliveryView delivery(Instant now) {
        return new DeliveryView(
                21L, 11L, 9L, 3L, "email", ReportDeliveryStatus.SENDING,
                1, 4, null, null, now, now, 1L);
    }
}
