# 0034 — Unified Chat, safe onboarding evidence, Korean-first UX

- Date: 2026-09-22
- Status: implementation and regression validation complete; answer-quality acceptance pending
- Scope: connect existing capabilities; no push, new Agent Tool, model, retrieval algorithm or data migration.

## Problem

Users had to decide whether a natural-language question belonged to RAG, Agent, Progress or
Activity. A broad Project question in the RAG page returned zero vector hits and stopped even
though Git, README, build files and Project facts existed. English-first labels, tiny metadata
and ambiguous Git status made this look like a stopped backend or broken language understanding.
Backend READY already means usable; starting another backend cannot fix missing retrieval evidence.

## Decisions

### One main chat, existing callbacks

`POST /api/workspaces/projects/chat/unified` is a new Project-scoped entry point. It uses the
existing qwen Tool Calling registry; exact example-query matches and keyword intent chains are
not used. The actual callbacks chosen are returned as routes, with GENERAL for verified-contract
no-tool answers and UNRESOLVED for no-tool failures/withheld drafts.

Ordinary requests have no separate router LLM round-trip. Existing Agent callbacks choose
knowledge, Git, Progress, Activity, diagnosis or general knowledge. The Unified policy explains
broad question decomposition and evidence limitations, but model compliance remains imperfect.
Legacy RAG Chat, Agent, Progress, Activity and Error APIs remain available.

### Reuse without nested answer generation

- `ProjectOverviewService.facts` returns existing index/automation/error facts without running
  all environment probes or another model.
- `ProjectProgressService.collect` and `DevelopmentActivityService.collect` reuse collection
  logic without their own summary LLM. Their existing analyze/summarize paths are retained.
- `ErrorAnalysisService.reviewCollected` applies the existing analysis validation to already
  collected evidence without a second model call or automatic Error History persistence.
- Existing Similar Error/Git/Docker/DB/Ollama/Log callbacks remain available. No Tool name is added.
- Automation/History metadata is read through existing services; Unified Chat does not run
  automation, mutate source, execute repairs, or save an error diagnosis automatically.

### Bounded live Project evidence

The existing `searchProjectKnowledge` callback accepts a Unified-only ProjectBrief provider.
It combines normal RAG with safe existing-file excerpts, cached scan metadata, languages and
Project overview facts. A missing index or zero hits can therefore still produce useful evidence.
Git and Workflow answers never require vector sources.

`ProjectBriefService` uses existing discovery, scanner and DocumentReader. Candidate selection
prioritizes an explicitly named file/class, README, build file, entry point, Controller, Service,
Decision Log and Test. This is evidence-file selection, not query intent keyword routing.
One file per category is tried first, then remaining candidates.

Limits: at most 8 file attempts, 1,400 characters per excerpt, 6,000 content characters overall,
30 observed paths, 25 directory/package hints. Whole line prefixes preserve path/line references.
These excerpts supplement, rather than change, the existing RAG 8,000-character context budget.
They are samples, not a complete repository inspection. Selection may favor an alphabetically
earlier AdminController over a more central ReservationController.

Every read reapplies Workspace boundary, link, excluded/sensitive file, extension, 5 MB and strict
UTF-8 policy. Cached metadata is not authorization to read. Content is redacted before model input.
No index is created or changed by opening chat or collecting a Project brief.

### Evidence and bounded execution

Knowledge sources retain citation IDs, paths and line ranges. Sanitized callback results, names,
duration, success/failure and call sequence are exposed separately in the UI. Project evidence,
runtime evidence and workflow evidence can be inspected even when Knowledge count is zero.

Unified limits: 6 callback attempts, at most 2 knowledge calls, same-argument memoization.
Repeated calls keep trace entries while reusing results; source IDs are deduplicated.
Budget exhaustion fails explicitly instead of continuing an unbounded Tool loop.

An early handover answer invented Python/main.py without calling a Tool. Project-dependent
no-tool drafts are now withheld unless the model follows the explicit GENERAL-only contract.
An invalid no-tool draft triggers **one** additional Tool-selection attempt; repeated invalid
output becomes INSUFFICIENT_EVIDENCE. This exceptional retry is not a second router on every query.
The self-declared GENERAL marker is not independent semantic verification and is not a complete
hallucination defense.

An initial real-model broad request exceeded 120 seconds with thinking enabled. Unified calls
now use thinking OFF and `localrag.unified.max-output-tokens=1200`; the model remains qwen3:8b.
Legacy generation paths and retrieval settings are unchanged. Long responses can still hit this
output bound; streaming and conversation memory remain out of scope.

### Korean-first frontend

