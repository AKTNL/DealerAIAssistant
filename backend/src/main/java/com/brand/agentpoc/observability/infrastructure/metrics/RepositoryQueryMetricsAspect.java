package com.brand.agentpoc.observability.infrastructure.metrics;

import com.brand.agentpoc.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RepositoryQueryMetricsAspect {

    static final String QUERY_METRIC = "agentpoc.database.query";
    static final String SLOW_QUERY_METRIC = "agentpoc.database.slow.query";

    private final long slowQueryThresholdNanos;
    private final LongSupplier nanoTime;
    private final Timer successTimer;
    private final Timer errorTimer;
    private final Counter slowSuccessCounter;
    private final Counter slowErrorCounter;

    @Autowired
    public RepositoryQueryMetricsAspect(MeterRegistry meterRegistry, AppProperties appProperties) {
        this(meterRegistry, appProperties.getObservability().getSlowQueryThreshold(), System::nanoTime);
    }

    RepositoryQueryMetricsAspect(MeterRegistry meterRegistry, Duration slowQueryThreshold, LongSupplier nanoTime) {
        Objects.requireNonNull(meterRegistry, "meterRegistry is required");
        Duration threshold = Objects.requireNonNull(slowQueryThreshold, "slowQueryThreshold is required");
        if (threshold.isNegative()) {
            throw new IllegalArgumentException("slowQueryThreshold must not be negative");
        }
        this.slowQueryThresholdNanos = threshold.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime is required");
        successTimer = timer(meterRegistry, "success");
        errorTimer = timer(meterRegistry, "error");
        slowSuccessCounter = counter(meterRegistry, "success");
        slowErrorCounter = counter(meterRegistry, "error");
    }

    @Around("this(org.springframework.data.repository.Repository)")
    public Object recordRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = nanoTime.getAsLong();
        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            long elapsed = Math.max(0L, nanoTime.getAsLong() - startedAt);
            Timer timer = success ? successTimer : errorTimer;
            timer.record(elapsed, TimeUnit.NANOSECONDS);
            if (elapsed >= slowQueryThresholdNanos) {
                Counter counter = success ? slowSuccessCounter : slowErrorCounter;
                counter.increment();
            }
        }
    }

    private Timer timer(MeterRegistry meterRegistry, String outcome) {
        return Timer.builder(QUERY_METRIC)
                .tag("app.component", "database")
                .tag("app.outcome", outcome)
                .register(meterRegistry);
    }

    private Counter counter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(SLOW_QUERY_METRIC)
                .tag("app.component", "database")
                .tag("app.outcome", outcome)
                .register(meterRegistry);
    }
}
