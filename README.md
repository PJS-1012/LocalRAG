# LocalRAG

LocalRAG는 PC 전체를 무작정 AI에 넣지 않고, 등록된 Workspace 안의 개발 Project만
안전하게 탐색·인덱싱하여 검색, 근거 기반 답변, 읽기 전용 진단과 개발 Workflow 분석을
제공하는 local-first Developer AI Workbench입니다.

## Why

로컬 개발 환경의 지식은 코드, 문서, Git, 컨테이너, 로그와 오류 이력에 흩어져 있습니다.
LocalRAG는 이 정보를 Project 경계 안에서 연결하되, 민감 파일 차단과 실패 격리,
읽기 전용 Tool 정책을 먼저 적용합니다. 기존 RAG 전용 API는 검색 결과가 없으면
`NO_EVIDENCE`를 반환합니다. 메인 채팅은 Git/작업 이력/안전한 실제 파일 등 다른 근거도
사용하므로, Vector Search 0건만으로 모든 질문을 중단하지 않습니다.

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
- 하나의 채팅에서 기존 Tool을 선택하는 Unified Chat와 실제 파일 기반 온보딩
- React/Vite 기반 한국어 중심 10개 화면, Project 언어·Git 작업/원격 상태 분리
- Tauri 2 기반 Windows Desktop Window와 NSIS installer

## Quick Start — Windows Desktop

1. **LocalRAG 실행**

설치된 LocalRAG를 실행하면 Startup 화면이 즉시 열립니다. Docker Desktop → LocalRAG
PostgreSQL → Ollama → 필수 모델 → Backend를 확인하고, 필요한 서비스만 백그라운드에서
시작합니다. 모두 READY(준비 완료)가 되면 대시보드로 이동합니다. 실패 시 같은 창의 **다시 준비**를
사용합니다. 매번 PowerShell, Docker UI, Ollama 터미널 또는 브라우저를 열 필요가 없습니다.

이미 설치되어 있어야 하는 환경은 Docker Desktop, Java 17, Ollama와
`qwen3:8b` / `qwen3-embedding:0.6b`입니다. Java 17은 JAVA_HOME 또는 PATH에서 찾습니다.
WebView2와 PostgreSQL용 pgvector/pgvector:0.8.6-pg17 이미지도 필요합니다.
Desktop은 설치·모델 다운로드·OS 설정 변경을 자동 수행하지 않습니다.

- 실행 파일: `frontend/src-tauri/target/release/localrag-desktop.exe`
- Installer: `frontend/src-tauri/target/release/bundle/nsis/LocalRAG_0.1.0_x64-setup.exe`
- 직접 exe를 실행할 때에는 옆의 backend/, runtime/ resource 폴더도 함께 유지합니다.
- Startup 실패·모델 누락·18080 port 충돌은 앱 안에 표시됩니다.
- 로그: `%LOCALAPPDATA%/com.localai.localrag/logs/`
- 앱을 닫아도 공유 runtime은 종료하지 않으며 다음 실행에서 재사용합니다.

대시보드의 전체 프로젝트 목록에서 상태와 상세 정보를 확인할 수 있습니다.
CLEAN/DIRTY는 파일 변경 여부이며, PUSHED/UNPUSHED와 ahead/behind는 설정된 upstream의
**로컬 참조 기준**입니다. 자동 fetch는 하지 않으므로 원격 서버 최신 상태를 보장하지 않습니다.
상세를 열거나 메타데이터를 갱신하는 동작은 LLM을 호출하지 않습니다.

## 채팅 사용과 현재 한계

프로젝트 선택 후 **채팅**에서 목적, 처음 볼 파일, Git 상태, 진행 상태, 최근 작업을 질문합니다.
기능별 화면을 먼저 고를 필요는 없습니다. **Enter 전송 / Shift+Enter 줄바꿈**을 지원합니다.
이전 RAG와 Agent 화면은 고급 기능의 지식 검색 상세 / 에이전트 상세에 유지합니다.
이 채팅은 단일 질문 단위이며 이전 대화 기억은 제공하지 않습니다.

답변 옆에서 파일 경로·행·citation과 실제 Tool 실행 근거를 확인할 수 있습니다.
인덱스가 없거나 관련 검색 결과가 없어도 안전한 README/build/code 샘플로 설명할 수 있지만,
이는 전체 Project의 구현 완료나 테스트 통과를 증명하지 않습니다.

