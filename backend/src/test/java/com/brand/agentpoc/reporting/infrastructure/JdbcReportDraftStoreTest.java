package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcReportDraftStoreTest {

    private JdbcReportDraftStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:report-store;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS report_drafts");
        jdbcTemplate.execute("""
                CREATE TABLE report_drafts (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    report_type VARCHAR(16) NOT NULL,
                    title VARCHAR(256) NOT NULL,
                    language VARCHAR(8) NOT NULL,
                    markdown TEXT NOT NULL,
                    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    import_batch_id VARCHAR(64) NOT NULL,
                    scope_type VARCHAR(32) NOT NULL,
                    scope_id VARCHAR(128),
                    model VARCHAR(128) NOT NULL,
                    prompt_version VARCHAR(64) NOT NULL
                )
                """);
        store = new JdbcReportDraftStore(jdbcTemplate);
    }

    @Test
    void persistsAndReadsReportMetadataWithoutChangingTheDraft() {
        ReportDraft draft = new ReportDraft(
                "report-1",
                ReportType.MONTHLY,
                "Monthly Report",
                "en",
                "# Monthly Report",
                Instant.parse("2026-08-10T05:00:00Z"),
                "batch-1",
                ReportScope.global(),
                "test-model",
                "reporting-v1"
        );

        assertThat(store.save(draft)).isEqualTo(draft);
        assertThat(store.findByTenantIdAndId(1L, "report-1")).contains(draft);
        assertThat(store.findByTenantIdAndId(2L, "report-1")).isEmpty();
        assertThat(store.findAllByTenantId(1L)).containsExactly(draft);
        assertThat(store.findAllByTenantId(2L)).isEmpty();
    }
}
