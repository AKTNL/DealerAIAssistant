package com.brand.agentpoc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReportDraftRequest(
        @NotBlank String reportType,
        @NotBlank String language,
        String scopeType,
        String scopeId,
        String topic
) {
}
