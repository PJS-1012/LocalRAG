CREATE TABLE document_chunk_embedding (
    chunk_id VARCHAR(64) PRIMARY KEY,
    project_id TEXT NOT NULL,
    source_path TEXT NOT NULL,
    source_file_name TEXT NOT NULL,
    extension VARCHAR(32) NOT NULL,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    start_offset INTEGER NOT NULL CHECK (start_offset >= 0),
    end_offset INTEGER NOT NULL CHECK (end_offset >= start_offset),
    start_line INTEGER NOT NULL CHECK (start_line >= 1),
    end_line INTEGER NOT NULL CHECK (end_line >= start_line),
    source_fingerprint VARCHAR(64) NOT NULL,
    source_modified_at TIMESTAMPTZ NOT NULL,
    embedding_model TEXT NOT NULL,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension = 1024),
    embedding VECTOR(1024) NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_chunk_embedding_project
    ON document_chunk_embedding (project_id);

CREATE INDEX idx_document_chunk_embedding_project_source
    ON document_chunk_embedding (project_id, source_path);
