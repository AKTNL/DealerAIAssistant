package com.brand.agentpoc.reporting.domain;

import java.util.Locale;

public enum ReportCollaborationStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public static ReportCollaborationStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported report collaboration status.", exception);
        }
    }

    public boolean terminal() {
        return this == RESOLVED || this == CLOSED;
    }

    public boolean canTransitionTo(ReportCollaborationStatus target) {
        if (target == null || target == this || terminal()) {
            return false;
        }
        return switch (this) {
            case OPEN -> target == IN_PROGRESS || target == CLOSED;
            case IN_PROGRESS -> target == RESOLVED || target == CLOSED;
            case RESOLVED, CLOSED -> false;
        };
    }
}
