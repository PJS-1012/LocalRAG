# Phase 11 Release Checklist

Validated on 2026-09-16.

| Item | Result | Evidence |
| --- | --- | --- |
| Windows Launcher | PASS | normal start, stopped postgres recovery, repeat-run reuse |
| Docker/Ollama/PostgreSQL UX | PASS | fixed targets, explicit failure codes, required models present |
| Backend tests | PASS | 173 tests, 0 failures/errors, 1 live skip |
| Frontend tests | PASS | 7 tests in 6 files |
| Production build | PASS | Vite 49 modules, JS/CSS bundle recorded |
| Flyway | PASS | V1–V6 validated, schema version 6 |
| Chrome desktop visual QA | PASS | 9 screens + Similar tab, no console/overflow issue at 1440px |
| RAG E2E | PASS | 5 Sources, 3 used, 0 invalid Citations |
| Agent Git E2E | PARTIAL | request completed; external runner did not return response JSON |
| Error History / Similar Error | PASS | successful empty-state responses |
| Progress | PASS | real qwen response, structured sections returned |
| Automation / History / Notification | PASS | no-LLM run, 2 history and 2 candidate rows |
| Dependency check | PASS_WITH_BACKLOG | no High/Critical npm finding; 2 moderate Vitest advisories |
| README / Architecture | PASS | release summary, startup and trust boundary current |
| Tauri | DEFERRED | Cargo unavailable; web UI + Launcher is release baseline |
| Push | NOT_DONE | explicit approval required |

## Phase 12 Desktop Packaging

Validated on 2026-09-16.

| Item | Result | Evidence |
| --- | --- | --- |
| Rust/MSVC/WebView2 | PASS | Rust 1.98.1 MSVC target, VS C++ tools and WebView2 present |
| Tauri compile | PASS | `cargo check`, 2 Rust tests |
| Backend JAR | PASS | Java 17 `localrag-backend.jar`, 67.84 MB |
| Desktop executable | PASS | Windows x64 executable, 11.58 MB |
| Installer | PASS | unsigned NSIS setup, 62.47 MB |
| Desktop security | PASS | core permission only; fixed loopback API bridge; CSP enabled |
| Backend readiness | PASS | identified reuse/start/port-conflict decisions and bounded polling tested |
| Production Window | PASS | `LocalRAG` native Window and `http://tauri.localhost/` WebView |
| Project / Overview | PASS | 13 Projects; `Local_Ai_Work`; Docker/DB/Ollama AVAILABLE |
| RAG | PASS | SUCCESS, 5 Sources, no UI error |
| Agent Git | PASS | answer returned, `getGitStatus` trace |
| Error / Progress | PASS | History empty-state and Progress action rendered |
| Automation | PASS | Run Now SUCCESS, LLM skipped |
| Failure scenarios | PASS_WITH_LIMITATION | bounded/unit paths verified; services were not deliberately stopped |
| Backend tests | PASS | 174 tests, 0 failures/errors, 1 live skip |
| Frontend tests | PASS | 11 tests in 7 files |
| Development command | CONFIGURED | standard 5173 port already occupied by running Phase 11 launcher |
| Computer Use | FALLBACK | helper failed workspace refresh; actual WebView was inspected through local CDP |
| Code signing | DEFERRED | SmartScreen warning remains possible |
| Push | NOT_DONE | explicit approval required |

## Launcher scenario coverage

- A running services: actual repeat execution reused every component.
- B Docker stopped: deterministic startup branch and `DOCKER_NOT_RUNNING` timeout are tested;
  Docker Desktop was not deliberately shut down for testing.
- C postgres stopped: actual run started only `local-ai-postgres` and preserved its volume.
- D Ollama unavailable: deterministic startup/failure branch tested; actual run started fixed
  `ollama.exe` and found both required models.
- E/F Backend/Frontend port conflict: deterministic `PORT_IN_USE` branch tested without killing
  or occupying unrelated processes.
