# Decision 0032 — Tauri Desktop Packaging

Date: 2026-09-16

## Problem

The Phase 11 release required a browser plus a PowerShell launcher. Phase 12 needed a Windows
Desktop Window without changing the RAG, Agent, Error, Workflow, or Automation feature semantics.

## Decision

Reuse the existing React/Vite frontend and Spring Boot Backend. Tauri 2 owns the native Window,
packages Vite assets, bundles a fixed `localrag-backend.jar`, and uses the external Java 17 runtime.
No jlink runtime is bundled in this phase.

Backend startup uses a constrained candidate-B design:

1. Probe `127.0.0.1:18080` with both LocalRAG health and Workspace discovery.
2. Reuse only an identified LocalRAG process.
3. If the port is free, run only `java -jar <bundled-resource>` with fixed address/port arguments.
4. If another listener owns the port, report `PORT_IN_USE` and do not terminate it.
5. Record the child PID, redirect logs to the Tauri app log directory, and poll for at most 60 seconds.

The frontend exposes `STARTING`, `READY`, `UNAVAILABLE`, and `PORT_IN_USE` and independently stops
retrying after 45 seconds. Production API calls use a Rust bridge fixed to the loopback Backend.
The bridge allows only `/api/**`, GET/POST/PUT/PATCH, UTF-8 responses up to 10 MB, and a 120-second
request timeout. It rejects external URLs, traversal, backslashes, and DELETE.

## Security boundary

- Tauri capability: `core:default` only.
- No shell plugin or shell permission.
- No filesystem plugin or filesystem permission.
- No remote arbitrary URL navigation.
- CSP permits bundled assets, Tauri IPC, and the fixed loopback Backend only.
- Spring CORS permits only `http://tauri.localhost`; wildcard CORS is not used.
- The Agent remains read-only and receives no Desktop mutation tool.

## Lifecycle trade-off

The app never calls `Child::kill`, `taskkill`, or any other force-stop path. The current Backend has
no safe authenticated graceful-shutdown contract. Therefore a Backend started by Tauri can remain
running after the Window closes and is reused by the next launch. A protected graceful shutdown
channel is backlog; exposing Actuator shutdown was rejected because it would weaken the local API
boundary.

## Validation

- Rust 1.98.1 / MSVC target, C++ Build Tools, and WebView2 were present.
- `cargo check` and 2 Rust lifecycle/security tests passed.
- 174 Backend tests passed; 1 opt-in live test skipped.
- 11 Frontend tests passed; Vite production build passed.
- Tauri produced an 11.49 MB executable and a 62.46 MB unsigned NSIS installer.
- An actual production `LocalRAG` Window loaded 13 Projects and selected `Local_Ai_Work`.
- RAG returned SUCCESS with 5 Sources; Agent used `getGitStatus`; Error History and Progress loaded;
  Automation Run Now returned SUCCESS with LLM skipped.

The Windows Computer Use helper failed during workspace refresh. Production UI verification used
the real WebView2 target through a localhost-only DevTools endpoint for this test run; the release
does not enable that endpoint by default.

## Known limitations

- External Java 17 remains required.
- Docker, PostgreSQL, Ollama, and models are not bundled or broadly controlled by Tauri.
- The NSIS installer is unsigned and may trigger SmartScreen.
- The automatic bundled-JAR start path was compile/unit-tested but the final smoke reused the
  already running identified LocalRAG Backend, preserving the no-termination policy.
- Service-down scenarios were not induced by stopping working local services; bounded failure
  behavior is covered by deterministic frontend/Rust tests.
- Standard `desktop:dev` uses strict port 5173; the running Phase 11 Vite instance prevented a
  second concurrent dev server during final verification.

## Outcome

Phase 12 produces a one-click Windows Desktop artifact while preserving the existing Backend and
all AI baselines. Code signing, bundled Java runtime, graceful child shutdown, optional startup
buttons, native notifications, and auto-update remain future work.
