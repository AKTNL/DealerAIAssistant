package com.brand.agentpoc.observability.infrastructure;

import com.brand.agentpoc.observability.domain.CorrelationField;
import com.brand.agentpoc.observability.domain.OperationalEvent;
import com.brand.agentpoc.observability.domain.OperationalOutcome;
import com.brand.agentpoc.observability.domain.TelemetryFieldPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OperationalTelemetry {

    private static final Logger log = LoggerFactory.getLogger(OperationalTelemetry.class);

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;
    private final boolean completionLoggingEnabled;

    @Autowired
    public OperationalTelemetry(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this(observationRegistry, meterRegistry, true);
    }

    public OperationalTelemetry(ObservationRegistry observationRegistry) {
        this(observationRegistry, null, true);
    }

    private OperationalTelemetry(
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry,
            boolean completionLoggingEnabled
    ) {
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry is required");
        this.meterRegistry = meterRegistry;
        this.completionLoggingEnabled = completionLoggingEnabled;
    }

    public static OperationalTelemetry noop() {
        return new OperationalTelemetry(ObservationRegistry.NOOP, null, false);
    }

    public <T> T observe(OperationalEvent event, Function<EventContext, T> operation) {
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(operation, "operation is required");
        Observation observation = Observation.start(event.observationName(), observationRegistry)
                .lowCardinalityKeyValue("app.component", event.component());
        EventContext context = new EventContext(observation);
        long startedAt = System.nanoTime();
        try (Observation.Scope ignored = observation.openScope()) {
            return operation.apply(context);
        } catch (RuntimeException | Error exception) {
            context.outcome(OperationalOutcome.ERROR);
            observation.error(exception);
            throw exception;
        } finally {
            context.applyOutcome();
            observation.stop();
            if (completionLoggingEnabled) {
                logCompletion(event, context.outcome, System.nanoTime() - startedAt);
            }
        }
    }

    public void observe(OperationalEvent event, Runnable operation) {
        observe(event, context -> {
            operation.run();
            return null;
        });
    }

    public void observeVoid(OperationalEvent event, Consumer<EventContext> operation) {
        Objects.requireNonNull(operation, "operation is required");
        observe(event, context -> {
            operation.accept(context);
            return null;
        });
    }

    public void recordDuration(
            OperationalEvent event,
            OperationalOutcome outcome,
            long duration,
            TimeUnit unit
    ) {
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(unit, "unit is required");
        if (meterRegistry == null || duration < 0L) {
            return;
        }
        Timer.builder(event.observationName())
                .tag("app.component", event.component())
                .tag("app.outcome", outcome.value())
                .register(meterRegistry)
                .record(duration, unit);
    }

    private void logCompletion(OperationalEvent event, OperationalOutcome outcome, long elapsedNanos) {
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (outcome == OperationalOutcome.ERROR) {
            log.atWarn()
                    .addKeyValue("event", event.eventName())
                    .addKeyValue("component", event.component())
                    .addKeyValue("status", outcome.value())
                    .addKeyValue("duration_ms", durationMillis)
                    .log("Operational event completed");
            return;
        }
        log.atInfo()
                .addKeyValue("event", event.eventName())
                .addKeyValue("component", event.component())
                .addKeyValue("status", outcome.value())
                .addKeyValue("duration_ms", durationMillis)
                .log("Operational event completed");
    }

    public static final class EventContext {
        private final Observation observation;
        private OperationalOutcome outcome = OperationalOutcome.SUCCESS;

        private EventContext(Observation observation) {
            this.observation = observation;
        }

        public EventContext correlate(CorrelationField field, Object value) {
            String normalized = TelemetryFieldPolicy.normalizeCorrelationValue(field, value);
            if (normalized != null) {
                observation.highCardinalityKeyValue(field.attributeKey(), normalized);
            }
            return this;
        }

        public EventContext outcome(OperationalOutcome value) {
            outcome = Objects.requireNonNull(value, "outcome is required");
            return this;
        }

        private void applyOutcome() {
            observation.lowCardinalityKeyValue("app.outcome", outcome.value());
        }
    }
}
