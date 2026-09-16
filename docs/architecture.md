# LocalRAG Architecture

## System boundary

LocalRAG is a local-first developer knowledge and workflow assistant. A configured Workspace
is the trust boundary. External callers select a Project with a Workspace-relative `projectId`;
canonical path checks, scan policy, sensitive-name filtering, file-size limits, and strict UTF-8
decoding protect every downstream read.

```text
Tauri Desktop Window
  -> React/Vite UI
     -> fixed loopback API bridge
        -> Spring Boot REST API (bundled executable JAR or identified existing process)
     -> Workspace discovery and safe file scan
     -> Document read -> Chunk -> Ollama embedding -> pgvector
     -> Vector retrieval -> bounded context -> Ollama chat
     -> Read-only Git/Docker/DB/Ollama/Log tools -> Agent
     -> Error History / Progress / Activity / Automation
  -> PostgreSQL + pgvector
  -> local Ollama models
```

## Frontend

The React application is a thin Project-scoped client. `App.tsx` owns navigation, Project
selection, discovery, and overview loading. Page components own feature inputs and result
presentation; API modules own HTTP contracts and normalize server errors. Although legacy
discovery responses contain root metadata, the UI neither renders it nor accepts absolute-path
input. No client state library or duplicate business rules were added.

The nine screens are Dashboard, RAG, Agent, Errors, Progress, Activity, Automation,
Notifications, and Settings. The Settings screen exposes availability only, never credentials
or arbitrary filesystem controls. Vite proxies `/api` to port 18080 in browser development.
In production Tauri, the client invokes one Rust command that accepts only `/api/**`, only
GET/POST/PUT/PATCH, and always targets `127.0.0.1:18080`. External URLs, traversal, backslashes,
DELETE and responses above 10 MB are rejected.

## Desktop process boundary

The Desktop executable checks both `/api/health` and Workspace discovery before reusing port
18080. If the port is free, it starts only the bundled `backend/localrag-backend.jar` with the
external Java 17 runtime and fixed loopback address/port arguments. It redirects stdout/stderr to
the Tauri application log directory, records the child PID, and polls readiness for at most 60
seconds. A non-LocalRAG listener becomes `PORT_IN_USE`; it is never terminated.

The React shell independently exposes `STARTING`, `READY`, `UNAVAILABLE`, and `PORT_IN_USE` and
uses a 45-second bounded retry. The app opens before Docker, Ollama, or PostgreSQL availability is
known. Those integrations remain backend status data and do not control whether the Window exists.

The current release intentionally does not call `Child::kill` or terminate another Java process.
Dropping the Desktop Window therefore leaves a Tauri-started Backend running for safe reuse. A
token-protected graceful shutdown channel is a future improvement, not an exposed actuator today.

## Backend overview boundary

`GET /api/workspaces/projects/overview?projectId=...` composes bounded summary data already
owned by existing services: Project metadata, Git status, vector index statistics, Error
History count, notification count, and Docker/Ollama/database availability. Individual
component failures are represented as status data so one unavailable integration does not
prevent the dashboard from rendering.

The overview endpoint does not execute mutation tools, index Projects, start services, or
invoke an LLM. Feature pages continue to call their existing Project-scoped APIs directly.

## Data and AI paths

```text
Knowledge path:
Project -> policy-filtered Document -> structural Chunk -> 1024-d embedding
        -> pgvector cosine search -> 8,000-character Context -> cited answer

Workflow path:
Git / Docker / Database / Ollama / Log evidence -> read-only Agent
Error -> analysis -> history -> audit -> similar-error retrieval
Project evidence -> progress/activity -> automation run -> notification candidate
```

Retrieval remains Top-K 5, threshold 0.45, Query Instruction enabled, vector-only sequential
search. Release hardening does not change prompts, chunking, ranking, or model settings.

## Failure isolation and security

- A file read failure does not abort its Project; a Project scan failure does not abort its Workspace.
- Sensitive names, excluded directories, files over 5 MB, invalid UTF-8, path traversal, and link escape are rejected.
- Agent tools are read-only and their evidence is bounded and redacted.
- RAG content is untrusted evidence; citations and no-evidence behavior remain explicit.
- Automation persists bounded summaries and candidates but does not change source, Git, or service state.

## Development startup boundary

`dev-start.ps1` is the only startup mutation boundary. It checks Docker readiness, starts only
the fixed `postgres` Compose service when needed, checks or starts the fixed Ollama executable,
and then starts Spring Boot on port 18080 and Vite on port 5173. It reuses identified LocalRAG
listeners and reports an occupied port without terminating its owner. Runtime logs and listener
PIDs live under the ignored `.localrag/` directory.

The Launcher never removes a container or volume, pulls a model, stops a process, or exposes
these operations through Agent Tools. The Tauri wrapper also has no shell or filesystem
permission. Its only native command boundaries are Backend lifecycle status and the validated
loopback API bridge.

## Desktop release deployment

`npm run desktop:build` first creates the fixed Spring Boot executable JAR and Vite production
assets, then builds a Windows x64 executable and unsigned NSIS installer. The JAR is a read-only
bundle resource. The release depends on an external Java 17 installation; a custom jlink runtime,
code signing, auto-update, and Docker/Ollama control are intentionally outside Phase 12.
