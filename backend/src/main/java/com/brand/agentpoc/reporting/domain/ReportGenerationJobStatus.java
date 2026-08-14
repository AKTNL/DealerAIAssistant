package com.brand.agentpoc.reporting.domain;

public enum ReportGenerationJobStatus {
    READY,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    PERMANENT_FAILURE,
    SKIPPED,
    CANCELLED
}
