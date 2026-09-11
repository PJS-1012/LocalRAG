# Decision 0028: Developer workflow intelligence

## Scope and architecture

Phase 8 Steps 3-5 add three read-only workflows without adding infrastructure:

1. Similar Error Retrieval uses explicitly saved Error History, the existing
   `qwen3-embedding:0.6b` provider, 1024 dimensions, PostgreSQL and pgvector.
2. Project Progress combines bounded Git evidence, two Project Knowledge retrievals and
   unresolved Error History. The LLM writes only a redacted narrative over deterministic
   structured sections; it does not calculate a percentage or establish test status.
3. Recent Development Summary combines recent commits, current status, bounded diff summary
   and related indexed Decision Logs. Git timestamps implement optional `since`; no range
   defaults to five commits and bounded range queries inspect at most twenty.

The APIs are independently callable. The Agent Tools call these services and contain no
duplicated business logic. No arbitrary shell, write Tool, scheduler, notification, cache,
hybrid retrieval, reranker or multi-agent behavior was added.

## Similar Error Retrieval decision

Relational filters remain appropriate for exact status/type/date/file queries, but not for
phrases such as "Connection refused while connecting to PostgreSQL" versus "Could not
connect to database server". The semantic path is therefore separate from relational
History query.

`error_history_embedding` owns the vector and references Error History by a cascading
foreign key. It stores project ID, model, dimension, source fingerprint and indexed time.
The original vector is never returned. Search SQL applies project ID to both the vector row
and joined History row before cosine ranking. This phase deliberately uses sequential
pgvector search and adds no HNSW/IVFFlat index.

Embedding input is a stable labeled text:

```text
Error type: <errorType when present>
Error message: <errorMessage>
Symptom: <symptom when present>
Verified root cause: <rootCause only after explicit verification>
Verified solution: <solution only after explicit verification>
```

The unverified Agent narrative and evidence JSON are excluded. Current query fields are
redacted before embedding. Initial explicit save and VERIFIED/RESOLVED transitions refresh
the derived vector. A matching content fingerprint, model and dimension skips re-embedding;
changing any of those refreshes it. Provider failure is reported in the save result while
the relational History remains authoritative and can be retried through the idempotent save
path. Database/schema failure is not silently ignored.

The Error History-specific baseline is Top-K 5 and threshold 0.65. A single real embedding
batch measured: paraphrased PostgreSQL refusal 0.8380; PostgreSQL authentication 0.7713;
similar-looking Redis refusal 0.6095; Kafka missing topic 0.4923; NullPointerException
0.4689. Thus 0.65 retains the relevant database cases while rejecting the sampled
same-word/different-system and unrelated cases. This is a small baseline dataset, not a
universal final threshold.

All statuses can be candidates. Output labels RESOLVED as `PAST_RESOLVED_CASE`, VERIFIED
as `PAST_VERIFIED_ANALYSIS`, and UNVERIFIED as `PAST_UNVERIFIED_ANALYSIS`. Unverified cause
and solution are hidden. Every response states that similarity does not prove the current
cause and still requires separate verification.

## Progress and activity trust boundaries

Progress categories are structured and evidence-backed: recorded committed work,
uncommitted/in-progress paths, planning-document evidence, unresolved-history issues,
documentation mismatch candidates and unknowns. A commit proves recorded work, not test
success. The analysis never runs tests and explicitly reports current test state as unknown.
README-versus-Decision-Log Phase differences are reported only when both are retrieved;
source changes without a README change are a sync-review candidate, not proof of stale docs.

Project Knowledge is an indexed snapshot. Missing sources become an evidence limitation.
Commit messages, paths and retrieved text are untrusted and redacted before LLM input/output.
The LLM narrative is not persisted and is not a verified project plan. Structured evidence
remains usable if narrative generation fails.

Recent activity uses commit timestamps, with TODAY based on the host's local calendar day.
`since` results can be incomplete beyond the existing twenty-commit safety cap. Changed
areas are package/top-level groups rather than arbitrary intent. Decision Logs are optional
RAG evidence; their absence is not interpreted as absence of decisions.

## Agent routing and quality

The registered read-only Tools are `findSimilarErrors`, `analyzeProjectProgress` and
`summarizeRecentDevelopment`. Exact project matching is checked before service invocation.
Actual qwen evaluation selected each new Tool exactly once for its matching question,
selected only the existing Database Tool for DB state, and selected no Tool for HashMap.
All five child processes completed under the per-case 90-second bound.

Selection is accepted. Model prose is not fully reliable: the Similar Error no-result case
incorrectly generalized bounded absence and suggested unrelated Tools despite prompt rules;
Recent Summary emitted English from a Korean-only summary policy in one direct service run.
These are retained as LLM quality backlog. They do not change stored verification status or
the deterministic service output, and the live cases are not repeated for cosmetic tuning.

## Performance and validation

Single-run baselines on this Windows development machine:

- Similar embedding batch: 2,335.8 ms for six 1024-dimensional inputs. Integration vector
  queries were 0-2 ms with deterministic fixtures.
- Actual LocalRAG Progress: evidence 448 ms, RAG 193 ms, LLM 17,780 ms, total 18,490 ms.
- Actual Recent Summary: Git evidence 267 ms, RAG 2,881 ms, LLM 13,247 ms, total 16,421 ms.
- qwen Agent cases: Similar 53,169 ms; Progress 49,140 ms; Activity 33,091 ms;
  Database 32,332 ms; no-Tool 22,727 ms wall time including child startup/teardown.
- Final pre-commit full suite: 159 tests passed, one opt-in legacy live test skipped,
  zero failures across 56 suites.

Filesystem cache, model warm-up and machine load affect these figures. No performance index,
cache or model change is justified by this baseline alone.
