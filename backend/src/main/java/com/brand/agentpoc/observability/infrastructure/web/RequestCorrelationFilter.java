package com.brand.agentpoc.observability.infrastructure.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private final Tracer tracer;

    public RequestCorrelationFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean invalidSuppliedId = RequestCorrelation.suppliedRequestIdIsInvalid(request);
        String requestId = RequestCorrelation.requestId(request);
        String traceId = currentTraceId(requestId);
        request.setAttribute(RequestCorrelation.TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        response.setHeader(RequestCorrelation.TRACE_ID_HEADER, traceId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            if (invalidSuppliedId) {
                log.atWarn()
                        .addKeyValue("event", "http.request_id.replaced")
                        .addKeyValue("reason", "invalid_request_id")
                        .log("Untrusted request correlation ID was replaced");
            }
            filterChain.doFilter(request, response);
        }
    }

    private String currentTraceId(String fallback) {
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null || span.context().traceId() == null
                || span.context().traceId().isBlank()) {
            return fallback;
        }
        return span.context().traceId();
    }
}
