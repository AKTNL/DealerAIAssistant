package com.brand.agentpoc.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

final class AuthRequestTrace {

    private static final int MAX_TRACE_ID_LENGTH = 128;

    private AuthRequestTrace() {
    }

    static String resolve(HttpServletRequest request) {
        String provided = request.getHeader("X-Request-ID");
        if (provided == null || provided.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = provided.trim();
        return normalized.length() <= MAX_TRACE_ID_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TRACE_ID_LENGTH);
    }
}
