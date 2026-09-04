# Decision 0013: Project pgvector indexing

## Status

Accepted for Phase 6 Step 3.

## Context

Phase 6 Step 2 creates deterministic Chunks and 1024-dimensional embeddings in memory. Step 3 needs durable,
repeatable Project-level storage without introducing vector search or RAG. A provider failure, partial embedding run,
or dimension mismatch must never replace a previously valid Project index with partial data.

## Decision

- Store Chunk content, source metadata, embedding metadata, and `vector(1024)` in one
  `document_chunk_embedding` table.
- Use the deterministic SHA-256 `chunk_id` as the primary key. Scope synchronization and stale deletion by the
  portable Workspace-relative `project_id`; do not expose absolute paths or introduce random UUIDs yet.
- Keep 1024 as a database schema constraint and an application configuration value. Validate the complete embedding
  result and every individual vector before starting database writes.
- Synchronize one Project in one transaction: upsert new or changed Chunk IDs, leave identical rows untouched with a
  conditional `ON CONFLICT`, then delete IDs no longer produced for that Project.
- Do not call the repository when embedding is partial or failed. Roll back every write when any storage operation
  fails.
- Reuse the vector extension established by Flyway V1. Do not create HNSW or IVFFlat indexes until semantic search is
  designed and measured.
- Keep writes sequential for this baseline; batching and parallelism require measured evidence.

## Verification baseline

Measured on 2026-09-04 using `Local_Ai_Work`, PostgreSQL 17 with pgvector 0.8.6, Ollama, and
`qwen3-embedding:0.6b`:

- First index: 112 Documents, 181 Chunks, 181 inserted, 0 deleted, 181 stored.
- First index timing: Chunking 277 ms, Embedding 8,762 ms, DB synchronization 237 ms, total 9,317 ms.
- Identical reindex: 181 Chunks, 0 written, 0 deleted, 181 stored.
- Identical reindex timing: Chunking 107 ms, Embedding 6,031 ms, DB synchronization 187 ms, total 6,330 ms.
- Stored dimension: 1024; failed Chunks: 0.

These are development reference values, not performance targets. Ollama warm-up, filesystem cache, database state,
and machine load can change subsequent measurements.

## Consequences

The Project index is idempotent and preserves the last complete stored state when preprocessing fails. Changed source
content naturally receives new deterministic IDs, and the same transaction removes its stale IDs. The current design
still recomputes embeddings before discovering that rows are identical; avoiding that work requires a future indexing
plan and is intentionally outside this step.
