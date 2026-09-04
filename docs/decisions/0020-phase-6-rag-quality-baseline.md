# Decision 0020: Phase 6 RAG answer-quality baseline

## Status

Accepted. Phase 6 is complete with documented retrieval and grounding limitations.

## Evaluation setup

- Corpus: current `Local_Ai_Work`, reindexed before evaluation
- Documents / stored Chunks: 151 / 259
- Text and Markdown Chunk size: 2,000 characters
- Source-code Chunk size: 2,400 characters
- Overlap: 200 characters
- Embedding: `qwen3-embedding:0.6b`, 1,024 dimensions
- Search: cosine similarity, exact sequential search, Top-K 5, threshold 0.45
- Query instruction: ON
- Context budget: 8,000 formatted characters
- Chat model: `qwen3:8b`

The previous 208-Chunk index did not contain the Step 5 and Step 6 implementation. It was refreshed with the same
fixed baseline before the final evaluation; this produced 259 stored Chunks. No Chunking, search, or Prompt setting
was changed during evaluation.

## Manual quality evaluation

Ratings are manual `PASS`, `PARTIAL`, or `FAIL`. `N/A` means that no-evidence behavior is not applicable.

| # | Query | Retrieval | Groundedness | Citation meaning | Hallucination | Language | No evidence | Overall |
|---:|---|---|---|---|---|---|---|---|
| 1 | Project type discovery | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 2 | Sensitive-file exclusion | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 3 | Workspace boundary protection | PARTIAL | PARTIAL | PARTIAL | PASS | PASS | N/A | PARTIAL |
| 4 | Document Chunking | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 5 | Chunk to Vector conversion | PARTIAL | FAIL | PARTIAL | FAIL | PASS | N/A | FAIL |
| 6 | Duplicate reindexing | PARTIAL | PASS | PASS | PASS | PASS | N/A | PASS |
| 7 | Unity generated-folder exclusion | PASS | PARTIAL | PASS | PARTIAL | PASS | N/A | PARTIAL |
| 8 | Nested Project discovery | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 9 | Invalid UTF-8 isolation | PARTIAL | PASS | PASS | PASS | PASS | N/A | PASS |
| 10 | Missing Kafka configuration | PARTIAL | PASS | PARTIAL | PASS | PASS | PARTIAL | PARTIAL |
| 11 | Embedding dimension mismatch | PASS | PARTIAL | PARTIAL | PARTIAL | PASS | N/A | PARTIAL |
| 12 | 8,000-character Context budget | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 13 | File larger than 5 MB | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 14 | Missing RabbitMQ retry policy | PASS | PASS | N/A | PASS | PASS | PASS | PASS |

Overall: 9 PASS, 4 PARTIAL, and 1 FAIL. Every Korean Query received a Korean answer. All emitted Citation IDs existed;
the table's Citation rating additionally judges whether the cited text supports the associated claim.

## Material findings

1. The Chunk-to-Vector answer did not retrieve `EmbeddingService` or the main embedding orchestration method. It
   incorrectly attributed the transformation to the `EmbeddedChunk` data record and used a test-created `float[]`
   as conversion evidence. This is a semantic behavior hallucination even though the named classes exist.
2. The Workspace answer was supported mainly by Decision Logs and thin configuration/exception types instead of the
   concrete path-validation methods. Its high-level policy was right, but implementation grounding was incomplete.
3. The dimension-mismatch answer correctly described majority-dimension validation and partial failure, but
   overstated pre-filtering behavior not shown by the implementation.
4. Unity, reindexing, nested discovery, UTF-8, Chunking, and file-size answers often ranked Decision Logs or tests
   above production code. Most remained accurate, so these are primarily Source-priority issues rather than answer
   failures.
5. After reindexing, the Kafka Query retrieved Decision 0018's earlier evaluation statement that Kafka was absent.
   The answer still declined to invent configuration, but this is evaluation/corpus contamination and no longer
   exercises the zero-Source path. A separate RabbitMQ control returned zero Sources, `NO_EVIDENCE`, and skipped the
   LLM.

## Performance baseline

Across 14 Queries:

- Average retrieval and Context assembly: 140 ms
- Average qwen3:8b duration for the 13 LLM-invoked Queries: 10,990 ms
- Average end-to-end duration across all 14 Queries: 10,345 ms
- Zero-Source RabbitMQ control: 151 ms total and 0 ms LLM time

The LLM and end-to-end averages use different populations. The 10,990 ms LLM average includes only the 13 Queries
that invoked the model, while the 10,345 ms end-to-end average includes all 14 Queries, including the 151 ms
zero-Source control that skipped the LLM. Therefore the end-to-end average can be lower than the LLM-only average;
the recorded values are not a timing-order violation.

Times vary with Ollama model residency, database/filesystem cache, and local machine load.

## Phase 6 conclusion

Phase 6 should close. Major Queries are usable, no fabricated Kafka or RabbitMQ configuration was produced, all
syntactic Citations were valid, and most Citations were semantically appropriate. The single failed embedding answer
and four partial answers are concrete Phase 7+ backlog items; extending Phase 6 with new retrieval techniques would
blur the established MVP baseline.

## Backlog

- Evaluate Source-type weighting or filtering so implementation code can outrank Decision Logs, README, tests, and
  migrations when the Query asks how code works.
- Prevent evaluation documents that mention negative-control Queries from contaminating later no-evidence tests;
  maintain a held-out Query set or exclude evaluation-only documents from the evaluated corpus.
- Evaluate reranking for queries where semantically related metadata types outrank the actual service method.
- Add claim-level Source verification only if product evaluation justifies its latency and complexity.
- Keep prompt-injection defenses under adversarial evaluation; Prompt instructions are not a hard security boundary.
- Consider Hybrid Search, BM25, HNSW, conversation memory, streaming, Agent/tool use, and UI only in later phases.
