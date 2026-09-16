# LocalRAG

LocalRAG는 PC 전체를 무작정 AI에 넣지 않고, 등록된 Workspace 안의 개발 Project만
안전하게 탐색·인덱싱하여 검색, 근거 기반 답변, 읽기 전용 진단과 개발 Workflow 분석을
제공하는 local-first Developer AI Workbench입니다.

## Why

로컬 개발 환경의 지식은 코드, 문서, Git, 컨테이너, 로그와 오류 이력에 흩어져 있습니다.
LocalRAG는 이 정보를 Project 경계 안에서 연결하되, 민감 파일 차단과 실패 격리,
읽기 전용 Tool 정책을 먼저 적용합니다. 검색 결과가 없으면 LLM을 호출하지 않고
`NO_EVIDENCE`를 반환합니다.

```text
File / Code -> Document -> Chunk -> Embedding -> pgvector -> cited RAG
Environment -> Git / Docker / DB / Ollama / Log -> read-only Agent
Error -> Analysis -> History -> Verification Audit -> Similar Error
Project -> Progress / Activity -> change-aware Automation
```

## 주요 기능

- Workspace/Container/Project 탐지와 Project-relative `projectId`
- 민감 파일, 제외 경로, 5 MB 제한, UTF-8 및 link/path traversal 방어
- 구조 경계를 우선하는 Chunking과 1024차원 Ollama Embedding
- Project 범위 cosine search, citation-ready Context와 `NO_EVIDENCE`
- Git/Docker/DB/Ollama/Log 기반 read-only Multi-Tool Agent
- Error History, Verification Audit, Similar Error vector retrieval
- Progress, Recent Activity, change-aware Automation과 Notification Candidates
- React/Vite 기반 9개 화면의 Local Developer Dashboard
- Tauri 2 기반 Windows Desktop Window와 NSIS installer

## Quick Start — Windows

필수 환경은 Java 17, Node.js/npm, Docker Desktop, Ollama입니다. 다음 모델은 설치되어 있어야
하며 Launcher가 자동으로 다운로드하지 않습니다.

- `qwen3:8b`
- `qwen3-embedding:0.6b`

프로젝트 루트에서 한 번 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\dev-start.ps1
```

Launcher는 Docker Engine을 확인하고, LocalRAG의 `postgres` Compose service만 시작한 뒤
Ollama, Spring Boot Backend, Vite Frontend를 순서대로 준비합니다. 기존 LocalRAG 인스턴스는
재사용하고, 다른 프로세스가 18080/5173 포트를 사용하면 종료하지 않고 `PORT_IN_USE`로
실패합니다. 로그와 PID 상태는 Git에서 제외된 `.localrag/`에 기록됩니다.

준비가 끝나면 `http://localhost:5173`을 엽니다. 브라우저를 열지 않으려면:

```powershell
.\dev-start.ps1 -NoBrowser
```

Launcher는 컨테이너/volume 삭제, 모델 pull, 기존 프로세스 강제 종료를 수행하지 않습니다.

## Desktop — Windows

Desktop build도 기존 React UI와 Spring Boot Backend를 그대로 사용합니다. 설치 후
`LocalRAG`를 실행하면 독립 Window가 열리고, port 18080에서 식별된 LocalRAG Backend를
재사용하거나 bundle resource의 고정 `localrag-backend.jar`를 Java 17로 시작합니다.

필수 환경:

- Java 17이 `PATH`에서 실행 가능
- Docker Desktop과 LocalRAG PostgreSQL/pgvector (DB 기능 사용 시)
- Ollama와 `qwen3:8b`, `qwen3-embedding:0.6b` (AI 기능 사용 시)

개발 및 installer build:

```powershell
cd frontend
npm run desktop:dev
npm run desktop:build
```

생성 위치:

- 실행 파일: `frontend/src-tauri/target/release/localrag-desktop.exe`
- NSIS installer: `frontend/src-tauri/target/release/bundle/nsis/LocalRAG_0.1.0_x64-setup.exe`

Desktop은 Docker/Ollama를 범용 제어하지 않습니다. 해당 서비스가 꺼져 있어도 Window는
유지되고 Backend 및 Settings의 상태 표시를 통해 장애 범위를 확인합니다. Backend가
없거나 시작되지 않으면 `STARTING` 후 bounded timeout을 거쳐 `UNAVAILABLE`을 표시하며,
다른 프로세스가 18080을 사용하면 종료하지 않고 `PORT_IN_USE`로 표시합니다.

문제 해결:

- Backend unavailable: Java 17과 Desktop Backend log를 확인합니다.
- Docker/DB unavailable: Docker Desktop과 `local-ai-postgres` container를 확인합니다.
- Ollama unavailable/model missing: Ollama와 위 두 모델 설치 상태를 확인합니다.
- Port 18080 in use: 점유 프로세스를 자동 종료하지 않으므로 사용자가 충돌을 해소해야 합니다.

## Architecture

