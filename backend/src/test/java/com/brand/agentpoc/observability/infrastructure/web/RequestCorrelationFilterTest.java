package com.brand.agentpoc.observability.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    @Test
    void returnsRequestAndTraceHeadersAndCleansUpMdc() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        RequestCorrelationFilter filter = new RequestCorrelationFilter(tracer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelation.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(response.getHeader(RequestCorrelation.TRACE_ID_HEADER))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(RequestCorrelation.traceId(request)).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesAnInvalidUntrustedRequestId() throws Exception {
        Tracer tracer = mock(Tracer.class);
        RequestCorrelationFilter filter = new RequestCorrelationFilter(tracer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.REQUEST_ID_HEADER, "invalid request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelation.REQUEST_ID_HEADER)).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(RequestCorrelation.TRACE_ID_HEADER))
                .isEqualTo(response.getHeader(RequestCorrelation.REQUEST_ID_HEADER));
    }
}
