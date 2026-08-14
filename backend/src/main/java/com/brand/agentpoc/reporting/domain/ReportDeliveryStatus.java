package com.brand.agentpoc.reporting.domain;

public enum ReportDeliveryStatus {
    READY,
    SENDING,
    RETRY_WAIT,
    SUCCEEDED,
    PERMANENT_FAILURE,
    UNKNOWN,
    CANCELLED
}
