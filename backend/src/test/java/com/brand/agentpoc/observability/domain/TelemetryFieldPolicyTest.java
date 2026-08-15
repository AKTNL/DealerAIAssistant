package com.brand.agentpoc.observability.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelemetryFieldPolicyTest {

    @Test
    void keepsAllCorrelationIdentifiersOutOfMetricTags() {
        assertThat(CorrelationField.values())
                .allSatisfy(field -> assertThat(TelemetryFieldPolicy.metricTagAllowed(field)).isFalse());
    }

    @Test
    void deniesBuiltInTraceAndSessionFamilyAsCustomAttributes() {
        assertThat(TelemetryFieldPolicy.traceAttributeAllowed(CorrelationField.TRACE_ID)).isFalse();
        assertThat(TelemetryFieldPolicy.traceAttributeAllowed(CorrelationField.SESSION_FAMILY)).isFalse();
        assertThat(TelemetryFieldPolicy.traceAttributeAllowed(CorrelationField.REPORT_ID)).isTrue();
    }

    @Test
    void identifiesSecretAndPayloadFieldNames() {
        assertThat(TelemetryFieldPolicy.forbiddenKey("model.api_key")).isTrue();
        assertThat(TelemetryFieldPolicy.forbiddenKey("user_prompt")).isTrue();
        assertThat(TelemetryFieldPolicy.forbiddenKey("tool_arguments")).isTrue();
        assertThat(TelemetryFieldPolicy.forbiddenKey("app.report.id")).isFalse();
    }

    @Test
    void rejectsBlankAndOverlongCorrelationValues() {
        assertThat(TelemetryFieldPolicy.normalizeCorrelationValue(CorrelationField.JOB_ID, " ")).isNull();
        assertThat(TelemetryFieldPolicy.normalizeCorrelationValue(CorrelationField.JOB_ID, "x".repeat(129))).isNull();
        assertThat(TelemetryFieldPolicy.normalizeCorrelationValue(CorrelationField.JOB_ID, " job-1 "))
                .isEqualTo("job-1");
    }
}
