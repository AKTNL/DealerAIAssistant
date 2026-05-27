package com.brand.agentpoc.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthVerifyResponse(
        boolean success,
        String sessionToken,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt
) {

    public static AuthVerifyResponse success(String sessionToken, Instant expiresAt) {
        return new AuthVerifyResponse(true, sessionToken, expiresAt);
    }

    public static AuthVerifyResponse failure() {
        return new AuthVerifyResponse(false, null, null);
    }
}
