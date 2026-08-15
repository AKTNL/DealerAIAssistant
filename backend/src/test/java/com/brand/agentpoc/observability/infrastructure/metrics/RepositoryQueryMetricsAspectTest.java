package com.brand.agentpoc.observability.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

class RepositoryQueryMetricsAspectTest {

    @Test
    void recordsSuccessfulSlowCallsWithoutRepositoryOrQueryTags() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong time = new AtomicLong();
        RepositoryQueryMetricsAspect aspect = new RepositoryQueryMetricsAspect(
                registry,
                Duration.ofMillis(500),
                () -> time.getAndAdd(Duration.ofMillis(600).toNanos())
        );
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("done");

        assertThat(aspect.recordRepositoryCall(joinPoint)).isEqualTo("done");

        assertThat(registry.get(RepositoryQueryMetricsAspect.QUERY_METRIC)
                .tag("app.outcome", "success").timer().count()).isEqualTo(1L);
        assertThat(registry.get(RepositoryQueryMetricsAspect.SLOW_QUERY_METRIC)
                .tag("app.outcome", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(RepositoryQueryMetricsAspect.QUERY_METRIC).timer().getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("app.component", "app.outcome");
    }

    @Test
    void recordsRepositoryFailuresAndRethrows() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong time = new AtomicLong();
        RepositoryQueryMetricsAspect aspect = new RepositoryQueryMetricsAspect(
                registry,
                Duration.ofMillis(500),
                () -> time.getAndAdd(Duration.ofMillis(100).toNanos())
        );
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> aspect.recordRepositoryCall(joinPoint))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.get(RepositoryQueryMetricsAspect.QUERY_METRIC)
                .tag("app.outcome", "error").timer().count()).isEqualTo(1L);
    }
}