주요 언어 비율은 제외 정책을 통과한 **소스 파일 수** 기준이며 코드 줄 수 비율이 아닙니다.
Metadata는 기본 5분 cache를 사용합니다. 상세를 여는 동작에는 추가 Project API 요청이 없습니다.

실제 15개 질문 평가: **PASS 4 / PARTIAL 10 / FAIL 1**. 현재 일부 답변은 citation을
누락하거나, 기록되지 않은 미완료 작업을 없다고 단정합니다. 기능 연결은 완료했지만 답변 품질의
완전한 종료를 선언하지 않습니다. 상세 결과는 [평가 보고서](docs/unified-chat-evaluation.md),
설계는 [Decision Log 0034](docs/decisions/0034-unified-chat-onboarding-korean-ux.md)에 기록했습니다.

## Developer setup

소스 개발에는 Node.js/npm이 추가로 필요하며 Desktop build에는 Rust/MSVC C++ build tools가
필요합니다.

```powershell
cd frontend
npm run desktop:dev
npm run desktop:build
```

웹 개발용 수동 실행은 프로젝트 루트의 dev-start.ps1을 사용합니다.
이 스크립트는 Backend와 Vite 개발 서버를 준비하고 브라우저를 엽니다.

```powershell
.\dev-start.ps1
# 브라우저를 열지 않는 개발 실행
.\dev-start.ps1 -NoBrowser
```

Desktop runtime 제어는 고정 실행 파일과 고정 Compose service만 사용합니다.
Container/volume 삭제, 다른 Project container 변경, 모델 pull, 기존 Java/Ollama 강제 종료는
하지 않습니다. Docker 자체의 OS/소켓 오류는 실패로 보고하며 자동 초기화하지 않습니다.

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

2026-09-18 로컬 Windows 환경 기준입니다. 캐시, 모델 warm-up과 시스템 부하에 따라 달라집니다.

- Backend: 180 tests / 67 suites, 실패 0, opt-in live test 1개 skip
- Frontend: 13 tests / 8 files, 실패 0; Rust: 10 tests 통과
- Production bundle: JS 281.24 kB (gzip 85.11 kB), CSS 24.69 kB (gzip 5.99 kB)
- Desktop: Windows x64 exe 및 unsigned NSIS installer 생성
- 실제 RAG: SUCCESS, Source 5개, 17.7초
- 실제 Agent: SUCCESS, getGitStatus, LLM 28.6초
- Workspace summary: 13 Projects, 2,680 ms; Local_Ai_Work 151 documents / 259 chunks
- Production WebView 1440×1000: Project list/detail, 내부 scroll, console 오류 및 가로 overflow 0
- One-click: Docker 소켓 환경 복구 후 모든 runtime 미실행 상태에서 자동 시작 PASS

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
- 2026-09-18 production UI에서 Agent Git 답변과 Tool trace를 모두 확인했습니다.
- UI와 Tauri Window의 최소 width는 720px입니다.
- Desktop은 외부 Java 17 설치를 요구하며 jlink runtime bundle은 deferred입니다.
- 앱이 시작한 Backend는 PID를 추적하지만 앱 종료 시 강제 종료하지 않습니다. 현재 release는
  안전한 graceful shutdown endpoint를 추가하지 않고 실행 중 Backend를 다음 실행에서 재사용합니다.
- installer는 code signing하지 않아 Windows SmartScreen 경고가 표시될 수 있습니다.
- 현재 Docker Desktop은 재부팅 후 오래된 AF_UNIX 소켓 접근 오류가 재발할 수 있습니다.
  이 경우 Startup 실패/Retry가 표시됩니다. 이번 검증에서는 별도 승인 아래 소켓 폴더만
  백업해 복구했으며, 컨테이너/volume은 삭제하지 않았습니다. 무조건적인 재부팅 후
  one-click 성공은 이 Docker 외부 문제 해결 전까지 보장하지 않습니다.
- Docker CLI는 background start를 요청하지만 Docker 자체 Dashboard/오류창이 나타날 수 있습니다.
- Vitest toolchain에 moderate 개발 의존성 advisory 2건이 있으며 수정에는 major upgrade가 필요합니다.
- Launcher는 시작/재사용만 담당하며 서비스 종료 명령은 제공하지 않습니다.

## Portfolio Documents

- [Architecture](docs/architecture.md)
- [Portfolio Highlights](docs/portfolio-highlights.md)
- [Interview Stories](docs/interview-stories.md)
- [Release Checklist](docs/release-checklist.md)
- [Phase 10 Metrics](docs/phase10-portfolio-metrics.md)
