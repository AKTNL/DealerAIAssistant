package com.brand.agentpoc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ModelConfigRequest(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String model
) {
}
