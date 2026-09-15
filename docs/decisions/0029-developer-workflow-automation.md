# Decision 0029: Developer workflow automation

## Scope and architecture

Phase 9 adds persistent, read-only automation around the Phase 8 services:

```text
Project configuration -> due-project poller -> local per-project lock
  -> cheap project/error/environment evidence
  -> changed: existing Progress and Activity services
  -> run history and notification candidates
  -> unchanged: NO_CHANGE without an LLM call
```

Manual and scheduled execution share `AutomationExecutionService`. Automation never edits
code, executes arbitrary shell, writes Git state, starts or stops Docker/Ollama, or promotes
an LLM answer to verified Error History.

## Configuration and scheduler

Project configuration is stored in PostgreSQL. The baseline schedule is an interval rather
than cron: 60 seconds minimum, seven days maximum. A Spring fixed-delay poller wakes every
30 seconds and selects enabled rows whose `nextRunAt` is due. Runs are sequential and a
Project failure is caught so the next Project continues.

No Redis, queue or distributed lock is added. A process-local concurrent set prevents two
runs for the same Project. This is sufficient for one LocalRAG process and must be revisited
for multi-instance execution.

## Change detection and LLM economy

The Project fingerprint combines Git HEAD, a sorted hash of working-tree status and diff
summary, Error History counts plus latest row ID/version/time, and Project Index metadata.
Decision Log changes are therefore covered through Git or index evidence without a separate
filesystem scan. All paths still pass exact Project scope.

The first run establishes fingerprints. Identical later evidence returns `NO_CHANGE` and
does not invoke Progress or Activity. When evidence changes, the existing services run
independently; failure in one does not discard the other's result.

## Error and environment watch

Error Watch reuses the bounded Log Tool and redacts fields. Its fingerprint uses source,
timestamp, line and normalized message; uniqueness is Project plus fingerprint. A duplicate
skips persistence and Similar Error Retrieval. A new event invokes Similar Error once and
stores only reference metadata. It never creates Error History or applies an old solution.

Environment Watch reuses Docker, Project container, Ollama and Database status services.
Unavailable components become evidence and notification candidates. Watchers never start
or stop a component.

## History, notifications, and API

Flyway V6 adds `project_automation_config`, `automation_run`, `detected_error_event`, and
`notification_candidate`. Run details contain bounded statuses, counts and timings, not
prompts or raw logs. Candidate types are `ERROR_DETECTED`, `ENVIRONMENT_FAILURE`,
`PROJECT_CHANGED`, and `PROGRESS_UPDATED`; Project/type/fingerprint uniqueness makes them
idempotent. Delivery is outside Phase 9.

The API surface is:

- `GET|PUT /api/workspaces/projects/automation`
- `POST /api/workspaces/projects/automation/run`
- `GET /api/workspaces/projects/automation/runs`
- `GET /api/workspaces/projects/automation/notifications`

Manual runs remain available when scheduling is disabled. API requests and query paths
remain Project scoped.

## Failure semantics

Statuses are `SUCCESS`, `PARTIAL_SUCCESS`, `NO_CHANGE`, `FAILED`, `DISABLED`, and
`ALREADY_RUNNING`. Fingerprint collection failure fails the run. Error Watch, Environment
Watch, Progress and Activity failures are isolated as partial success. Exception details
are neither returned nor persisted.

## Performance and validation

Final local automated baselines on this Windows development machine:

- no-change branch: 2 ms in the isolated service test, zero Progress/Activity calls;
- changed branch excluding mocked LLM work: 2 ms;
- fingerprint fixture: 80 ms for three captures;
- new plus duplicate Error Watch fixture: 43 ms;
- Environment Watch fixture: 3 ms;
- config, run and notification DB saves: 69 ms against Testcontainers PostgreSQL;
- full suite: 169 tests, zero failures, one opt-in live test skipped, 62 suites in 50 s.

These are regression guardrails, not production latency claims. Cache, container startup,
model warm-up and machine load affect them. Phase 8 real qwen baselines remain about 18.5 s
for Progress and 16.4 s for Activity, so skipping both dominates `NO_CHANGE` savings. No new
live qwen run was made because Phase 9 changes neither prompt nor model workflow.

## Backlog

- Multi-instance operation needs a database/distributed lease.
- Notification delivery, acknowledgement, retention and Windows toast belong to a later phase.
- A future UI may require cron/calendar schedules; interval scheduling is the current baseline.
- Environment recovery notifications may later be separated from failure notifications.
- Packaged desktop operation should supply production filesystem and scheduling SLO samples.
