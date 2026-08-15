package com.brand.agentpoc.observability.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestCorrelation {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String REQUEST_ID_ATTRIBUTE = RequestCorrelation.class.getName() + ".requestId";
    public static final String TRACE_ID_ATTRIBUTE = RequestCorrelation.class.getName() + ".traceId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private RequestCorrelation() {
    }

    public static String requestId(HttpServletRequest request) {
        Object resolved = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (resolved instanceof String value && valid(value)) {
            return value;
        }
        String supplied = normalize(request.getHeader(REQUEST_ID_HEADER));
        String requestId = valid(supplied) ? supplied : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }

    public static String traceId(HttpServletRequest request) {
        Object resolved = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (resolved instanceof String value && valid(value)) {
            return value;
        }
        return requestId(request);
    }

    public static boolean suppliedRequestIdIsInvalid(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        return supplied != null && !supplied.isBlank() && !valid(normalize(supplied));
    }

    static boolean valid(String value) {
        return value != null
                && value.length() <= MAX_CORRELATION_ID_LENGTH
                && SAFE_CORRELATION_ID.matcher(value).matches();
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
