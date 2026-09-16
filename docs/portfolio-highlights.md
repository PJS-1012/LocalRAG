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

## Measured release baseline

Validated on the local Windows host on 2026-09-16:

- Backend: 173 tests across 64 suites, zero failures/errors, one opt-in live test skipped.
- Frontend: 7 tests across 6 files, all passing.
- Vite production build: 49 modules in 0.83 seconds; JS 272.58 kB / gzip 82.41 kB;
  CSS 22.10 kB / gzip 5.45 kB.
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
- Tauri, native notifications, Hybrid Search, reranking, streaming and cloud deployment are not
  part of this release.
