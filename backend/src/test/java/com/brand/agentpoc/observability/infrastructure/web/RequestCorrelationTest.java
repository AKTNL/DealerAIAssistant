package com.brand.agentpoc.observability.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestCorrelationTest {

    @Test
    void preservesAValidTrimmedRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.REQUEST_ID_HEADER, "  request-123  ");

        assertThat(RequestCorrelation.requestId(request)).isEqualTo("request-123");
    }

    @Test
    void replacesOverlongAndMalformedRequestIds() {
        MockHttpServletRequest overlong = new MockHttpServletRequest();
        overlong.addHeader(RequestCorrelation.REQUEST_ID_HEADER, "x".repeat(129));
        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.addHeader(RequestCorrelation.REQUEST_ID_HEADER, "request id with spaces");

        assertThat(RequestCorrelation.requestId(overlong)).matches("[0-9a-f-]{36}");
        assertThat(RequestCorrelation.requestId(malformed)).matches("[0-9a-f-]{36}");
    }

    @Test
    void resolvesTheSameGeneratedIdForRepeatedReads() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(RequestCorrelation.requestId(request)).isEqualTo(RequestCorrelation.requestId(request));
        assertThat(RequestCorrelation.traceId(request)).isEqualTo(RequestCorrelation.requestId(request));
    }
}
