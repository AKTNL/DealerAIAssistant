package com.brand.agentpoc.auth.controller;

import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;

final class AuthRequestTrace {

    private AuthRequestTrace() {
    }

    static String resolve(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }
}
