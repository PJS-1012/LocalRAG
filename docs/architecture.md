# LocalRAG Architecture

## System boundary

LocalRAG is a local-first developer knowledge and workflow assistant. A configured Workspace
is the trust boundary. External callers select a Project with a Workspace-relative `projectId`;
canonical path checks, scan policy, sensitive-name filtering, file-size limits, and strict UTF-8
decoding protect every downstream read.

```text
React/Vite UI
  -> Spring Boot REST API
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
input. No client state
library or duplicate business rules were added.

The nine screens are Dashboard, RAG, Agent, Errors, Progress, Activity, Automation,
Notifications, and Settings. The Settings screen exposes availability only, never credentials
or arbitrary filesystem controls. Vite proxies `/api` to port 18080 in development.

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
search. Phase 10 deliberately does not change prompts, chunking, ranking, or model settings.

## Failure isolation and security

- A file read failure does not abort its Project; a Project scan failure does not abort its Workspace.
- Sensitive names, excluded directories, files over 5 MB, invalid UTF-8, path traversal, and link escape are rejected.
- Agent tools are read-only and their evidence is bounded and redacted.
- RAG content is untrusted evidence; citations and no-evidence behavior remain explicit.
- Automation persists bounded summaries and candidates but does not change source, Git, or service state.

## Deployment baseline and backlog

Development runs PostgreSQL with Docker Compose, Ollama as a local service, Spring Boot on
port 18080, and Vite on port 5173. Tauri packaging, a one-click launcher, streaming, Windows
notifications, and production deployment are later-phase work.
