package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthRateLimitServiceTest {

    private final Instant now = Instant.parse("2026-05-21T08:00:00Z");

    @Test
    void limitsClientAfterRepeatedFailuresUntilCooldownExpires() {
        MutableClock clock = new MutableClock(now);
        AuthRateLimitService service = new AuthRateLimitService(clock);

        for (int i = 0; i < 4; i++) {
            service.recordFailure("203.0.113.10");
            assertThat(service.isLimited("203.0.113.10")).isFalse();
        }

        service.recordFailure("203.0.113.10");

        assertThat(service.isLimited("203.0.113.10")).isTrue();
        assertThat(service.retryAfterSeconds("203.0.113.10")).isEqualTo(300);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(service.isLimited("203.0.113.10")).isFalse();
        assertThat(service.retryAfterSeconds("203.0.113.10")).isZero();
    }

    @Test
    void successfulAuthenticationClearsPreviousFailures() {
        MutableClock clock = new MutableClock(now);
        AuthRateLimitService service = new AuthRateLimitService(clock);

        service.recordFailure("203.0.113.10");
        service.recordFailure("203.0.113.10");
        service.recordSuccess("203.0.113.10");

        service.recordFailure("203.0.113.10");

        assertThat(service.isLimited("203.0.113.10")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
