package com.brand.agentpoc.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigurationValidator {

    private static final int ENCRYPTION_KEY_BYTES = 32;
    private static final int MIGRATED_EMBEDDING_DIMENSIONS = 1536;
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";

    private final AppProperties appProperties;
    private final Environment environment;

    public ProductionConfigurationValidator(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        List<String> violations = new ArrayList<>();
        validateDatabase(violations);
        validateMigrationSettings(violations);
        validateApplicationSettings(violations);
        validateEncryptionKeys(violations);
        validateCorsOrigins(violations);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration validation failed: " + String.join(", ", violations));
        }
    }

    private void validateDatabase(List<String> violations) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:")) {
            violations.add("spring.datasource.url must use PostgreSQL");
        }
        requireText(violations, "spring.datasource.username");
        requireText(violations, "spring.datasource.password");
        if (!POSTGRESQL_DRIVER.equals(environment.getProperty("spring.datasource.driver-class-name"))) {
            violations.add("spring.datasource.driver-class-name must use the PostgreSQL driver");
        }
    }

    private void validateMigrationSettings(List<String> violations) {
        requireTrue(violations, "spring.flyway.enabled");
        requireTrue(violations, "spring.flyway.validate-on-migrate");
        requireTrue(violations, "spring.flyway.clean-disabled");
        if (!"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto", ""))) {
            violations.add("spring.jpa.hibernate.ddl-auto must be validate");
        }
    }

    private void validateApplicationSettings(List<String> violations) {
        if (!appProperties.getAuth().isCookieSecure()) {
            violations.add("app.auth.cookie-secure must be true");
        }
        if (!appProperties.getAuth().getBootstrap().isRequired()) {
            violations.add("app.auth.bootstrap.required must be true");
        }
        if (appProperties.getExcel().isFallbackEnabled()) {
            violations.add("app.excel.fallback-enabled must be false");
        }
        if (!"pgvector".equalsIgnoreCase(appProperties.getKnowledge().getVectorStore())) {
            violations.add("app.knowledge.vector-store must be pgvector");
        }
        if (appProperties.getKnowledge().getDimensions() != MIGRATED_EMBEDDING_DIMENSIONS) {
            violations.add("app.knowledge.dimensions must match the migrated vector dimension");
        }
        if (!"openai".equalsIgnoreCase(environment.getProperty("spring.ai.model.embedding", ""))) {
            violations.add("spring.ai.model.embedding must be openai");
        }
        requireText(violations, "spring.ai.openai.api-key");
        requireText(violations, "spring.ai.openai.embedding.options.model");
        requireTrue(violations, "app.reporting.jobs.enabled");
    }

    private void validateEncryptionKeys(List<String> violations) {
        byte[] modelKey = decodeKey(
                violations,
                "app.model.secret-key",
                appProperties.getModel().getSecretKey());
        byte[] notificationKey = decodeKey(
                violations,
                "app.notification.secret-key",
                appProperties.getNotification().getSecretKey());
        if (modelKey.length == ENCRYPTION_KEY_BYTES
                && notificationKey.length == ENCRYPTION_KEY_BYTES
                && Arrays.equals(modelKey, notificationKey)) {
            violations.add("model and notification encryption keys must be independent");
        }
    }

    private byte[] decodeKey(List<String> violations, String propertyName, String configured) {
        if (configured == null || configured.isBlank()) {
            violations.add(propertyName + " is required");
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configured.trim());
            if (decoded.length != ENCRYPTION_KEY_BYTES) {
                violations.add(propertyName + " must decode to exactly 32 bytes");
                return new byte[0];
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            violations.add(propertyName + " must be valid Base64");
            return new byte[0];
        }
    }

    private void validateCorsOrigins(List<String> violations) {
        List<String> origins = appProperties.getCors().getAllowedOrigins();
        if (origins.isEmpty()) {
            violations.add("app.cors.allowed-origins must not be empty");
            return;
        }
        for (String origin : origins) {
            if (!isSecureOrigin(origin)) {
                violations.add("app.cors.allowed-origins must contain HTTPS origins only");
                return;
            }
        }
    }

    private boolean isSecureOrigin(String origin) {
        try {
            URI uri = new URI(origin);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && !isLocalHost(uri.getHost());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isLocalHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || normalized.endsWith(".localhost");
    }

    private void requireText(List<String> violations, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            violations.add(propertyName + " is required");
        }
    }

    private void requireTrue(List<String> violations, String propertyName) {
        if (!Boolean.TRUE.equals(environment.getProperty(propertyName, Boolean.class))) {
            violations.add(propertyName + " must be true");
        }
    }
}
