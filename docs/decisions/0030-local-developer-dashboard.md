# Decision 0030: Local developer dashboard

## Decision

Phase 10 adds a React 19, TypeScript and Vite web UI over the existing Project-scoped Spring
Boot APIs. A small overview API composes existing read-only summaries for the dashboard.
Business rules, Workspace path enforcement, tool execution, RAG, and automation remain in
the backend. Tauri packaging and a one-click launcher are deferred because the current host
does not provide the required desktop toolchain and the web baseline is independently useful.

## UI scope

The application covers Dashboard, RAG, Agent, Error History and Similar Error, Progress,
Recent Activity, Automation and Run History, Notification Candidates, and Settings. Project
selection uses relative `projectId` values. The UI exposes no absolute-path entry, secret,
arbitrary command, Git mutation, service start/stop, or source modification control.

The frontend uses local React state and dedicated API modules. This is sufficient for the
current single-user dashboard and avoids introducing a state framework before shared client
state becomes complex.

## Overview API

The Project overview endpoint reuses discovery, Git, index, Error History, notification, and
environment status services. Partial integration failure becomes component status instead of
an endpoint-wide exception. The endpoint never performs indexing or invokes a model.

## Validation

- Frontend: 7 tests in 6 files, all passing.
- Production build: 49 modules; 272.56 kB JavaScript and 22.10 kB CSS before gzip.
- Backend: 173 tests, zero failures/errors, one opt-in live test skipped.
- Live flows: Project selection/overview, RAG, Agent tool selection, Error History, Similar
  Error, Progress, Automation Run Now, Run History, and Notification Candidate exercised.
- Vite page and Spring API proxy returned HTTP 200.

The first Agent Git-status smoke exceeded the bounded evidence limit because unignored
frontend dependencies flooded working-tree output. A frontend `.gitignore` fixed the cause;
the composed Git overview then returned `SUCCESS`. No prompt or retrieval tuning was used.

Browser-skill startup was blocked by a workspace refresh helper error. Real API/proxy smoke
and jsdom interaction tests provided the fallback. Native browser interaction and visual QA
remain an explicit limitation rather than an implied pass.

## Portfolio baseline

The dashboard demonstrates end-to-end productization of the existing ingestion, RAG,
read-only Agent, Error Intelligence, Progress/Activity, and Automation layers without
weakening their Project boundary. Detailed timings and bundle metrics are stored in
`docs/phase10-portfolio-metrics.md`.

## Backlog

- Tauri/desktop packaging and a one-click local launcher.
- Native-browser visual and accessibility QA.
- Deliberate Vitest major upgrade for two moderate development-only advisories.
- Agent source-output usability improvements that preserve bounded evidence.
- Production SLO measurements and packaged deployment validation.
