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
        String promptVersion,
        Long tenantId
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
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
    }

    public ReportDraft(
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
        this(id, reportType, title, language, markdown, generatedAt, importBatchId, scope, model, promptVersion,
                com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
