package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.reporting.application.ReportDraftStore;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("prod")
public class JdbcReportDraftStore implements ReportDraftStore {

    private static final String SELECT_COLUMNS = """
            SELECT id, report_type, title, language, markdown, generated_at,
                   import_batch_id, scope_type, scope_id, model, prompt_version
            FROM report_drafts
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcReportDraftStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReportDraft save(ReportDraft draft) {
        jdbcTemplate.update("""
                        INSERT INTO report_drafts (
                            id, report_type, title, language, markdown, generated_at,
                            import_batch_id, scope_type, scope_id, model, prompt_version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                draft.id(),
                draft.reportType().wireName(),
                draft.title(),
                draft.language(),
                draft.markdown(),
                Timestamp.from(draft.generatedAt()),
                draft.importBatchId(),
                draft.scope().type(),
                draft.scope().id().isBlank() ? null : draft.scope().id(),
                draft.model(),
                draft.promptVersion()
        );
        return draft;
    }

    @Override
    public Optional<ReportDraft> findById(String id) {
        return jdbcTemplate.query(
                        SELECT_COLUMNS + " WHERE id = ?",
                        this::mapRow,
                        id
                ).stream()
                .findFirst();
    }

    @Override
    public List<ReportDraft> findAll() {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " ORDER BY generated_at DESC",
                this::mapRow
        );
    }

    private ReportDraft mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReportDraft(
                resultSet.getString("id"),
                ReportType.parse(resultSet.getString("report_type")),
                resultSet.getString("title"),
                resultSet.getString("language"),
                resultSet.getString("markdown"),
                resultSet.getTimestamp("generated_at").toInstant(),
                resultSet.getString("import_batch_id"),
                new ReportScope(resultSet.getString("scope_type"), resultSet.getString("scope_id")),
                resultSet.getString("model"),
                resultSet.getString("prompt_version")
        );
    }
}
