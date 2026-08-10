package com.brand.agentpoc.reporting.application;

public record ReportGenerationRequest(
        String reportType,
        String language,
        String scopeType,
        String scopeId,
        String topic
) {
}
