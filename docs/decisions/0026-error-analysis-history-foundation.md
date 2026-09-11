# Decision 0026: Error Analysis and explicit Error History

## Scope and APIs

Phase 7 closed with user approval. Phase 8 Step 1 adds analysis and explicit history
persistence, not automatic repair. No write Tool, automatic commit, history embedding,
scheduler, notification, UI or multi-agent flow. No push.

Base `/api/workspaces/projects/errors`:

- POST `/analyze` with projectId/query: existing read-only callbacks, returns expiring
  analysisId and evidence. No database write.
- POST `/history` with projectId/analysisId: save server snapshot, not client evidence.
  Repeated saves return the existing record; concurrent unique-key conflict returns 409.
- GET `/history?projectId=...&page=0&size=20`: scoped pagination, size at most 100.
- PATCH `/history/{id}/status` with projectId/status/verificationNote/rootCause/solution/
  expectedVersion: explicit user attestation with optimistic concurrency control.

Existing ProjectDiscoveryService supplies canonical relative IDs and rejects traversal,
absolute paths and Containers. Same default Workspace supplier, no new root policy.
LOCAL_USER_REQUESTED and LOCAL_USER_ATTESTATION are provenance labels, not authenticated
identities. Keep these endpoints local/trusted until authentication is implemented.

## Model and evidence policy

ErrorHistory: id, analysisId, projectId, occurredAt, recordedAt, errorType, errorMessage,
symptom, rootCause, solution, status, relatedFiles, relatedCommits, evidenceSummary,
createdBy, verificationNote, verifiedBy, statusChangedAt, version. Flyway V3 creates
a relational table with JSONB evidence/path/hash collections and project/time index.
No vector data.

Initial status is always UNVERIFIED and rootCause/solution are null. Model prose is
unverified analysis inside the snapshot. VERIFIED/RESOLVED require an explicit supplied
cause and note; RESOLVED also requires a solution. This is user attestation, not automatic
proof. Only latest verification metadata is retained; append-only audit is future work.

Analysis reuses Log, Git, Knowledge and environment callbacks, selecting only relevant
Tools. Error routing preserves injection/failure/citation policies. ConfirmedEvidence
records actual Tool DTOs/timestamps, including bounded failure observations. Inference
remains unverified prose; Unknown records missing/partial evidence and cause uncertainty.
Without an observed error, deterministic no-evidence text replaces model prose.

Related files/commits come only from Tool/RAG DTOs, never model text. Unsafe paths and
cross-project DTOs are rejected. Log source path identifies evidence, not causal code.
Git chronology is not causation. occurredAt requires a parseable timezone-bearing log
timestamp, otherwise null. recordedAt is independent.

Structured status/rootCause safety is deterministic; arbitrary model prose may still
overstate a cause. ID validation is not semantic citation verification. Review remains
necessary; no claim of perfect model grounding.

## Redaction and drafts

Existing log/Knowledge redaction remains; query, model prose and evidence are sanitized
again. Persistence sanitizes the full snapshot and sensitive JSON keys. Quoted credentials,
private keys and common API-key formats extend Authorization/Cookie/password/JDBC/JWT/email
patterns. Pattern matching cannot detect every unknown secret.

Server snapshots are serialized immutable drafts in bounded memory: configurable
localrag.errors.draft-ttl=PT30M and max-drafts=100, max 512,000 serialized characters each,
up to 32 captured Tool outputs. Restart/expiry/eviction requires re-analysis; no auto-save.

## Timeout and metrics

Run `gradlew.bat errorAnalysisLiveEval`: NPE, DB, EMPTY once each. Synthetic Tool fixtures
plus real qwen3:8b evaluate selection/prose, not production fault occurrence/retrieval.
Each child JVM has 90 seconds including startup. Timeout kills only owned case processes,
records quality FAIL, writes incremental timestamped summary, and continues. Shared
Ollama/Docker are untouched; server inference may not stop immediately after client death.
The prior opt-in AgentDiagnosisLiveEvaluationTest also uses this runner. Synthetic timeout
then success verifies continuation. A successful harness exit is not model quality PASS.
This is separate from runtime Agent timeout.

Evidence time equals summed Tool time, not an additional duration. RAG time is its
Knowledge subset. LLM time is residual Agent elapsed (includes orchestration), not pure
GPU time. Analysis total excludes final draft serialization. DB save timing includes
lookup/draft sanitation/INSERT flush but not outer transaction commit. Fixture timings
are not production search benchmarks. No performance optimization.

## Validation

Results and remaining issues are recorded below after bounded evaluation. Model quality
and latency remain backlog; no repeated live evaluation for perfection.

See [evaluation](../phase8-step1-evaluation.md): NPE PARTIAL, DB prose/citation FAIL,
EMPTY PASS. All three live cases completed within 90 seconds. Mean LLM 49,063.7 ms,
analysis 49,282 ms. RootCause remained null/status UNVERIFIED in every live result.
PostgreSQL 17/pgvector Testcontainers verified Flyway V3, explicit persistence, redaction,
project isolation and state transitions. An initial integration failure found JSONB dirty
checking incrementing `version` again at commit; write-once JSON fields are now immutable
and returned/committed versions are asserted equal. Synthetic save/flush baseline: 115 ms.
Final full regression: 149 discovered, 148 passed, 1 opt-in live test skipped, 0 failures
in 46 seconds. No commit or push was performed in this step.
