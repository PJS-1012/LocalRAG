# Decision 0014: Project cosine similarity search baseline

## Status

Accepted for Phase 6 Step 4. Threshold 0.50 remains the implemented initial baseline; evaluation recommends testing
0.45 next rather than treating either value as final.

## Decision

- Embed the user Query with the configured Ollama embedding model and validate it against the configured 1024
  dimensions before querying PostgreSQL.
- Restrict every SQL query with `project_id = ?`.
- Use pgvector cosine distance (`<=>`) and expose `1 - cosine_distance` as similarity.
- Apply threshold filtering, similarity-descending ordering, and Top-K limiting in PostgreSQL.
- Default to Top-K 5 and threshold 0.50 through configuration, with a maximum accepted Top-K of 20.
- Return Chunk content and citation-ready source metadata, never the vector.
- Keep sequential exact search. With 208 stored Chunks, HNSW and IVFFlat are not justified yet.

## Retrieval evaluation

The Project was reindexed before evaluation: 125 Documents, 208 Chunks, 208 stored, 1024 dimensions. Ten Queries
were executed at threshold 0.50. A diagnostic threshold 0.0 run captured the unfiltered Top-5 distribution. Small
differences between repeated scores are expected because each run requests a new Query embedding.

| # | Query | Diagnostic Top-1 / Top-5 | Top result and judgment | At 0.50 |
|---|---|---:|---|---:|
| 1 | 프로젝트 타입을 탐지하는 코드는 어디에 있나? | 0.637 / 0.552 | `ProjectType.java`, relevant; detector ranked third | 5 |
| 2 | 민감 파일을 어떻게 제외하나? | 0.379 / 0.332 | `ProjectFileScanner.java`, relevant | 0 |
| 3 | 문서를 어떤 기준으로 Chunk로 나누나? | 0.625 / 0.562 | `DocumentChunker.java`, relevant | 5 |
| 4 | 텍스트를 Vector로 변환하는 코드는 어디에 있나? | 0.593 / 0.474 | vector migration was irrelevant Top-1; relevant `EmbeddingService.java` ranked fifth at 0.474 | 2 |
| 5 | 동일 프로젝트 재인덱싱 시 중복 저장을 어떻게 막나? | 0.464 / 0.419 | Decision 0013, relevant | 0 |
| 6 | Unity 캐시 폴더가 검색 대상에 안 들어가게 하는 부분 | 0.521 / 0.485 | workspace scan decision, relevant semantic match | 3 |
| 7 | Kafka consumer 설정은 어디에 있나? | 0.383 / 0.348 | no matching feature; all results irrelevant | 0 |
| 8 | Workspace 밖으로 나가는 경로 접근은 어떻게 차단하나? | 0.343 / 0.315 | Phase 5 security decision, relevant but weak | 0 |
| 9 | 잘못된 UTF-8 파일 하나가 전체 읽기를 중단하지 않게 하는 코드는? | 0.635 / 0.530 | `ProjectDocumentReaderTest.java`, relevant | 5 |
| 10 | 중첩 폴더에서 실제 프로젝트 루트를 어떻게 찾나? | 0.542 / 0.477 | depth-one discovery decision, relevant | 2 |

### Threshold findings

- False negatives at 0.50: sensitive-file filtering, reindex synchronization, Workspace boundary protection, the
  actual Embedding service, and lower-ranked nested discovery implementation.
- False positives at 0.50: the vector-extension migration outranked text-to-vector implementation; a controller test
  containing the evaluation wording also passed for Project detection.
- The nonexistent Kafka Query peaked at 0.383 and was fully filtered.
- The reindex and Embedding implementation matches fall between 0.45 and 0.50. Lowering the next evaluation baseline
  to 0.45 should recover them while still filtering the observed Kafka results.
- Lowering near 0.38 is not recommended: relevant sensitive-file results and the nonexistent Kafka distribution
  overlap there. Threshold alone cannot resolve that ambiguity.
- Recommendation: retain Top-K 5, test threshold 0.45 next, and separately evaluate query instructions and corpus
  effects before considering a lower global threshold. Do not change the configured 0.50 without approval.

## Performance baseline

Across ten threshold-0.50 searches after warm-up:

- Query embedding: 27-39 ms, average 30.6 ms.
- DB similarity search: 2-25 ms, average 12.9 ms.
- Total: 31-63 ms, average 45.2 ms.
- Required Project-type Query: 41 ms total and five results.

These values vary with Ollama warm-up, filesystem and database cache, and machine load.

## Failure behavior

Blank or invalid requests, missing Project indexes, provider/model failures, dimension mismatches, and database
failures have distinct response statuses. A valid search with no result above threshold returns `SUCCESS` with an
empty result list.
