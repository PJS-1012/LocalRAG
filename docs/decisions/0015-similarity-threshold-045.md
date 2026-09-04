# Decision 0015: Adopt similarity threshold 0.45

## Status

Accepted for Phase 6 Step 4.1. Top-K remains 5 and the default similarity threshold changes from 0.50 to 0.45.

## Controlled evaluation

The same ten Queries and the same 208-Chunk `Local_Ai_Work` index from Step 4 were used. Query instructions,
rewriting, chunking, ranking, and the corpus were unchanged, so this comparison isolates the threshold effect.

| Query | Results at 0.50 | Results at 0.45 | Relevant recovery | New irrelevant result |
|---|---:|---:|---|---|
| 프로젝트 타입을 탐지하는 코드는 어디에 있나? | 5 | 5 | Not needed; ranking retained | None new |
| 민감 파일을 어떻게 제외하나? | 0 | 0 | No | None |
| 문서를 어떤 기준으로 Chunk로 나누나? | 5 | 5 | Not needed; ranking retained | None new |
| 텍스트를 Vector로 변환하는 코드는 어디에 있나? | 2 | 5 | Yes; `EmbeddingService.java` at 0.474 | No material new false positive |
| 동일 프로젝트 재인덱싱 시 중복 저장을 어떻게 막나? | 0 | 2 | Yes; decision and repository test at 0.464/0.452 | None |
| Unity 캐시 폴더가 검색 대상에 안 들어가게 하는 부분 | 3 | 5 | Scanner test recovered at 0.485 | One unrelated `StoredChunkMatch.java` at 0.494 |
| Kafka consumer 설정은 어디에 있나? | 0 | 0 | Not applicable | None |
| Workspace 밖으로 나가는 경로 접근은 어떻게 차단하나? | 0 | 0 | No | None |
| 잘못된 UTF-8 파일 하나가 전체 읽기를 중단하지 않게 하는 코드는? | 5 | 5 | Not needed; ranking retained | None new |
| 중첩 폴더에서 실제 프로젝트 루트를 어떻게 찾나? | 2 | 5 | Yes; discovery service and decisions at 0.477-0.485 | None |

Top-1 and diagnostic Top-5 similarity values are unchanged from Decision 0014 because only the filter threshold
changed. Across the ten Queries, Top-1 ranged from 0.343 to 0.637 and diagnostic Top-5 from 0.315 to 0.562.

## Decision

Adopt 0.45 as the new retrieval baseline:

- Relevant implementation or evidence was recovered for Embedding, reindexing, Unity exclusion, and nested discovery.
- Queries already working at 0.50 retained their ranking and Top-K quality.
- The nonexistent Kafka Query remained empty; its observed maximum similarity was 0.383.
- Only one clear new irrelevant result was observed, within a five-result Unity Query that still contained relevant
  results.
- Sensitive-file filtering and Workspace-boundary Queries still return no result. Their relevant scores overlap the
  absent-feature distribution, so lowering the global threshold further would trade precision for uncertain recall.

## Consequence

Threshold adjustment improved recall but did not solve all semantic misses. Do not lower below 0.45 based on this
sample. The next isolated experiment should keep Top-K 5 and threshold 0.45 while testing a Query instruction; no
instruction is applied in this decision.
