package com.brand.agentpoc.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportDeliveryEntityTest {

    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    @Test
    void expiredSendingLeaseBecomesUnknownAndRequiresForceReplay() {
        ReportDeliveryEntity delivery = delivery();
        delivery.claim("worker-a", NOW, NOW.plusSeconds(30));

        delivery.recoverExpiredLease(NOW.plusSeconds(31));

        assertThat(delivery.getStatus()).isEqualTo(ReportDeliveryStatus.UNKNOWN);
        assertThat(delivery.getErrorCode()).isEqualTo("LEASE_EXPIRED_OUTCOME_UNKNOWN");
        assertThatThrownBy(() -> delivery.manualRetry(NOW.plusSeconds(32)))
                .isInstanceOf(IllegalStateException.class);

        delivery.forceReplay(NOW.plusSeconds(33));

        assertThat(delivery.getStatus()).isEqualTo(ReportDeliveryStatus.READY);
        assertThat(delivery.getAttempt()).isZero();
    }

    @Test
    void boundedRetryBecomesPermanentWhenAttemptsAreExhausted() {
        ReportDeliveryEntity delivery = delivery();
        for (int attempt = 1; attempt <= ReportDeliveryEntity.DEFAULT_MAX_ATTEMPTS; attempt++) {
            Instant attemptNow = NOW.plusSeconds(attempt * 120L);
            delivery.claim("worker-a", attemptNow, attemptNow.plusSeconds(30));
            delivery.markRetry("SMTP_TEMPORARY_REJECTION", attemptNow.plusSeconds(60),
                    attemptNow.plusSeconds(2));
        }

        assertThat(delivery.getStatus()).isEqualTo(ReportDeliveryStatus.PERMANENT_FAILURE);
        assertThat(delivery.getErrorCode()).isEqualTo("RETRY_EXHAUSTED");
        assertThat(delivery.getAttempt()).isEqualTo(ReportDeliveryEntity.DEFAULT_MAX_ATTEMPTS);
    }

    private ReportDeliveryEntity delivery() {
        return new ReportDeliveryEntity(
                11L, 9L, 7L, 2L, "draft-1", 3L,
                "email", "report-delivery:11:email:3", NOW);
    }
}
