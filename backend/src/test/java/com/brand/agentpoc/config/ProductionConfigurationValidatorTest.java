package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {

    private static final String MODEL_KEY = key((byte) 7);
    private static final String NOTIFICATION_KEY = key((byte) 9);

    @Test
    void acceptsTheFailClosedProductionBaseline() {
        ProductionConfigurationValidator validator = validator(validProperties(), validEnvironment());

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void reportsAllUnsafeSettingsWithoutEchoingSecrets() {
        AppProperties properties = new AppProperties();
        properties.getModel().setSecretKey("invalid-model-secret");
        properties.getNotification().setSecretKey("invalid-notification-secret");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:unsafe")
                .withProperty("spring.datasource.username", "")
                .withProperty("spring.datasource.password", "database-secret")
                .withProperty("spring.datasource.driver-class-name", "org.h2.Driver")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.flyway.validate-on-migrate", "false")
                .withProperty("spring.flyway.clean-disabled", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update")
                .withProperty("spring.ai.model.embedding", "none")
                .withProperty("app.reporting.jobs.enabled", "false");

        assertThatThrownBy(validator(properties, environment)::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining("spring.datasource.username")
                .hasMessageContaining("spring.flyway.clean-disabled")
                .hasMessageContaining("app.auth.cookie-secure")
                .hasMessageContaining("app.excel.fallback-enabled")
                .hasMessageContaining("app.knowledge.vector-store")
                .hasMessageContaining("spring.ai.openai.api-key")
                .hasMessageContaining("app.model.secret-key")
                .hasMessageContaining("app.notification.secret-key")
                .hasMessageNotContaining("database-secret")
                .hasMessageNotContaining("invalid-model-secret")
                .hasMessageNotContaining("invalid-notification-secret");
    }

    @Test
    void rejectsLocalOrNonOriginCorsValues() {
        AppProperties properties = validProperties();
        properties.getCors().setAllowedOrigins(List.of("https://localhost", "https://app.example.com/path"));

        assertThatThrownBy(validator(properties, validEnvironment())::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.cors.allowed-origins must contain HTTPS origins only");
    }

    @Test
    void requiresIndependentEncryptionKeys() {
        AppProperties properties = validProperties();
        properties.getNotification().setSecretKey(MODEL_KEY);

        assertThatThrownBy(validator(properties, validEnvironment())::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption keys must be independent")
                .hasMessageNotContaining(MODEL_KEY);
    }

    @Test
    void requiresTheMigratedEmbeddingDimension() {
        AppProperties properties = validProperties();
        properties.getKnowledge().setDimensions(3072);

        assertThatThrownBy(validator(properties, validEnvironment())::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.knowledge.dimensions must match the migrated vector dimension");
    }

    private ProductionConfigurationValidator validator(
            AppProperties properties,
            MockEnvironment environment
    ) {
        return new ProductionConfigurationValidator(properties, environment);
    }

    private AppProperties validProperties() {
        AppProperties properties = new AppProperties();
        properties.getAuth().setCookieSecure(true);
        properties.getAuth().getBootstrap().setRequired(true);
        properties.getExcel().setFallbackEnabled(false);
        properties.getKnowledge().setVectorStore("pgvector");
        properties.getModel().setSecretKey(MODEL_KEY);
        properties.getNotification().setSecretKey(NOTIFICATION_KEY);
        properties.getCors().setAllowedOrigins(List.of("https://dealer-ai.example.com"));
        return properties;
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://postgres:5432/agentpoc")
                .withProperty("spring.datasource.username", "agentpoc")
                .withProperty("spring.datasource.password", "database-secret")
                .withProperty("spring.datasource.driver-class-name", "org.postgresql.Driver")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.validate-on-migrate", "true")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.ai.model.embedding", "openai")
                .withProperty("spring.ai.openai.api-key", "embedding-provider-secret")
                .withProperty("spring.ai.openai.embedding.options.model", "text-embedding-ada-002")
                .withProperty("app.reporting.jobs.enabled", "true");
    }

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
