# Phase 10 Portfolio Metrics

Measured on the local Windows development machine on 2026-09-15. These values are regression
and portfolio baselines, not production SLOs; filesystem cache, model warm-up, Docker state,
and machine load affect timings.

## Delivered surface

- 9 Project-scoped UI screens
- 1 bounded backend overview endpoint
- 7 frontend tests across 6 test files
- 173 backend tests across 64 suites: 0 failures, 0 errors, 1 opt-in live test skipped
- React 19, TypeScript 5.9, Vite 7; Java 17 and Spring Boot 3.5.16 retained

## Build baseline

Production build compiled 49 modules in 0.85 seconds:

- HTML: 0.45 kB, gzip 0.29 kB
- CSS: 22.10 kB, gzip 5.45 kB
- JavaScript: 272.56 kB, gzip 82.39 kB

The full backend regression suite completed in 1 minute 15 seconds with Docker-backed
integration tests available.

## Live smoke baseline

The Vite page returned HTTP 200 in 346 ms. Its `/api` proxy returned the Project overview in
4,031 ms wall time; the overview service reported 837 ms. The final overview showed Git,
Docker, Ollama, and PostgreSQL available and 259 indexed Chunks for `Local_Ai_Work`.

Representative real-model requests:

| Flow | Result | Retrieval / tool | LLM | Total |
| --- | --- | ---: | ---: | ---: |
| RAG Project-type question | SUCCESS, 5 sources, 2 used | 2,777 ms | 13,708 ms | 16,486 ms |
| Agent Git-status question, first attempt | INSUFFICIENT_EVIDENCE | 138 ms | 29,740 ms | 29,945 ms |
| Progress analysis | SUCCESS | 464 ms non-LLM work | 16,186 ms | 16,650 ms |

The Agent first attempt found `getGitStatus` but rejected its oversized evidence because
`frontend/node_modules` was not ignored. Adding `frontend/.gitignore` restored the overview
Git component to `SUCCESS`. This was an evidence-boundary failure, not an LLM tuning issue.

Automation Run Now completed in 413 ms with LLM invocation disabled for the smoke fixture.
Run History returned one row and Notification Candidates returned one row. Error History
returned a successful empty list; Similar Error Retrieval also succeeded with zero matches.

## Validation notes

Browser automation could not complete because the workspace refresh helper repeatedly failed
while loading the browser skill. The bounded fallback used real backend endpoints, the Vite
HTTP server and proxy, plus jsdom user-flow tests. It verified the requested feature routes and
data contracts, but it is not a pixel-level or native-browser interaction certification.

`npm audit` reports two moderate development-only advisories through the Vitest toolchain.
The suggested automatic fix crosses a major Vitest version, so it is recorded for a deliberate
dependency upgrade rather than mixed into Phase 10 feature completion.
