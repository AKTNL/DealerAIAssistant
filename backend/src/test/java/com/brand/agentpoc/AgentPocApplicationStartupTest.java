package com.brand.agentpoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

class AgentPocApplicationStartupTest {

    @Test
    void startsWithoutGlobalOpenAiConfiguration() {
        assertThatCode(() -> {
            SpringApplication application = new SpringApplication(AgentPocApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);

            try (ConfigurableApplicationContext ignored = application.run(
                    "--spring.datasource.url=jdbc:h2:mem:startup-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.datasource.username=sa",
                    "--spring.datasource.password="
            )) {
                // Context close is part of the assertion.
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void applicationYamlDoesNotDefaultCredentials() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));

        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("app.auth.access-key")).isEqualTo("${APP_ACCESS_KEY:}");
        assertThat(properties.getProperty("app.auth.session-secret")).isEqualTo("${APP_SESSION_SECRET:}");
        assertThat(properties.getProperty("app.security.api-key")).isEqualTo("${APP_API_KEY:}");
    }

    @Test
    void prodProfileDisablesH2Console() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-prod.yml"));

        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
    }

    @Test
    void baselineMigrationCreatesBatchScopedSchema() throws Exception {
        String url = "jdbc:h2:mem:flyway-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO import_batches "
                            + "(batch_key, source, scope_type, active, fallback_active, created_at, message) "
                            + "VALUES ('batch-a', 'test', 'GLOBAL', TRUE, FALSE, CURRENT_TIMESTAMP, 'test')"
            );
            statement.executeUpdate(
                    "INSERT INTO import_batches "
                            + "(batch_key, source, scope_type, active, fallback_active, created_at, message) "
                            + "VALUES ('batch-b', 'test', 'GLOBAL', TRUE, FALSE, CURRENT_TIMESTAMP, 'test')"
            );
            statement.executeUpdate(
                    "INSERT INTO dealers "
                            + "(dealer_code, dealer_name, city, dealer_group_name, import_batch_id) "
                            + "VALUES ('D001', 'Dealer A', 'Beijing', 'Group A', 'batch-a')"
            );
            statement.executeUpdate(
                    "INSERT INTO dealers "
                            + "(dealer_code, dealer_name, city, dealer_group_name, import_batch_id) "
                            + "VALUES ('D001', 'Dealer A', 'Beijing', 'Group A', 'batch-b')"
            );

            try (ResultSet tables = connection.getMetaData().getTables(null, null, "IMPORT_BATCHES", null);
                    ResultSet dealers = statement.executeQuery(
                            "SELECT COUNT(*) FROM dealers WHERE dealer_code = 'D001'")) {
                assertThat(tables.next()).isTrue();
                assertThat(dealers.next()).isTrue();
                assertThat(dealers.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void flywaySchemaPassesHibernateValidation() {
        SpringApplication application = new SpringApplication(AgentPocApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext ignored = application.run(
                "--spring.datasource.url=jdbc:h2:mem:flyway-jpa-validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true",
                "--app.excel.path=classpath:missing-for-migration-test.xlsx"
        )) {
            // Context creation proves that Flyway's schema satisfies Hibernate mappings.
        }
    }

    @Test
    void readmeDoesNotDocumentRemovedDefaultCredentials() throws IOException {
        String readme = Files.readString(Path.of("../README.md"));

        assertThat(readme).doesNotContain("demo123", "poc-api-key", "从访问密钥派生");
    }
}
