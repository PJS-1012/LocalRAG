# Decision 0027: Error History query and verification audit

## Scope

Phase 8 Step 2 completes relational History query and state-change traceability. It does
not add Error embeddings, semantic/vector search, automatic storage, automated solution
recommendation, scheduling, notification, progress tracking, multi-agent behavior or UI.

## Query APIs and boundaries

- `GET /api/workspaces/projects/errors/history/{id}?projectId=...` resolves the canonical
  Project and uses `id + projectId`; absent or foreign-Project IDs return 404. The detail
  contains the stored evidence snapshot, current state and ordered Verification Audit.
- `GET /api/workspaces/projects/errors/history` remains Project-scoped and accepts status,
  errorType, errorMessage, occurredFrom/To, recordedFrom/To, relatedFile, relatedCommit,
  page and size. Size is 1..100 and default ordering is recordedAt DESC then id DESC.

Filtering uses parameterized PostgreSQL SQL through Spring Data JPA. errorType is an
exact lower/trim normalized match. errorMessage is a case-insensitive literal substring;
LIKE wildcards are escaped. Date endpoints are inclusive. Files and commits are exact
JSONB array elements; file separators normalize to `/`, unsafe traversal/absolute forms
are rejected, and commit input must be 7..40 hexadecimal characters.

`Page` was selected instead of `Slice`: the expected management view needs total elements
and total pages. This intentionally permits a count query. Current dataset is small; key
status/type/date and JSONB filters have indexes but no cache or premature optimization.

## Audit and N+1 policy

`error_history_verification` is append-only for this phase and stores history ID,
from/to status, redacted root cause/solution/note, actor provenance, changedAt and the
previous optimistic-lock version. It has a foreign key to ErrorHistory and a chronological
index. ErrorHistory has no ORM collection relation to Audit. List queries therefore fetch
only History rows and cannot produce per-row Audit queries; only detail executes one
separate ordered Audit query.

`LOCAL_USER_ATTESTATION` is still a local provenance label, not authenticated identity.
An authenticated actor ID and append-only authorization policy remain future requirements.

## State machine and transaction

Only `UNVERIFIED -> VERIFIED -> RESOLVED` is allowed. Direct UNVERIFIED -> RESOLVED,
same-state, reverse transitions and any transition from terminal RESOLVED are rejected.
VERIFIED requires rootCause and verificationNote. RESOLVED requires rootCause, solution
and verificationNote. All user fields are redacted again at the storage boundary.

The current row is checked against expectedVersion, updated/flushed, and its Audit row is
inserted/flushed within the same transaction. A competing stale update raises 409 and the
transaction creates no Audit. The audit records previousVersion; the current row exposes
the new version. JSONB evidence remains immutable after initial persistence.

## Performance definition

List and detail response metrics measure service/repository work before transaction commit.
Status duration covers project validation, row lock/version check, current-row flush and
Audit flush; auditSaveDuration is its nested Audit insert/flush portion. These local
Testcontainers figures are baselines, not production latency guarantees.

## Validation

PostgreSQL integration covers same/foreign Project detail, missing IDs, status/type/message/
inclusive-date/file/commit filters, literal wildcard escaping, Page total/order, both valid
transitions, direct/reverse transition rejection, stale-version 409 semantics, no duplicate
Audit and Secret redaction in current and historical values. Final measurements:

- List query: 7 ms
- Detail query including ordered Audit: 11 ms
- Status change including both flushes: 5 ms
- Audit insert/flush subset: 1 ms
- Initial History save/flush regression baseline: 118 ms
- Full suite: 152 discovered, 151 passed, one opt-in live test skipped, zero failures;
  `gradlew.bat test --no-daemon` completed in 38 seconds on Java 17.

These are local Testcontainers single-run baselines and include framework warm-up variance.
No qwen live quality evaluation was repeated because this relational query/Audit step does
not alter Agent analysis or model behavior. No commit or push was performed.
