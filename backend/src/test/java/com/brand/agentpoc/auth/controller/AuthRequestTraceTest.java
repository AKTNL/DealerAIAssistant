package com.brand.agentpoc.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthRequestTraceTest {

    @Test
    void preservesTrimmedTraceIdsWithinTheAuditColumnLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "  request-123  ");

        assertThat(AuthRequestTrace.resolve(request)).isEqualTo("request-123");
    }

    @Test
    void replacesUntrustedTraceIdsInsteadOfTruncatingThem() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "x".repeat(200));

        assertThat(AuthRequestTrace.resolve(request)).matches("[0-9a-f-]{36}");
    }

    @Test
    void generatesATraceIdWhenTheHeaderIsMissing() {
        assertThat(AuthRequestTrace.resolve(new MockHttpServletRequest())).isNotBlank();
    }
}
