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
discovery responses contain root metadata, only the read-only Project detail displays the root.
The UI never accepts an absolute-path input. No client state library was added.

The ten screens are Dashboard, Unified Chat, legacy RAG, legacy Agent, Errors, Progress,
Activity, Automation, Notifications, and Settings. Unified Chat is the primary entry; legacy
RAG and Agent are advanced views. The Settings screen exposes availability only, never credentials
or arbitrary filesystem controls. Vite proxies `/api` to port 18080 in browser development.
In production Tauri, the client invokes one Rust command that accepts only `/api/**`, only
GET/POST/PUT/PATCH, and always targets `127.0.0.1:18080`. External URLs, traversal, backslashes,
DELETE and responses above 10 MB are rejected.

## Desktop process boundary

The Window opens first. A native worker sequentially prepares Docker (180 s), PostgreSQL (90 s),
Ollama (60 s), required models (5 s), and Backend (90 s). Each probe/command has its own timeout;
a stage deadline can exceed its budget by at most the in-flight bounded command. The frontend
polls for at most 510 attempts and displays every stage, failure reason, and Retry Startup.
Retries are serialized; already-running services are reused.

Docker uses the installed fixed CLI and the local desktop Linux named pipe. Only the
`local-ai-postgres` container with Compose labels `local_ai_work/postgres` may be started.
When missing, the bundled repository compose file starts only `postgres` with no dependencies
or image pull. The existing Compose project/volume name is preserved. Unhealthy or foreign
containers fail explicitly. Ollama uses its known installation path and `serve`; model tags are
checked exactly, never downloaded.

The Backend health includes `application=localrag`; compatibility with older LocalRAG instances
also checks Workspace discovery. A free 18080 port starts only the bundled JAR with Java 17.
Tauri verbatim Windows resource paths are normalized for Java's JAR launcher. All child consoles
are hidden and output goes to the app log directory. Existing listeners/processes are never killed.
Timeout cleanup terminates only the orchestrator's own short-lived CLI probe, not a service.
Closing the app leaves shared runtimes available for reuse.

The API bridge runs blocking I/O off the UI thread, disables redirects/proxies, and only targets
the fixed loopback Backend. Startup has no arbitrary command/path input and is not exposed as
an Agent tool. Docker OS/socket recovery is an external maintenance action, never automatic.

## Backend overview boundary

`GET /api/workspaces/projects/overview?projectId=...` composes bounded summary data already
owned by existing services: Project metadata, Git status, vector index statistics, Error
History count, notification count, and Docker/Ollama/database availability. Individual
component failures are represented as status data so one unavailable integration does not
prevent the dashboard from rendering.

The overview endpoint does not execute mutation tools, index Projects, start services, or
invoke an LLM. Feature pages continue to call their existing Project-scoped APIs directly.

`GET /api/workspaces/overview` discovers Projects once, uses four bulk metadata queries
(index counts, error counts, notification counts, automation config), and reads bounded Git
metadata per repository. It does not return document contents or embeddings. The frontend
uses one request for the entire scrollable list; opening detail requires no further request.
Failures are represented by unknown values/warnings rather than fabricated zero counts.

Git push badges use ancestry against the configured upstream SHA; divergence uses that same
locally cached ref. No upstream, missing ref and command failures remain distinct from pushed
or unpushed. No network fetch occurs, so this is not a live remote-server assertion.

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
search. Unified Chat adds its own orchestration policy and bounded live evidence; legacy RAG
prompt, chunking, ranking and models remain unchanged.

## Unified conversation and onboarding

```text
POST /api/workspaces/projects/chat/unified (Project-relative ID)
  -> existing qwen3 Tool Calling, no separate router call
     -> existing Knowledge / Git / environment / workflow callbacks
        -> Knowledge: existing RAG + safe ProjectBrief samples + overview facts
        -> Progress / Activity: existing collectors, without nested summary LLM
        -> Diagnosis: reuse ErrorAnalysis validation on collected evidence
  -> bounded sanitized evidence / Tool trace / citations
  -> answer + warnings (or explicit insufficient evidence)
```

No exact-query intent rules or new Tool names are added. The source-file selector may match
an explicitly named identifier to an existing file, but it does not choose the conversational
route. Generic questions use the model's GENERAL contract without Tools. Unverified Project
drafts with no Tool are discarded and retried once; a second invalid draft is withheld.
No-tool failures are labeled UNRESOLVED, not GENERAL. This is a bounded safeguard, not
independent semantic hallucination verification.

Unified invocation uses thinking OFF, maximum 1,200 output tokens, at most 6 callback attempts
and 2 Knowledge calls. Duplicate arguments reuse results. The legacy Agent generation path
is retained. Progress/Activity avoid intermediate summary generations in the Unified path.

ProjectBrief reads at most 8 policy-approved files, 1,400 characters per excerpt and 6,000
characters overall; paths and line references are retained. The reader rechecks all access
policies even for cached candidates. No automatic indexing or mutation occurs.

Project language ratios reuse metadata scanning, exclude generated/sensitive/oversized files,
and count known source extensions rather than lines. A bounded 64-entry, 5-minute cache
avoids scanning on every Dashboard load. A single Workspace overview includes all Project
rows and details; expanding a row performs no further API call. Git and DB collection still
cost time on cache hits.

The UI distinguishes file/Project evidence, runtime observations and workflow history.
Citation validation checks IDs, not claim-level semantics. The real evaluation found omitted
citations and unsupported completion claims; see [evaluation](unified-chat-evaluation.md)
and [decision](decisions/0034-unified-chat-onboarding-korean-ux.md).

## Failure isolation and security

- A file read failure does not abort its Project; a Project scan failure does not abort its Workspace.
- Sensitive names, excluded directories, files over 5 MB, invalid UTF-8, path traversal, and link escape are rejected.
- Agent tools are read-only and their evidence is bounded and redacted.
- RAG content is untrusted evidence; citations and no-evidence behavior remain explicit.
- Automation persists bounded summaries and candidates but does not change source, Git, or service state.

## Development startup boundary

`dev-start.ps1` remains a developer-only startup boundary. It checks Docker readiness, starts only
the fixed `postgres` Compose service when needed, checks or starts the fixed Ollama executable,
and then starts Spring Boot on port 18080 and Vite on port 5173. It reuses identified LocalRAG
listeners and reports an occupied port without terminating its owner. Runtime logs and listener
PIDs live under the ignored `.localrag/` directory.

The Launcher never removes a container or volume, pulls a model, stops a process, or exposes
these operations through Agent Tools. The Tauri wrapper also has no shell or filesystem
permission. Its native boundaries are fixed startup orchestration/status/retry and the validated
loopback API bridge.

## Desktop release deployment

`npm run desktop:build` first creates the fixed Spring Boot executable JAR and Vite production
assets, then builds a Windows x64 executable and unsigned NSIS installer. The JAR is a read-only
bundle resource. The release depends on an external Java 17 installation; a custom jlink runtime,
code signing and auto-update remain deferred. Fixed Docker/Ollama startup is now handled by
the Desktop orchestrator; installation and general service management remain outside its scope.
