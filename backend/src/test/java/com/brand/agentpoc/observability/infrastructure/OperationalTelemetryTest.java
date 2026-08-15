package com.brand.agentpoc.observability.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.observability.domain.CorrelationField;
import com.brand.agentpoc.observability.domain.OperationalEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OperationalTelemetryTest {

    @Test
    void recordsLowCardinalityOutcomeWithoutIdentifierTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        AtomicReference<Observation.Context> stoppedContext = new AtomicReference<>();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry))
                .observationHandler(new ObservationHandler<>() {
                    @Override
                    public void onStop(Observation.Context context) {
                        stoppedContext.set(context);
                    }

                    @Override
                    public boolean supportsContext(Observation.Context context) {
                        return true;
                    }
                });
        OperationalTelemetry telemetry = new OperationalTelemetry(observationRegistry);

        String value = telemetry.observe(OperationalEvent.REPORT_GENERATION, context -> {
            context.correlate(CorrelationField.REPORT_ID, "report-1");
            context.correlate(CorrelationField.TENANT_ID, 7L);
            return "done";
        });

        assertThat(value).isEqualTo("done");
        assertThat(meterRegistry.get("agentpoc.report.generate").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("agentpoc.report.generate").timer().getId().getTags())
                .extracting(tag -> tag.getKey())
                .contains("app.component", "app.outcome")
                .doesNotContain("app.report.id", "app.tenant.id");
        assertThat(stoppedContext.get().getHighCardinalityKeyValues())
                .extracting(keyValue -> keyValue.getKey() + "=" + keyValue.getValue())
                .contains("app.report.id=report-1", "app.tenant.id=7");
    }

    @Test
    void recordsAnErrorOutcomeAndRethrows() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        OperationalTelemetry telemetry = new OperationalTelemetry(observationRegistry);

        assertThatThrownBy(() -> telemetry.observe(OperationalEvent.DATA_IMPORT, context -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.get("agentpoc.data.import").tag("app.outcome", "error").timer().count())
                .isEqualTo(1);
    }
}
