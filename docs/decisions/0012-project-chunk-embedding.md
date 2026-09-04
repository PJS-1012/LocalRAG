# 0012. Project Chunk embedding baseline

## Status

Accepted

## Context

Phase 6 Step 2 applies the existing `qwen3-embedding:0.6b` Spring AI `EmbeddingModel` to in-memory
`DocumentChunk` values. Vector persistence, schema design, and retrieval remain outside this step.

## Decision

- Reuse the existing `EmbeddingService` and `EmbeddingModel`; do not create another Ollama client.
- Use Spring AI 1.1.8 `EmbeddingModel.embed(List<String>)` for one observable Project batch.
- Keep processing sequential and do not add Virtual Threads or parallel requests.
- Keep `EmbeddedChunk` as the relationship between one `DocumentChunk`, model name, derived dimension, and full
  internal vector.
- Never expose full vectors through the Preview API; return only the first eight values.
- Derive the expected dimension from the most frequent non-empty result dimension instead of hardcoding 1024.
- Mark vectors with a different dimension as `DIMENSION_MISMATCH`.
- Reject an unexpected empty Chunk before calling the provider.

## Failure model

A connection failure and missing model are Project-level provider failures. They are reported as
`PROVIDER_UNAVAILABLE` and `MODEL_UNAVAILABLE` and are not retried once per Chunk.

A non-system batch failure falls back to sequential single-Chunk requests so one invalid data item can be isolated.
Individual failures are retained with Chunk identity and reason while successful Chunk results remain available.
A returned vector-count mismatch is treated as an invalid provider response.

## Preview API

```http
POST /api/workspaces/projects/chunks/embeddings/preview?projectId=Local_Ai_Work
```

The response contains Project/Document/Chunk counts, success and failure counts, model, derived dimensions, separate
Chunking and Embedding durations, run status, up to eight vector values per successful Chunk, and failure summaries.

## Actual consistency check

Using the local `qwen3-embedding:0.6b` model:

- repeated identical content returned 1024 dimensions both times
- repeated results were not bit-identical
- the first-eight-value cosine similarity was 0.9994978
- the maximum absolute difference in the first eight values was 0.0021193
- different content returned a different vector preview

The repeated output is practically equivalent but floating-point/provider execution is not strictly deterministic.
Future tests and caching must use a tolerance or similarity measure, not exact array equality.

## Local_Ai_Work baseline

- source Documents: 100
- generated Chunks: 155
- successfully embedded Chunks: 155
- failed Chunks: 0
- embedding model: `qwen3-embedding:0.6b`
- derived and consistent dimension: 1024
- read and Chunking duration: 107 ms
- Embedding batch duration: 7,258 ms
- average Embedding wall time per Chunk: 46.826 ms
- total duration: 7,366 ms

The average is batch wall time divided by Chunk count, not the latency of an individual provider request. Counts and
timings can change with the working tree, filesystem cache, model warm-up state, CPU/GPU load, and Ollama version.

## Operational observation

During final validation Ollama was not running. The Project result returned `PROVIDER_UNAVAILABLE`, dimension 0,
and 155 failures in 110 ms without retrying every Chunk. After Ollama restarted, the same request completed with all
155 Chunks embedded. This confirms the Provider failure classification while showing that application availability
still depends on the independently managed local Ollama process.
## Consequences

Batching is substantially more practical than issuing one request for every Chunk, while the fallback retains
individual data-failure isolation. Full vectors remain in memory only. The next persistence step must decide schema,
transaction boundaries, replacement semantics, and dimension constraints before storing any vector.
