CREATE TABLE error_history_embedding (
    error_history_id BIGINT PRIMARY KEY REFERENCES error_history(id) ON DELETE CASCADE,
    project_id VARCHAR(512) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension = 1024),
    source_fingerprint CHAR(64) NOT NULL,
    embedding vector(1024) NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_error_history_embedding_project
    ON error_history_embedding(project_id);
