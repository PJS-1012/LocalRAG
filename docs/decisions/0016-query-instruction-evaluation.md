# Decision 0016: Query instruction evaluation

## Status

Evaluated for Phase 6 Step 4.2. The instruction is recommended for adoption, but this evaluation does not enable it
in the search service.

## Instruction

```text
Instruct: Retrieve the most relevant source code or project documentation for the software project query.
Query: <USER_QUERY>
```

The instruction is one English sentence, is applied only to Queries, and leaves stored Document embeddings unchanged.
This follows Qwen's documented `Instruct: <task>\nQuery:<query>` query-side format and its recommendation to tailor an
English instruction to the retrieval task.

Reference: https://huggingface.co/Qwen/Qwen3-Embedding-0.6B

## Controlled evaluation

The same ten Queries, 208-Chunk corpus, Top-K 5, threshold 0.45, chunking, and ranking were retained. Only the text
sent for Query embedding changed.

| Query | Raw Top-1 | Instruct Top-1 | Raw / Instruct count | Relevant rank change | Judgment |
|---|---:|---:|---:|---|---|
| 프로젝트 타입을 탐지하는 코드는 어디에 있나? | 0.635 | 0.657 | 5 / 5 | detector 3 → 2 | Improved |
| 민감 파일을 어떻게 제외하나? | none | 0.520 | 0 / 5 | scanner absent → 1; policy absent → 4/5 | Strong recovery |
| 문서를 어떤 기준으로 Chunk로 나누나? | 0.623 | 0.724 | 5 / 5 | `DocumentChunker` 1 → 3 | Ranking regression |
| 텍스트를 Vector로 변환하는 코드는 어디에 있나? | 0.594 | 0.579 | 5 / 5 | `EmbeddingService` 5 → 4 | Small improvement; migration remains first |
| 동일 프로젝트 재인덱싱 시 중복 저장을 어떻게 막나? | 0.464 | 0.557 | 2 / 4 | relevant decision/test remain 1/2 | Recall improved |
| Unity 캐시 폴더가 검색 대상에 안 들어가게 하는 부분 | 0.521 | 0.560 | 5 / 5 | scanner test 5 → 3 | Improved; prior unrelated match removed |
| Kafka consumer 설정은 어디에 있나? | none | none | 0 / 0 | no relevant result | Precision retained |
| Workspace 밖으로 나가는 경로 접근은 어떻게 차단하나? | none | 0.544 | 0 / 5 | security decision/exception absent → 1/2 | Strong recovery |
| 잘못된 UTF-8 파일 하나가 전체 읽기를 중단하지 않게 하는 코드는? | 0.635 | 0.590 | 5 / 5 | relevant top ranks retained | Stable despite lower scores |
| 중첩 폴더에서 실제 프로젝트 루트를 어떻게 찾나? | 0.541 | 0.617 | 5 / 5 | discovery service 5 → outside Top-5 | Implementation-rank regression |

## Findings

- Both previously missing Queries were recovered with clearly relevant results.
- The nonexistent Kafka Query remained empty, so the instruction did not merely lift every Query over threshold.
- Production-code priority improved for Project type and Embedding, and a prior irrelevant Unity match disappeared.
- The instruction did not fix the vector migration false positive for Text-to-Vector.
- It moved Chunking documentation above implementation and removed `ProjectDiscoveryService` from nested-discovery
  Top-5. Search quality therefore improved overall but not uniformly.
- Similarity scores rose for most Queries but fell for Text-to-Vector and UTF-8. Adoption is based on ranking and
  recall, not score inflation.

## Recommendation

Adopt this single instruction as a configurable Query-side default in the next implementation step while retaining
Top-K 5 and threshold 0.45. Keep a raw-query switch for controlled evaluation and rollback. Do not adjust the
threshold or Document embeddings at the same time.
