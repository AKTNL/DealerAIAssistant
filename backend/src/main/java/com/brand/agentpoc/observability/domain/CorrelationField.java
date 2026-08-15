package com.brand.agentpoc.observability.domain;

public enum CorrelationField {
    REQUEST_ID("app.request.id", 128, true),
    TRACE_ID("trace_id", 32, false),
    JOB_ID("app.job.id", 128, true),
    SUBSCRIPTION_ID("app.subscription.id", 128, true),
    TENANT_ID("app.tenant.id", 64, true),
    USER_ID("app.user.id", 64, true),
    SESSION_FAMILY("app.session.family", 0, false),
    BATCH_ID("app.batch.id", 128, true),
    REPORT_ID("app.report.id", 128, true),
    CORRELATION_ID("app.correlation.id", 128, true),
    DELIVERY_ID("app.delivery.id", 128, true),
    EVENT_ID("app.event.id", 128, true),
    NOTIFICATION_ID("app.notification.id", 128, true);

    private final String attributeKey;
    private final int maxLength;
    private final boolean traceAttributeAllowed;

    CorrelationField(String attributeKey, int maxLength, boolean traceAttributeAllowed) {
        this.attributeKey = attributeKey;
        this.maxLength = maxLength;
        this.traceAttributeAllowed = traceAttributeAllowed;
    }

    public String attributeKey() {
        return attributeKey;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean traceAttributeAllowed() {
        return traceAttributeAllowed;
    }
}
