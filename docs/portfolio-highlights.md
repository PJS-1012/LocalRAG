# LocalRAG Portfolio Highlights

All figures below come from implemented code, Decision Logs, automated tests, or bounded local
evaluation. They are not production SLOs.

## Product and architecture

1. Built the complete local knowledge path: safe file read -> structural Chunk -> Ollama
   Embedding -> pgvector -> Project-scoped retrieval -> cited answer.
2. Used deterministic Chunk IDs and transactional Project reindexing so unchanged rows stay
   untouched while stale rows are removed only inside the selected Project.
3. Added a read-only Multi-Tool Agent over bounded Git, Docker, database, Ollama and Log evidence.
4. Connected Error Analysis, persistent Error History, Verification Audit and Similar Error
   retrieval without treating model output as verified fact.
5. Added evidence-based Progress/Activity and change-aware Automation. `NO_CHANGE` skips the
   Progress and Activity model calls, avoiding roughly 34.9 seconds from their measured
   18.5-second and 16.4-second baselines.
6. Packaged the same React and Spring Boot application as a Windows Tauri Desktop app. The
   wrapper bundles a fixed Backend JAR, reuses only an identified LocalRAG listener, and exposes
   no general shell or filesystem permission.

## Retrieval decisions

- Initial threshold 0.50 dropped relevant results. A controlled rerun kept corpus, Top-K and
  Query unchanged and adopted 0.45 after recall improved while the nonexistent Kafka query
  stayed empty.
- A separate Query Instruction A/B evaluation recovered missing sensitive-file and Workspace
  boundary results while Kafka remained empty. Raw Query mode remains available.
- The 8,000-character Context budget counts headers, path, lines and content. A Chunk that does
  not fit is excluded whole instead of truncated.
- A no-result search skips qwen and returns deterministic `NO_EVIDENCE`.

## Security and failure isolation

- Project identity is a normalized Workspace-relative path, not an absolute path.
- Canonical path checks block traversal and symbolic-link escape.
- Sensitive names, generated directories, invalid UTF-8 and files above 5 MB are rejected.
- One file or Project failure does not abort the remaining batch.
- Tool arguments are scoped, command sets are fixed, outputs are bounded/redacted, and Agent
  Tools cannot mutate Git, Docker, services or source.
- The development Launcher can start only the LocalRAG postgres service and fixed local
  executables. It contains no delete, model pull, process stop or arbitrary shell operation.
- The Desktop API bridge is fixed to `127.0.0.1:18080`, `/api/**`, four HTTP methods and a 10 MB
  response ceiling. External URLs, traversal, DELETE and arbitrary command execution are denied.

## Measured release baseline

Validated on the local Windows host on 2026-09-16:

- Backend: 174 tests across 65 suites, zero failures/errors, one opt-in live test skipped.
- Frontend: 11 tests across 7 files, all passing; Rust Desktop lifecycle/security: 2 tests.
- Vite production build: 52 modules; JS 275.55 kB / gzip 83.54 kB;
  CSS 22.23 kB / gzip 5.49 kB.
- Tauri production artifacts: 11.58 MB executable and 62.47 MB unsigned NSIS installer.
- Actual production WebView smoke: 13 Projects rendered; `Local_Ai_Work` Dashboard available;
  RAG SUCCESS with 5 Sources; Agent used `getGitStatus`; Error History and Progress rendered;
  Automation Run Now returned SUCCESS with LLM skipped.
- Real RAG Project-type query: SUCCESS, 5 retrieved Sources, 3 cited/used, 0 invalid
  Citations, 17,561 ms total.
- Real Progress analysis: SUCCESS, 21,573 ms LLM and 25,508 ms total.
- Automation with model workflows disabled: SUCCESS, no LLM call, 374 ms.
- Chrome at 1440x1000: Dashboard plus eight feature screens and Similar Errors rendered with
  zero document-level horizontal overflow and zero console errors.
- Launcher normal and repeat runs reused Docker/Ollama/PostgreSQL and identified existing
  Backend/Frontend without duplicate starts.

## Honest limitations

- Vector-only ranking still allows documentation, tests or migrations to outrank implementation.
- Model latency and output quality vary; verified state never depends solely on prose quality.
- Agent Git final response capture remains PARTIAL because the external execution harness lost
  the returned JSON twice after the server request completed.
- Desktop requires external Java 17. A Tauri-started Backend is left running for safe reuse when
  the Window closes because no unauthenticated shutdown endpoint or forced kill was added.
- Native notifications, Hybrid Search, reranking, streaming and cloud deployment are not part of
  this release.
