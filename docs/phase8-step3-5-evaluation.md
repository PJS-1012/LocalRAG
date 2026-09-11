# Phase 8 Steps 3-5 evaluation (2026-09-11)

## Error similarity baseline

One real `qwen3-embedding:0.6b` batch was evaluated; no repeated threshold tuning was done.

| Comparison | Cosine similarity | Threshold 0.65 |
| --- | ---: | --- |
| PostgreSQL refusal paraphrase | 0.8380 | included |
| PostgreSQL refusal vs authentication failure | 0.7713 | included |
| PostgreSQL refusal vs Redis refusal | 0.6095 | excluded |
| PostgreSQL refusal vs Kafka missing topic | 0.4923 | excluded |
| PostgreSQL refusal vs NullPointerException | 0.4689 | excluded |

The integration scenario ranks the paraphrased PostgreSQL case first, rejects the sampled
NullPointerException, filters an identical vector belonging to another Project, exposes a
RESOLVED solution, hides unverified cause/solution, refreshes after VERIFIED, skips an
unchanged fingerprint and redacts the query before embedding.

## Actual LocalRAG service evaluation

Progress returned SUCCESS with five recorded commits, one in-progress working-tree item,
one documentation-sync candidate, one explicit unknown and zero unresolved Error History
rows. Nine evidence items were returned. At evaluation time 66 uncommitted paths were
observed. The first direct run preceded the path-evidence enhancement, so its narrative
described the committed Phase 7 baseline plus current uncommitted work rather than asserting
all Phase 8 work complete. No test state was invented.

Recent Summary returned five commits, a dirty working tree and no retrieved Decision Log
source. Its original changed-area output exposed excessive filename-level granularity; the
classifier was corrected to package-level grouping without rerunning the real model.
The model returned English despite the Korean system instruction. This is a quality backlog,
not a structural failure and was not prompt-tuned/retried.

Artifacts from the direct API run are under
`build/reports/developer-workflow-actual/` (generated, not committed).

## One-shot qwen Tool selection

Artifacts:
`build/reports/developer-workflow-live/2026-09-11T11-25-05.120656100Z/`.

| Case | Tool selection | Wall time | Result |
| --- | --- | ---: | --- |
| Similar past error | findSimilarErrors only | 53,169 ms | selection PASS; prose FAIL |
| Current progress | analyzeProjectProgress only | 49,140 ms | PASS |
| Recent work | summarizeRecentDevelopment only | 33,091 ms | PASS |
| DB running | getDatabaseStatus only | 32,332 ms | PASS |
| Java HashMap | none | 22,727 ms | PASS |

All cases completed and their child processes terminated under the 90-second limit.
The Similar fixture represented an empty bounded result. qwen incorrectly claimed this
meant the error had never occurred, speculated about other subsystems, and recommended more
Tools. The deterministic Tool selection and response boundary are correct, but the prose is
not accepted as evidence. The controlled fixture count was corrected for future runs; this
evaluation was not repeated.

## Automated regression

The complete Java 17 suite produced 56 test suites, 159 passed tests, one skipped opt-in
legacy live test and zero failures. Coverage includes Project isolation, secret redaction,
optimistic locking and audit regression, RAG, Git, Environment, Logs, Error History,
similarity indexing/search, workflow evidence and Agent callback routing.
