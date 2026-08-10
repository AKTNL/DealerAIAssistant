package com.brand.agentpoc.reporting.domain;

import java.time.Instant;
import java.util.Objects;

public record ReportDraft(
        String id,
        ReportType reportType,
        String title,
        String language,
        String markdown,
        Instant generatedAt,
        String importBatchId,
        ReportScope scope,
        String model,
        String promptVersion
) {

    public ReportDraft {
        id = required(id, "id");
        reportType = Objects.requireNonNull(reportType, "reportType is required.");
        title = required(title, "title");
        language = required(language, "language");
        markdown = required(markdown, "markdown");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt is required.");
        importBatchId = required(importBatchId, "importBatchId");
        scope = scope == null ? ReportScope.global() : scope;
        model = required(model, "model");
        promptVersion = required(promptVersion, "promptVersion");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
