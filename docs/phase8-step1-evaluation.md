# Phase 8 Step 1 evaluation (2026-09-09)

## Single live run

Real qwen3:8b, controlled read-only Tool fixtures, unchanged retrieval baseline.
Artifacts: `build/reports/error-analysis-live/2026-09-09T07-33-11.328909600Z/`.
NPE, DB, EMPTY were each executed once; no model quality reruns.

| Case | Observed Tools | Quality | Evidence/Tool ms | RAG ms | LLM ms | Analysis ms | Case wall ms |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| NPE | getRecentErrors, searchLogs, searchProjectKnowledge | PARTIAL | 11 | 3 | 54,166 | 54,396 | 61,021 |
| DB | searchLogs, getDatabaseStatus | FAIL (prose/citations) | 7 | 0 | 54,076 | 54,295 | 60,002 |
| EMPTY | searchLogs | PASS | 9 | 0 | 38,949 | 39,155 | 44,837 |

Average evidence/Tool 9 ms; RAG 1 ms; LLM 49,063.7 ms; analysis 49,282 ms.
Fixture RAG timing is not a real vector-search benchmark. Case wall includes child
startup/teardown; analysis total does not. All completed before the 90-second timeout.
Runner marks completed cases REQUIRES_REVIEW, not automatic quality PASS. This file is
the manual semantic review of those artifacts.

## Evidence and claim review

NPE: actual fixture log reports NullPointerException at UserService.java:42; Knowledge
K1-S1 contains `String name(User user) { return user.getName(); }` (lines 40-44).
The answer identifies a possibly null user correctly, but expands to parameter omission/
initialization failure without evidence. It cites the Knowledge ID for log occurrence,
not the actual implementation claim. One Chinese word appears in otherwise Korean prose.
Selection/evidence PASS; groundedness/citation/language PARTIAL. No fabricated structured
path/hash; rootCause remains null, status UNVERIFIED.

DB: actual fixture log says Connection refused; current Database Tool returns available
JDBC and pgvector. These are distinct historical/current observations, not proof the
application DB configuration works. Model invents K1-S1 and K2-S2 citations despite no
Knowledge Tool call; existing validator reports both invalid. It speculates about host,
port, firewall and permissions and recommends tools without supporting evidence. Thus
prose/citation FAIL even though relevant Tool selection and status boundaries PASS.
No generated cause is promoted to rootCause or VERIFIED. The unverified narrative is
still visible/storable and must be reviewed; labeling does not repair its semantics.

EMPTY: searchLogs returns no entries. The application returns deterministic Korean
no-evidence text, null errorMessage/rootCause, no files/commits and UNVERIFIED. No invented
error/cause. No Knowledge IDs. PASS for no-evidence handling.

## Synthetic and database validation

Synthetic callback scenarios additionally cover Git correlation (not cause), absent
Knowledge, secret-bearing logs, fabricated model path/hash rejection, default UNVERIFIED.
Draft tests cover immutable snapshots, foreign-project lookup, eviction and expiry.
An owned 60-second sleeper is terminated after a 2-second test budget; a subsequent
child completes. This verifies timeout isolation without waiting on qwen.

Database integration verifies explicit save/redaction/idempotency, project isolation,
status transition/version control, client status-injection rejection and Flyway V3 on
PostgreSQL 17 with pgvector. The first real run exposed repeated JSONB dirty updates:
the version returned after save was 1 while the committed row was 2. JSON evidence and
path/hash collections are write-once, so mapping them as immutable (and assigning copied
lists) prevents the second update. A permanent assertion checks returned/committed version
equality before VERIFIED to RESOLVED transitions. Synthetic DB save/flush baseline: 115 ms;
transaction commit occurs outside that measurement.

Final full regression: `gradlew.bat test --no-daemon`, 149 discovered, 148 passed,
1 opt-in live test skipped, 0 failures, 46 seconds. Timeout-isolation test completed in
2.207 seconds in the earlier selected run. Final git diff check passed.

Docker recovery note: Desktop 4.79 initially crashed before WSL engine startup because
stale Windows AF_UNIX sockets (`dockerInference`, then `engine.sock`) could not be removed.
The runtime directories were moved—not deleted—to timestamped backup paths and Docker AI
was restored to its original enabled setting. Docker Engine 29.5.3 then started and the
Testcontainers suite passed. These runtime backups are outside the repository.

## Backlog and acceptance

Do not repeat live runs or tune the model to obtain all PASS. Retain unsupported cause
enumeration, invented/semantically mismatched citations, occasional language mixing and
approximately 49-second LLM latency as explicit quality backlog. Stored status is safe
but prose is not guaranteed correct. No requirement for another model in this step.

Phase 8 Step 1 implementation and automated acceptance criteria are complete. Model prose
quality remains explicitly partial and does not become VERIFIED data.
