package com.brand.agentpoc.auth.application;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IdentityInputPolicy {

    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 64;
    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;
    public static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[\\p{L}\\p{N}._-]+");

    public String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_USERNAME_LENGTH
                || normalized.length() > MAX_USERNAME_LENGTH
                || !USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Username does not meet the identity policy.");
        }
        return normalized;
    }

    public String normalizeDisplayName(String displayName, String fallback) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        if (normalized == null || normalized.isBlank() || normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Display name does not meet the identity policy.");
        }
        return normalized;
    }

    public void validatePassword(String password) {
        if (password == null
                || password.isBlank()
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password does not meet the identity policy.");
        }
    }

    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf(',') >= 0) {
            throw new IllegalArgumentException("Email does not meet the identity policy.");
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(normalized, true);
            if (parsed.length != 1
                    || parsed[0].getPersonal() != null
                    || !normalized.equals(parsed[0].getAddress())) {
                throw new IllegalArgumentException("Email does not meet the identity policy.");
            }
            parsed[0].validate();
            return normalized;
        } catch (AddressException exception) {
            throw new IllegalArgumentException("Email does not meet the identity policy.", exception);
        }
    }
}
