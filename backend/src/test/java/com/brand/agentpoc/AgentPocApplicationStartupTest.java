package com.brand.agentpoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

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
        assertThat(properties).doesNotContainKeys(
                "app.auth.access-key",
                "app.auth.session-secret",
                "app.auth.session-ttl",
                "app.security.api-key"
        );
        assertThat(properties.getProperty("app.auth.access-token-ttl"))
                .isEqualTo("${APP_AUTH_ACCESS_TOKEN_TTL:30m}");
        assertThat(properties.getProperty("app.auth.refresh-token-ttl"))
                .isEqualTo("${APP_AUTH_REFRESH_TOKEN_TTL:7d}");
        assertThat(properties.getProperty("app.auth.bootstrap.username"))
                .isEqualTo("${APP_AUTH_BOOTSTRAP_USERNAME:}");
        assertThat(properties.getProperty("app.auth.bootstrap.password"))
                .isEqualTo("${APP_AUTH_BOOTSTRAP_PASSWORD:}");
    }

    @Test
    void persistsTheInitialAdministratorWithRolesAndAudit() {
        SpringApplication application = new SpringApplication(AgentPocApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.datasource.url=jdbc:h2:mem:bootstrap-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--app.excel.path=classpath:missing-for-auth-bootstrap.xlsx",
                "--app.auth.bootstrap.username=Initial.Admin",
                "--app.auth.bootstrap.password=temporary-password",
                "--app.auth.bootstrap.display-name=Initial Administrator"
        )) {
            AuthUserRepository users = context.getBean(AuthUserRepository.class);
            AuthAuditEventRepository auditEvents = context.getBean(AuthAuditEventRepository.class);
            PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);

            AuthUserEntity administrator = users.findByUsernameIgnoreCase("initial.admin").getFirst();
            assertThat(administrator.getDisplayName()).isEqualTo("Initial Administrator");
            assertThat(administrator.getMustChangePassword()).isTrue();
            assertThat(administrator.getRoles())
                    .extracting(role -> role.getRoleKey())
                    .containsExactly("ADMIN");
            assertThat(passwordEncoder.matches("temporary-password", administrator.getPasswordHash())).isTrue();
            assertThat(administrator.getPasswordHash()).doesNotContain("temporary-password");
            assertThat(auditEvents.findAll())
                    .extracting(event -> event.getAction())
                    .containsExactlyInAnyOrder("USER_BOOTSTRAP", "ORG_BOOTSTRAP");
        }
    }

    @Test
    void servletApplicationCreatesTheSecurityFilterChain() {
        SpringApplication application = new SpringApplication(AgentPocApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);

        try (ConfigurableApplicationContext context = application.run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:servlet-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--app.excel.path=classpath:missing-for-servlet-security.xlsx"
        )) {
            assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
            assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
        }
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
        assertThat(properties.getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration,classpath:db/postgresql");
        assertThat(properties.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        assertThat(properties.getProperty("app.auth.cookie-secure"))
                .isEqualTo("${APP_AUTH_COOKIE_SECURE:true}");
        assertThat(properties.getProperty("app.auth.bootstrap.required"))
                .isEqualTo("${APP_AUTH_BOOTSTRAP_REQUIRED:true}");
        assertThat(properties.getProperty("app.knowledge.vector-store"))
                .isEqualTo("${APP_KNOWLEDGE_VECTOR_STORE:pgvector}");
    }

    @Test
    void pgvectorMigrationMatchesTheConfiguredKnowledgeStoreContract() throws IOException {
        String migration = new ClassPathResource(
                "db/postgresql/V2__create_knowledge_vector_store.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("CREATE EXTENSION IF NOT EXISTS vector")
                .contains("id TEXT PRIMARY KEY")
                .contains("metadata JSON NOT NULL")
                .contains("embedding VECTOR(1536) NOT NULL")
                .contains("USING HNSW (embedding vector_cosine_ops)");
    }

    @Test
    void reportMigrationCreatesTheProductionDraftStoreContract() throws Exception {
        String migration = new ClassPathResource(
                "db/postgresql/V3__create_report_drafts.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        String url = "jdbc:h2:mem:report-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(migration);
            statement.executeUpdate("""
                    INSERT INTO report_drafts (
                        id, report_type, title, language, markdown, generated_at,
                        import_batch_id, scope_type, scope_id, model, prompt_version
                    ) VALUES (
                        'report-1', 'weekly', 'Weekly Report', 'en', '# Weekly Report',
                        CURRENT_TIMESTAMP, 'batch-1', 'GLOBAL', NULL, 'deterministic', 'reporting-v1'
                    )
                    """);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT report_type, import_batch_id, scope_type, model, prompt_version
                    FROM report_drafts
                    WHERE id = 'report-1'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("report_type")).isEqualTo("weekly");
                assertThat(rows.getString("import_batch_id")).isEqualTo("batch-1");
                assertThat(rows.getString("scope_type")).isEqualTo("GLOBAL");
                assertThat(rows.getString("model")).isEqualTo("deterministic");
                assertThat(rows.getString("prompt_version")).isEqualTo("reporting-v1");
            }
        }
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
                            + "(tenant_id, batch_key, source, scope_type, active, fallback_active, created_at, message) "
                            + "VALUES (1, 'batch-a', 'test', 'GLOBAL', TRUE, FALSE, CURRENT_TIMESTAMP, 'test')"
            );
            statement.executeUpdate(
                    "INSERT INTO import_batches "
                            + "(tenant_id, batch_key, source, scope_type, active, fallback_active, created_at, message) "
                            + "VALUES (1, 'batch-b', 'test', 'GLOBAL', TRUE, FALSE, CURRENT_TIMESTAMP, 'test')"
            );
            statement.executeUpdate(
                    "INSERT INTO dealers "
                            + "(tenant_id, dealer_code, dealer_name, city, dealer_group_name, import_batch_id) "
                            + "VALUES (1, 'D001', 'Dealer A', 'Beijing', 'Group A', 'batch-a')"
            );
            statement.executeUpdate(
                    "INSERT INTO dealers "
                            + "(tenant_id, dealer_code, dealer_name, city, dealer_group_name, import_batch_id) "
                            + "VALUES (1, 'D001', 'Dealer A', 'Beijing', 'Group A', 'batch-b')"
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
    void tenantMigrationBackfillsExistingSingleTenantRecordsAndMemberships() throws Exception {
        String url = "jdbc:h2:mem:tenant-backfill-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("5"))
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_roles (
                        id, role_key, display_name, built_in, created_at, updated_at, version
                    ) VALUES (10, 'TEST_ROLE', 'Test Role', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """);
            statement.executeUpdate("""
                    INSERT INTO auth_users (
                        id, username, display_name, password_hash, enabled,
                        must_change_password, created_at, updated_at, version
                    ) VALUES (
                        20, 'tenant.user', 'Tenant User', '{bcrypt}hash', TRUE,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("INSERT INTO auth_user_roles (user_id, role_id) VALUES (20, 10)");
            statement.executeUpdate("""
                    INSERT INTO import_batches (
                        batch_key, source, scope_type, active, fallback_active, created_at, message
                    ) VALUES ('legacy-batch', 'test', 'GLOBAL', TRUE, FALSE, CURRENT_TIMESTAMP, 'legacy')
                    """);
            statement.executeUpdate("""
                    INSERT INTO dealers (
                        dealer_code, dealer_name, city, dealer_group_name, import_batch_id
                    ) VALUES ('D001', 'Dealer A', 'Beijing', 'Group A', 'legacy-batch')
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        SELECT t.tenant_key, b.tenant_id AS batch_tenant_id,
                               d.tenant_id AS dealer_tenant_id, m.enabled, mr.role_id
                        FROM tenants t
                        JOIN import_batches b ON b.tenant_id = t.id AND b.batch_key = 'legacy-batch'
                        JOIN dealers d ON d.tenant_id = t.id AND d.dealer_code = 'D001'
                        JOIN tenant_memberships m ON m.tenant_id = t.id AND m.user_id = 20
                        JOIN tenant_membership_roles mr ON mr.membership_id = m.id
                        """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("tenant_key")).isEqualTo("default");
            assertThat(rows.getLong("batch_tenant_id")).isEqualTo(rows.getLong("dealer_tenant_id"));
            assertThat(rows.getBoolean("enabled")).isTrue();
            assertThat(rows.getLong("role_id")).isEqualTo(10L);
            assertThat(rows.next()).isFalse();
        }
    }

    @Test
    void authMigrationCreatesIdentitySessionAndAuditSchema() throws Exception {
        String url = "jdbc:h2:mem:auth-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_roles (
                        id, role_key, display_name, built_in, created_at, updated_at, version
                    ) VALUES (
                        10, 'ADMIN', 'Administrator', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO auth_role_permissions (role_id, permission_key)
                    VALUES (10, 'USER_MANAGE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO auth_users (
                        id, username, display_name, password_hash, enabled,
                        must_change_password, created_at, updated_at, version
                    ) VALUES (
                        20, 'initial.admin', 'Initial Administrator', '{bcrypt}hash', TRUE,
                        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("INSERT INTO auth_user_roles (user_id, role_id) VALUES (20, 10)");
            statement.executeUpdate("""
                    INSERT INTO auth_sessions (
                        family_key, user_id, access_token_hash, refresh_token_hash, issued_at,
                        access_expires_at, refresh_expires_at, version
                    ) VALUES (
                        'family-1', 20,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO auth_audit_events (
                        actor_user_id, action, target_type, target_id, outcome,
                        trace_id, detail_code, created_at
                    ) VALUES (
                        20, 'USER_BOOTSTRAP', 'USER', '20', 'SUCCESS',
                        'startup', 'initial_administrator_created', CURRENT_TIMESTAMP
                    )
                    """);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM auth_users u
                    JOIN auth_user_roles ur ON ur.user_id = u.id
                    JOIN auth_roles r ON r.id = ur.role_id
                    JOIN auth_role_permissions rp ON rp.role_id = r.id
                    WHERE u.username = 'initial.admin' AND rp.permission_key = 'USER_MANAGE'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (ResultSet sessions = statement.executeQuery("SELECT COUNT(*) FROM auth_sessions")) {
                assertThat(sessions.next()).isTrue();
                assertThat(sessions.getInt(1)).isEqualTo(1);
            }
            try (ResultSet audits = statement.executeQuery("SELECT COUNT(*) FROM auth_audit_events")) {
                assertThat(audits.next()).isTrue();
                assertThat(audits.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void organizationMigrationCreatesTreeMappingsAndGrantSchema() throws Exception {
        String url = "jdbc:h2:mem:organization-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_roles (
                        id, role_key, display_name, built_in, created_at, updated_at, version
                    ) VALUES (10, 'TEST_ROLE', 'Test Role', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """);
            statement.executeUpdate("""
                    INSERT INTO auth_users (
                        id, username, display_name, password_hash, enabled,
                        must_change_password, created_at, updated_at, version
                    ) VALUES (
                        20, 'scope.user', 'Scope User', '{bcrypt}hash', TRUE,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization_nodes (
                        tenant_id, node_key, display_name, node_type, parent_id, enabled,
                        created_at, updated_at, version
                    ) VALUES (
                        1, 'REGION_TEST', 'Region Test', 'REGION',
                        (SELECT id FROM organization_nodes WHERE node_key = 'GLOBAL_ROOT'),
                        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization_nodes (
                        tenant_id, node_key, display_name, node_type, parent_id, enabled,
                        created_at, updated_at, version
                    ) VALUES (
                        1, 'CITY_TEST', 'City Test', 'CITY',
                        (SELECT id FROM organization_nodes WHERE node_key = 'REGION_TEST'),
                        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization_nodes (
                        tenant_id, node_key, display_name, node_type, parent_id, enabled,
                        created_at, updated_at, version
                    ) VALUES (
                        1, 'DEALER_TEST', 'Dealer Test', 'DEALER',
                        (SELECT id FROM organization_nodes WHERE node_key = 'CITY_TEST'),
                        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization_dealer_mappings (
                        tenant_id, organization_node_id, dealer_code, created_at
                    ) SELECT 1, id, 'D001', CURRENT_TIMESTAMP
                    FROM organization_nodes WHERE node_key = 'DEALER_TEST'
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization_user_grants (
                        tenant_id, user_id, organization_node_id, include_descendants, created_at
                    ) SELECT 1, 20, id, FALSE, CURRENT_TIMESTAMP
                    FROM organization_nodes WHERE node_key = 'DEALER_TEST'
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization_role_grants (
                        tenant_id, role_id, organization_node_id, include_descendants, created_at
                    ) SELECT 1, 10, id, TRUE, CURRENT_TIMESTAMP
                    FROM organization_nodes WHERE node_key = 'GLOBAL_ROOT'
                    """);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM organization_user_grants ug
                    JOIN organization_nodes n ON n.id = ug.organization_node_id
                    JOIN organization_dealer_mappings m ON m.organization_node_id = n.id
                    WHERE ug.user_id = 20 AND m.dealer_code = 'D001'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
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