Main navigation is 채팅; previous Agent/Knowledge pages remain advanced views.
Dashboard, Settings, Startup, Project detail and common status labels use Korean while technical
identifiers remain unchanged. General page headings were localized; some advanced controls and
raw diagnostic evidence still use English.

Enter submits, Shift+Enter inserts a line break. IME composition, empty input and in-flight
duplicate submission are guarded across the chat forms. Project changes reset the chat result.
Answers remain single-turn; no conversation memory was added.

Working tree (변경 없음 / 수정된 파일 있음 / Git 저장소 아님) and upstream synchronization
(Push 완료 / 미Push / ahead / behind / no upstream) are displayed separately.
Modified/new/deleted counts use the same porcelain output, not another Git command.
Remote information uses local upstream refs without fetch and may be stale.

Metadata is at least 14 px; commit hashes are 15 px; section titles are 18 px. Segoe UI /
Malgun Gothic replaces the hard-to-read status typography. Lists have internal scrolling,
path wrapping and bounded evidence JSON. Commit copying keeps the full hash.

### Cheap language statistics

`ProjectMetadataService` reuses a metadata scan plan, not content/line reads. Language ratios
are **eligible source file counts**, not source LOC. The denominator and basis are explicit.
The map covers Java/Kotlin/C#/C++/C/JS/TS/Python/PHP/SQL/HTML/CSS/YAML/Rust.
Known language extensions unsupported for RAG may contribute metadata counts only; this does
not expand readable/indexable extensions. Sensitive names, excluded patterns, generated folders
and oversized files are omitted.

Cache: `localrag.overview.metadata-cache-ttl=PT5M`, maximum 64 Project snapshots, sequential
metadata collection. Root + Project type is the cache key. A warm Dashboard does not rescan
content or invoke LLM. Discovery/Git/DB metadata still cost time; cache expiration can rescan.
The Workspace summary returns all 13 Projects in one response. Detail expansion uses that
response and issues no new per-Project request.

## Validation and observed limitations

- Backend: 190 tests, 189 passed, 1 opt-in live evaluation skipped, 0 failed/errors.
- Frontend: 26 passed in 10 files. Rust: 10 passed. Java 17 maintained.
- Vite production, executable JAR, Tauri release and NSIS builds validated.
- Production WebView: 13 rows, 14 px metadata, Korean labels, correct Git separation,
  Project detail zero additional requests, Enter-to-real-Git answer, zero console errors,
  no horizontal overflow at 1440 x 1000.
- Native computer-use runtime could not initialize; used the existing developer-only release
  WebView smoke harness. This is not a new product browser/MCP integration.
- Real 15-query results: **4 PASS / 10 PARTIAL / 1 FAIL**. Full per-query evidence review and
  timings: [evaluation](../unified-chat-evaluation.md).
- Seven answers omitted Knowledge citations; IDs that did appear were valid, which does not
  prove claim-level semantic correctness.
- Broad/current-state and handover questions sometimes select Knowledge only. Progress lists
  with no recorded tasks are incorrectly summarized as no unfinished work. Commit messages are
  sometimes promoted to verified completion. DB diagnosis did not select the database Tool.
- Therefore feature integration is complete, but all answer-quality acceptance criteria are
  **not** met. No repeated model sampling is used to conceal these limitations.

## Performance baseline

13-Project overview: cold 11,183 ms client / 10,961 ms server; warm 3,128 / 3,116 ms.
RoomReservation languages: 114 recognized eligible files; Java 82.5%, JS 8.8%, YAML 6.1%, SQL 2.6%.
15-query average: Tool 849.67 ms, LLM 23,967.73 ms, total 24,817.40 ms.
Range 7.116–54.896 seconds. Cold overview remains noticeably slow.
Filesystem cache, model residency, CPU/GPU load and sampling affect subsequent runs.

## Backlog / closure

1. Enforce unknown-versus-empty Workflow semantics and reject unsupported completion claims.
2. Ensure broad/handover answers collect needed status evidence, not only architecture samples.
3. Improve claim-level citation coverage without replacing citations with decorative IDs.
4. Distinguish selected Project DB from LocalRAG infrastructure in diagnosis, and collect only
   relevant available evidence.
5. Profile cold metadata/Git collection if user-visible latency remains unacceptable.
6. Complete advanced-view localization and improve plain Markdown answer presentation separately.

No Hybrid Search, BM25, reranker, source weighting, HNSW, new model, threshold changes,
new Agent Tool, multi-agent, MCP, UI memory or streaming was introduced. Quality closure remains
pending; this decision log records the partial outcome rather than declaring all UX goals passed.
Logical local commits are allowed by the request. Push remains prohibited.