```text
Tauri Desktop -> React/Vite UI
  -> fixed loopback API bridge -> Spring Boot REST API
     -> Workspace Discovery -> Safe Scan -> Document Read
     -> Chunk -> Ollama Embedding -> PostgreSQL/pgvector
     -> Retrieval -> 8,000-char Context -> qwen3:8b
     -> Read-only Tools -> Agent
     -> Error / Progress / Activity / Automation services
```

세부 구조와 신뢰 경계는 [Architecture](docs/architecture.md), 설계 근거는
[Decision Logs](docs/decisions/)에 있습니다.

## Retrieval Baseline

| 항목 | 기준값 |
| --- | --- |
| Chunk | Text/Markdown 2,000자, Source 2,400자, overlap 200자 |
| Embedding | `qwen3-embedding:0.6b`, 1024 dimensions |
| Search | cosine similarity, Project scoped, Top-K 5, threshold 0.45 |
| Query | retrieval instruction 기본 ON, raw-query 전환 가능 |
| Context | formatted context 최대 8,000자, Chunk 중간 절단 없음 |
| Chat | `qwen3:8b`, Source citation, no evidence면 LLM skip |

## Safety Design

- 외부 식별자는 절대 경로가 아닌 Workspace-relative `projectId` 사용
- Workspace 밖 경로, `..`, symbolic link 우회 차단
- 민감 파일명, 생성 폴더, Unity cache와 5 MB 초과 파일 차단
- 파일/Project 단위 실패 격리와 엄격한 UTF-8 처리
- Agent Tool은 read-only이며 출력 크기·시간 제한과 secret redaction 적용
- Retrieved text, Git message와 Log는 신뢰하지 않는 evidence로 취급
- Automation은 코드/Git/서비스 상태를 변경하지 않음

## Measured Release Baseline

2026-09-16 로컬 Windows 환경 기준입니다. 캐시, 모델 warm-up과 시스템 부하에 따라 달라집니다.

- Backend: 174 tests, 실패 0, opt-in live test 1개 skip
- Frontend: 11 tests / 7 files, 실패 0
- Production bundle: JS 275.55 kB (gzip 83.54 kB), CSS 22.23 kB (gzip 5.49 kB)
- Desktop: 11.49 MB exe, 62.46 MB unsigned NSIS installer
- 실제 RAG: SUCCESS, Source 5개 / 사용 3개 / invalid citation 0, 17.56초
- 실제 Progress: SUCCESS, LLM 21.57초 / total 25.51초
- LLM 비활성 Automation Run Now: 374 ms
- Chrome 1440px 실제 렌더링: 9개 화면, Similar 탭, console 오류 및 가로 overflow 0

상세 수치는 [Portfolio Highlights](docs/portfolio-highlights.md)에 있습니다.

## Tech Stack

- Java 17, Spring Boot 3.5.16, Spring AI 1.1.8, Gradle 8.14.3
- React 19, TypeScript 5.9, Vite 7, Tauri 2.11, Rust 1.98
- PostgreSQL 17, pgvector 0.8.6, Flyway V1–V6
- Ollama, qwen3:8b, qwen3-embedding:0.6b
- Docker Compose, Testcontainers, JUnit 5, Vitest

## Tests

Docker Desktop이 실행 중이어야 Backend 통합 테스트가 pgvector Testcontainer를 사용합니다.

```powershell
.\gradlew.bat test --no-daemon
cd frontend
npm test
npm run build
$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
cargo test --manifest-path .\src-tauri\Cargo.toml
npm run desktop:build
```

Launcher의 상태 전이와 금지 명령 검사는 다음으로 실행합니다.

```powershell
.\scripts\DevLauncher.Tests.ps1
```

## Known Limitations

- Vector-only retrieval이라 migration/문서가 구현 코드보다 높게 노출되는 ranking 사례가 있습니다.
- 실제 qwen 응답은 환경에 따라 약 15–50초가 걸릴 수 있습니다.
- 최종 Agent Git 재확인 요청은 완료됐지만 실행 도구가 응답 JSON을 반환하지 않아 PARTIAL입니다.
- UI와 Tauri Window의 최소 width는 720px입니다.
- Desktop은 외부 Java 17 설치를 요구하며 jlink runtime bundle은 deferred입니다.
- 앱이 시작한 Backend는 PID를 추적하지만 앱 종료 시 강제 종료하지 않습니다. 현재 release는
  안전한 graceful shutdown endpoint를 추가하지 않고 실행 중 Backend를 다음 실행에서 재사용합니다.
- installer는 code signing하지 않아 Windows SmartScreen 경고가 표시될 수 있습니다.
- Docker/Ollama/PostgreSQL 자동 시작 버튼은 packaging 핵심 범위 밖으로 deferred했습니다.
- Vitest toolchain에 moderate 개발 의존성 advisory 2건이 있으며 수정에는 major upgrade가 필요합니다.
- Launcher는 시작/재사용만 담당하며 서비스 종료 명령은 제공하지 않습니다.

## Portfolio Documents

- [Architecture](docs/architecture.md)
- [Portfolio Highlights](docs/portfolio-highlights.md)
- [Interview Stories](docs/interview-stories.md)
- [Release Checklist](docs/release-checklist.md)
- [Phase 10 Metrics](docs/phase10-portfolio-metrics.md)
