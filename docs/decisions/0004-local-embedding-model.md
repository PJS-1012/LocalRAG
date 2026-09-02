# 0004. Local embedding model

## Status

Accepted

## Problem

LocalRAG needs one embedding space that works for Korean documents, English text, and source code without sending content to an external API.

## Candidates

- `qwen3-embedding:0.6b`: multilingual and code retrieval support with a relatively small local model.
- `bge-m3`: established multilingual model with a larger local footprint.
- `embeddinggemma`: lightweight multilingual model suited to general document retrieval.

## Decision

Use `qwen3-embedding:0.6b` through Ollama. Keep model pulling explicit and expose only the vector dimension and a short preview from the learning API.

## Consequence

The model produced 1024-dimensional vectors in the local integration test. Changing the embedding model later requires regenerating all stored document and query vectors, and the measured dimension will become part of the pgvector schema decision in Phase 7.
