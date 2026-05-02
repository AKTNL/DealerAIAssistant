package com.brand.agentpoc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthVerifyRequest(@NotBlank String key) {
}

