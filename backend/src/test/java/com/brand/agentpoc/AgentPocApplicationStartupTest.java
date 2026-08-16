package com.brand.agentpoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.boot.actuate.health.HealthEndpointGroups;
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

            HealthEndpointGroups groups = context.getBean(HealthEndpointGroups.class);
            assertThat(groups.get("liveness").isMember("livenessState")).isTrue();
            assertThat(groups.get("liveness").isMember("db")).isFalse();
            assertThat(groups.get("readiness").isMember("readinessState")).isTrue();
            assertThat(groups.get("readiness").isMember("db")).isTrue();
            assertThat(groups.get("readiness").isMember("migration")).isTrue();
            assertThat(groups.get("readiness").isMember("knowledge")).isTrue();
            assertThat(groups.get("readiness").isMember("operationalQueue")).isFalse();

            Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            assertThat(port).isNotNull();
            assertThat(getStatus(port, "/livez")).isEqualTo(200);
            assertThat(getStatus(port, "/readyz")).isEqualTo(200);
        }
    }

    private int getStatus(int port, String path) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException exception) {
            throw new IllegalStateException("Health probe request failed.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Health probe request was interrupted.", exception);
        }
    }

    @Test
    void applicationYamlSeparatesProbesAndEnablesMetricHistograms() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));

        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(properties.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db,migration,knowledge");
        assertThat(properties.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests"))
                .isEqualTo("true");
        assertThat(properties.getProperty(
                "management.metrics.distribution.percentiles-histogram.agentpoc.model.call"))
                .isEqualTo("true");
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
        assertThat(properties.getProperty("spring.ai.model.embedding")).isEqualTo("openai");
        assertThat(properties.getProperty("app.auth.cookie-secure"))
                .isEqualTo("${APP_AUTH_COOKIE_SECURE:true}");
        assertThat(properties.getProperty("app.auth.bootstrap.required"))
                .isEqualTo("${APP_AUTH_BOOTSTRAP_REQUIRED:true}");
        assertThat(properties.getProperty("app.knowledge.vector-store"))
                .isEqualTo("${APP_KNOWLEDGE_VECTOR_STORE:pgvector}");
        assertThat(properties.getProperty("app.reporting.jobs.enabled"))
                .isEqualTo("${APP_REPORTING_JOBS_ENABLED:true}");
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
    void reportSubscriptionMigrationPersistsTenantScheduleAndRecipients() throws Exception {
        String url = "jdbc:h2:mem:report-subscription-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_users (
                        id, username, display_name, password_hash, enabled,
                        must_change_password, created_at, updated_at, version
                    ) VALUES (
                        20, 'subscriber', 'Subscriber', '{bcrypt}hash', TRUE,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_subscriptions (
                        id, tenant_id, creator_user_id, report_type, scope_type, scope_id,
                        language, topic, schedule_kind, local_time, time_zone, channel_key,
                        enabled, next_run_at, misfire_policy, misfire_grace_minutes,
                        active_configuration_key, created_at, updated_at, version
                    ) VALUES (
                        30, 1, 20, 'weekly', 'ORGANIZATION', '1',
                        'zh', '', 'WEEKLY', '09:00:00', 'Asia/Shanghai', 'email',
                        TRUE, CURRENT_TIMESTAMP, 'SKIP', 60,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_subscription_recipients (subscription_id, recipient_user_id)
                    VALUES (30, 20)
                    """);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT s.tenant_id, s.schedule_kind, s.time_zone, r.recipient_user_id
                    FROM report_subscriptions s
                    JOIN report_subscription_recipients r ON r.subscription_id = s.id
                    WHERE s.id = 30
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("tenant_id")).isEqualTo(1L);
                assertThat(rows.getString("schedule_kind")).isEqualTo("WEEKLY");
                assertThat(rows.getString("time_zone")).isEqualTo("Asia/Shanghai");
                assertThat(rows.getLong("recipient_user_id")).isEqualTo(20L);
            }
        }
    }

    @Test
    void reportCollaborationMigrationPersistsWorkflowHistoryAndNotificationOutbox() throws Exception {
        String url = "jdbc:h2:mem:report-collaboration-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10"))
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_roles (
                        id, role_key, display_name, built_in, created_at, updated_at, version
                    ) VALUES
                        (61, 'ADMIN', 'Administrator', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
                        (62, 'ANALYST', 'Analyst', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
                        (63, 'VIEWER', 'Viewer', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_users (
                        id, username, display_name, password_hash, enabled,
                        must_change_password, created_at, updated_at, version
                    ) VALUES (
                        60, 'workflow.owner', 'Workflow Owner', '{bcrypt}hash', TRUE,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_collaborations (
                        id, tenant_id, report_draft_id, scope_type, scope_id, status,
                        assignee_user_id, assignee_username, assignee_display_name,
                        activity_count, created_at, updated_at, version
                    ) VALUES (
                        60, 1, 'report-workflow-1', 'ORGANIZATION', '11', 'IN_PROGRESS',
                        60, 'workflow.owner', 'Workflow Owner', 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_collaboration_events (
                        id, collaboration_id, tenant_id, report_draft_id, event_type,
                        actor_user_id, actor_username, actor_display_name, previous_value,
                        current_value, comment_body, trace_id, created_at
                    ) VALUES (
                        60, 60, 1, 'report-workflow-1', 'COMMENT_ADDED',
                        60, 'workflow.owner', 'Workflow Owner', NULL,
                        'comment_length:8', 'Reviewed', 'trace-workflow-1', CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_collaboration_notifications (
                        id, collaboration_id, event_id, tenant_id, recipient_user_id,
                        channel_key, delivery_key, status, attempt, max_attempts,
                        created_at, updated_at, version
                    ) VALUES (
                        60, 60, 60, 1, 60, 'email', 'collaboration:60:60',
                        'READY', 0, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT c.status, e.event_type, e.comment_body, n.status AS notification_status
                    FROM report_collaborations c
                    JOIN report_collaboration_events e ON e.collaboration_id = c.id
                    JOIN report_collaboration_notifications n ON n.event_id = e.id
                    WHERE c.tenant_id = 1 AND c.report_draft_id = 'report-workflow-1'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("IN_PROGRESS");
                assertThat(rows.getString("event_type")).isEqualTo("COMMENT_ADDED");
                assertThat(rows.getString("comment_body")).isEqualTo("Reviewed");
                assertThat(rows.getString("notification_status")).isEqualTo("READY");
            }

            try (ResultSet permissions = statement.executeQuery("""
                    SELECT r.role_key
                    FROM auth_roles r
                    JOIN auth_role_permissions p ON p.role_id = r.id
                    WHERE p.permission_key = 'REPORT_COLLABORATE'
                    ORDER BY r.role_key
                    """)) {
                assertThat(permissions.next()).isTrue();
                assertThat(permissions.getString("role_key")).isEqualTo("ADMIN");
                assertThat(permissions.next()).isTrue();
                assertThat(permissions.getString("role_key")).isEqualTo("ANALYST");
                assertThat(permissions.next()).isFalse();
            }
        }
    }

    @Test
    void openApiDocumentsReportSubscriptionContract() throws Exception {
        JsonNode openApi = new ObjectMapper().readTree(
                new ClassPathResource("static/openapi.json").getInputStream());

        assertThat(openApi.at("/paths/~1api~1report-subscriptions/post/operationId").asText())
                .isEqualTo("createReportSubscription");
        assertThat(openApi.at("/components/schemas/ReportSubscriptionRequest/properties/timeZone/type").asText())
                .isEqualTo("string");
        assertThat(openApi.at("/components/schemas/ReportSubscription/properties/executionEligible/type").asText())
                .isEqualTo("boolean");
        assertThat(openApi.at("/paths/~1api~1report-jobs/get/operationId").asText())
                .isEqualTo("listReportGenerationJobs");
        assertThat(openApi.at("/components/schemas/ReportGenerationJob/properties/status/type").asText())
                .isEqualTo("string");
        assertThat(openApi.at("/components/schemas/ReportSubscriptionRecipient/properties/emailConfigured/type")
                .asText()).isEqualTo("boolean");
        assertThat(openApi.at("/paths/~1api~1notification~1smtp/put/operationId").asText())
                .isEqualTo("saveTenantSmtpConfig");
        assertThat(openApi.at("/paths/~1api~1report-deliveries~1{id}~1force-replay/post/operationId")
                .asText()).isEqualTo("forceReplayReportDelivery");
        assertThat(openApi.at("/components/schemas/AdminUser/properties/email/format").asText())
                .isEqualTo("email");
        assertThat(openApi.at("/paths/~1api~1report-collaborations/get/operationId").asText())
                .isEqualTo("listReportCollaborations");
        assertThat(openApi.at("/paths/~1api~1report-collaborations~1{reportId}~1comments/post/operationId")
                .asText()).isEqualTo("addReportCollaborationComment");
        assertThat(openApi.at("/components/schemas/ReportCollaborationSummary/properties/status/enum")
                .toString()).contains("IN_PROGRESS", "RESOLVED", "CLOSED");
        assertThat(openApi.at("/components/schemas/ReportCollaborationConflictResponse/properties/data/properties"
                + "/currentVersion/format").asText()).isEqualTo("int64");
        assertThat(openApi.at("/components/schemas/ReportCollaborationMutationConflictResponse/oneOf").size())
                .isEqualTo(2);
        assertThat(openApi.at("/paths/~1api~1report-collaborations~1{reportId}~1status/patch/responses/409"
                + "/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ReportCollaborationMutationConflictResponse");
    }

    @Test
    void openApiDocumentsModelUsageGovernanceWithoutSensitivePayloadFields() throws Exception {
        JsonNode openApi = new ObjectMapper().readTree(
                new ClassPathResource("static/openapi.json").getInputStream());

        assertThat(openApi.at("/paths/~1api~1admin~1model-usage~1summary/get/operationId").asText())
                .isEqualTo("getTenantModelUsageSummary");
        assertThat(openApi.at("/paths/~1api~1admin~1model-usage~1budget/put/operationId").asText())
                .isEqualTo("saveTenantModelBudget");
        assertThat(openApi.at("/paths/~1api~1platform~1model-usage~1summary/get/operationId").asText())
                .isEqualTo("getPlatformModelUsageSummary");
        JsonNode eventProperties = openApi.at("/components/schemas/ModelUsageEvent/properties");
        assertThat(eventProperties.has("traceId")).isTrue();
        assertThat(eventProperties.has("prompt")).isFalse();
        assertThat(eventProperties.has("completion")).isFalse();
        assertThat(eventProperties.has("apiKey")).isFalse();
        assertThat(eventProperties.has("baseUrl")).isFalse();
        assertThat(openApi.at("/components/schemas/ModelUsageBudgetRequest/properties/hardLimitEnabled/default")
                .asBoolean()).isFalse();
    }

    @Test
    void modelUsageMigrationCreatesGovernanceTablesAndAdministratorPermissions() throws Exception {
        String url = "jdbc:h2:mem:model-usage-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11"))
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_roles (
                        id, role_key, display_name, built_in, created_at, updated_at, version
                    ) VALUES (
                        71, 'ADMIN', 'Administrator', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            try (ResultSet tables = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME IN (
                        'MODEL_USAGE_EVENTS', 'MODEL_PRICE_VERSIONS',
                        'MODEL_BUDGET_POLICIES', 'MODEL_BUDGET_RESERVATIONS'
                    )
                    """)) {
                assertThat(tables.next()).isTrue();
                assertThat(tables.getInt(1)).isEqualTo(4);
            }
            try (ResultSet permissions = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM auth_role_permissions permission
                    JOIN auth_roles role ON role.id = permission.role_id
                    WHERE role.role_key = 'ADMIN'
                      AND permission.permission_key IN (
                          'MODEL_USAGE_READ', 'MODEL_USAGE_MANAGE', 'MODEL_USAGE_PLATFORM_READ'
                      )
                    """)) {
                assertThat(permissions.next()).isTrue();
                assertThat(permissions.getInt(1)).isEqualTo(3);
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
    void reportGenerationJobMigrationCreatesIdempotentLeaseSchema() throws Exception {
        String url = "jdbc:h2:mem:report-job-migration-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_users (
                        id, username, display_name, password_hash, enabled,
                        must_change_password, created_at, updated_at, version
                    ) VALUES (
                        40, 'job.creator', 'Job Creator', '{bcrypt}hash', TRUE,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_subscriptions (
                        id, tenant_id, creator_user_id, report_type, scope_type, scope_id,
                        language, topic, schedule_kind, local_time, time_zone, channel_key,
                        enabled, next_run_at, misfire_policy, misfire_grace_minutes,
                        active_configuration_key, created_at, updated_at, version
                    ) VALUES (
                        40, 1, 40, 'daily', 'GLOBAL', '', 'en', '', 'DAILY', '09:00:00',
                        'Asia/Shanghai', 'email', TRUE, CURRENT_TIMESTAMP, 'SKIP', 60,
                        'job-config', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO report_generation_jobs (
                        id, subscription_id, tenant_id, creator_user_id, scheduled_at,
                        idempotency_key, report_type, scope_type, scope_id, language, topic,
                        status, attempt, max_attempts, trace_id, created_at, updated_at, version
                    ) VALUES (
                        40, 40, 1, 40, CURRENT_TIMESTAMP, '40:window-1', 'daily', 'GLOBAL',
                        '', 'en', '', 'READY', 0, 4, 'trace-job-1', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 0
                    )
                    """);

            try (ResultSet rows = statement.executeQuery("""
                    SELECT status, attempt, max_attempts, trace_id
                    FROM report_generation_jobs
                    WHERE id = 40
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("READY");
                assertThat(rows.getInt("attempt")).isZero();
                assertThat(rows.getInt("max_attempts")).isEqualTo(4);
                assertThat(rows.getString("trace_id")).isEqualTo("trace-job-1");
            }
        }
    }

    @Test
    void readmeDoesNotDocumentRemovedDefaultCredentials() throws IOException {
        String readme = Files.readString(Path.of("../README.md"));

        assertThat(readme).doesNotContain("demo123", "poc-api-key", "从访问密钥派生");
    }

}
