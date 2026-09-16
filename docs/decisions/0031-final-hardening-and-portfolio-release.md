# Decision 0031: Final hardening and portfolio release

## Release strategy

Phase 11 freezes the implemented RAG, Agent, Error and Automation behavior. Release work is
limited to development startup, bounded validation and portfolio documentation. Prompts,
models, retrieval parameters and backend feature boundaries remain unchanged.

The release target is the local React/Vite web application plus `dev-start.ps1`. Tauri is
deferred because Cargo is unavailable on the host and installing a desktop toolchain would
increase release risk without improving the core demonstration.

## Launcher boundary

The Launcher follows a fixed sequence: Docker readiness -> LocalRAG postgres -> Ollama and
required-model check -> Backend -> Frontend -> optional browser open. Existing identified
instances are reused. An unidentified listener on 18080 or 5173 returns `PORT_IN_USE` with
owner details. Started/reused listener PIDs and logs are stored under ignored `.localrag/`.

Only `docker compose --file <repository compose.yml> up --detach postgres` is available.
There is no remove/down/volume-delete, model pull, process stop, arbitrary command or Agent
Tool integration. Failure codes identify Docker, PostgreSQL, Ollama/model, Backend, Frontend
and port stages without default stack traces or secrets.

## Visual and E2E validation

The gstack browse runtime was absent and unified browser control failed twice with the existing
Windows workspace refresh error. The bounded fallback used installed Chrome via DevTools
Protocol. It rendered all nine screens and the Similar Errors tab at 1440x1000 with
`Local_Ai_Work` selected. Document-level horizontal overflow, unintended out-of-viewport
elements and console errors were zero. Empty, disabled and status-badge states were present.

Final RAG returned SUCCESS with 5 Sources, 3 used and 0 invalid Citations in 17,561 ms.
Progress returned SUCCESS in 25,508 ms. Error/Similar empty states succeeded. An Automation
run with all model workflows disabled finished in 374 ms and produced history/notification
rows. Agent Git was invoked once, but the external command harness again returned no captured
JSON after completion; it remains PARTIAL and does not block release.

## Dependency and release decision

`npm audit --audit-level=high` reported no High/Critical issue and retained two moderate
Vitest toolchain advisories whose fix requires a breaking major upgrade. Gradle runtime
dependencies resolved successfully; no backend CVE scanner is configured, so the release
does not claim a complete backend vulnerability audit.

The project is release-ready as a local portfolio application with documented limits:
desktop-first 720px minimum layout, variable local-model latency, vector-only ranking,
Agent capture PARTIAL, Tauri deferred, no native notifications, and no service stop command.
