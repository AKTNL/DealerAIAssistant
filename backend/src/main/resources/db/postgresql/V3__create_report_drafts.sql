CREATE TABLE IF NOT EXISTS report_drafts (
    id VARCHAR(64) PRIMARY KEY,
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
);

CREATE INDEX IF NOT EXISTS idx_report_drafts_generated_at
    ON report_drafts (generated_at DESC);
