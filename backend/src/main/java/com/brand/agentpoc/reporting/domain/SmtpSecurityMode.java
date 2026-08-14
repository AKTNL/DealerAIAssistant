package com.brand.agentpoc.reporting.domain;

import java.util.Locale;

public enum SmtpSecurityMode {
    STARTTLS,
    SMTPS;

    public static SmtpSecurityMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SMTP security mode is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("SMTP security mode is invalid.", exception);
        }
    }
}
