package com.brand.agentpoc.observability.domain;

public enum OperationalOutcome {
    SUCCESS("success"),
    ERROR("error"),
    FALLBACK("fallback"),
    SKIPPED("skipped"),
    CANCELLED("cancelled"),
    REJECTED("rejected");

    private final String value;

    OperationalOutcome(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
