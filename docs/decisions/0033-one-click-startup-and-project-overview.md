# Decision 0033 — One-click startup and Project overview

Date: 2026-09-18

## Scope and decision

Make the installed Windows application prepare already-installed runtimes and expose cheap
Project metadata. Preserve Java 17, models, RAG/Agent prompts, retrieval and scheduler policies.
No install automation, model pull, Git push, container deletion or volume reset was added.

Window → asynchronous native worker → Docker → PostgreSQL → Ollama → model tags → Backend
→ Dashboard. Per-stage states replace percentages. Failed stages retain their reason and
later stages remain WAITING. Retry is serialized and reuses healthy runtimes.

Docker is invoked through its fixed installation CLI; Engine access uses only the local named
pipe. Background start is requested with desktop start --detach. Docker may still display its
own Dashboard or error dialog. Java/Ollama/CLI consoles are hidden.

Only local-ai-postgres with local_ai_work/postgres Compose labels may be started. MISSING uses
the bundled repository compose.yml with the same project/volume namespace, postgres only,
--no-deps and --pull never. Foreign ownership and UNHEALTHY are failures. Model tags are exact.
Java 17 is resolved from JAVA_HOME/PATH, checked, and executes only the bundled fixed JAR.
Port 18080 requires LocalRAG health identity; older LocalRAG can be identified with discovery.

Polling budgets: Docker 180 s, PostgreSQL 90 s, Ollama 60 s, models 5 s, Backend 90 s.
Each native command/request is bounded, including the output reader. An in-flight command may
extend a stage deadline by its own bounded duration. The frontend stops after 510 polls.
App closing does not stop shared runtimes. The app never kills existing services; timeout
cleanup only terminates its own short-lived command.

## Java 17 packaging defect found

The actual production resource path had a Windows verbatim prefix. Java 17 failed to open the
JAR even though it existed. Normalize local/UNC resource paths before passing them to Java.
Add a regression test. The actual bundled Backend subsequently reached READY in about 7 s.

## Dashboard and Git evidence

GET /api/workspaces/overview discovers once and executes four bulk metadata queries, then
bounded Git reads per repository. One frontend request populates all Projects and details.
There is no per-row HTTP fan-out, LLM, vector retrieval or automatic indexing.

Counts include distinct indexed paths/documents and chunks, errors, notification candidates,
and stored automation enablement. Component failures produce UNKNOWN/null and warnings.
Project list scrolls within 340 px. Detail selection uses already-returned metadata.

Push status checks commit ancestry against the configured upstream SHA; divergence uses the
same upstream. CLEAN/DIRTY is independent. Missing upstream/ref and command failure are not
treated as pushed/unpushed. References are local snapshots; no automatic fetch is performed.
Unborn branches retain the actual branch name. Hashes are 15 px monospace, copyable, with
full-SHA titles; metadata is 13–14 px and badges 12 px.

## External Docker problem and bounded recovery

The initial 2026-09-17 and post-reboot 2026-09-18 attempts failed inside Docker Desktop 4.79:
stale dockerInference / engine.sock AF_UNIX reparse points could not be removed by Docker.
LocalRAG showed FAILED after its timeout. This is not a successful unconditional cold boot.
Related upstream report: https://github.com/docker/desktop-feedback/issues/460

The user separately approved safe Docker recovery. Only processes started for this verification
were stopped; runtime socket directories were renamed for recovery, never deleted.
Backups under %LOCALAPPDATA%:

- Docker/run.localrag-recovery-20260917 (and -2, -3)
- Docker/run.localrag-recovery-20260918
- docker-secrets-engine.localrag-recovery-20260917 (and -2)
- docker-secrets-engine.localrag-recovery-20260918

No Docker data directory, container, volume or settings reset occurred. Existing older backup
directories were untouched. This repair is deliberately not part of automatic startup. An OS/
Docker fix is still needed to guarantee repeatable post-reboot startup without maintenance.

## Verification

- Backend: 180 tests / 67 suites, failures 0, errors 0, one opt-in live skip.
- Frontend: 13 tests / 8 files pass; production build pass.
- Rust: 10 tests pass: reuse, needed starts, missing model, timeout/retry, conflict, foreign
  container, exact model names, Java paths, inherited stdout timeout and API path/method checks.
- Actual production Window appears before dependencies. After socket recovery, all services
  were stopped; exe automatically prepared Docker, existing stopped PostgreSQL, Ollama, both
  models and bundled Backend. ONE_CLICK_STARTUP=PASS_AFTER_ENVIRONMENT_RECOVERY.
- Missing models / foreign port and abstract start-needed transitions are deterministic tests.
  The native MISSING PostgreSQL creation branch was implemented but not exercised by deleting
  an existing container. Existing models and unrelated ports/processes were not disturbed.
- Workspace summary: 13 Projects, 2,680 ms. Local_Ai_Work: 151 documents / 259 chunks.
- 1440×1000 actual WebView: Project list/detail, scroll 1274 px within 340 px, hash 15 px,
  no positive horizontal overflow and no console errors.
- RAG once: SUCCESS, 5 Sources, 17.7 s. Agent once: SUCCESS, getGitStatus, 28.6 s LLM.
  No retrieval/prompt/model tuning and no repeated live quality evaluation.
- Computer Use helper failed initialization; actual production WebView was exercised over
  local CDP using the developer-only desktop-smoke.mjs harness. Generated evidence is ignored
  under build/desktop-qa, not bundled.
- Production exe and NSIS installer built. Installer installation/signing remains unverified.
- Final UI verified both PUSHED (Room frontend) and UNPUSHED (Local_Ai_Work), actual inner
  scroll, and Settings Retry: Backend PID stayed 9080 and every runtime was reused.

## Runtime classification and limits

| Runtime | Classification | Responsibility |
| --- | --- | --- |
| React UI / bundled Backend JAR / startup worker | INTERNAL | Packaged application |
| Docker Desktop Engine / LocalRAG PostgreSQL / Ollama | BACKGROUND_MANAGED | Reuse/start only |
| Java 17 / WebView2 / installed Docker and Ollama / models / pgvector image | USER_PREREQUISITE | Must already be installed |

Docker socket recurrence and possible Docker UI visibility are external limitations, not hidden
successes. Existing shared services remain running after app close. Unsigned installer may
trigger SmartScreen. No push was performed; implementation and docs are committed separately.
