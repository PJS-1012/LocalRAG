# LocalRAG

LocalRAG는 PC 전체를 무작정 AI에 넣지 않고, 지정된 작업공간 안의 개발 프로젝트만
안전하게 탐색·인덱싱하는 로컬 개발 도우미입니다.
코드 검색, 근거 기반 답변, 읽기 전용 환경 진단과 개발 진행 상황 분석을 제공합니다.

## 만든 이유

로컬 개발 환경의 지식은 코드, 문서, Git, 컨테이너, 로그와 오류 이력에 흩어져 있습니다.
LocalRAG는 이 정보를 프로젝트 범위 안에서 연결하되, 민감 파일 차단과 실패 격리,
읽기 전용 도구 정책을 먼저 적용합니다. 기존 RAG 전용 API는 검색 결과가 없으면
`NO_EVIDENCE`를 반환합니다. 메인 채팅은 Git/작업 이력/안전한 실제 파일 등 다른 근거도
사용하므로, 벡터 검색 결과가 0건이라는 이유만으로 모든 질문을 중단하지 않습니다.

```text
파일 / 코드 -> 문서 -> 청크 -> Embedding -> pgvector -> 출처가 있는 RAG 답변
실행 환경 -> Git / Docker / DB / Ollama / 로그 -> 읽기 전용 Agent
오류 -> 원인 분석 -> 이력 저장 -> 검증 기록 -> 유사 오류 검색
프로젝트 -> 진행 상태 / 최근 작업 -> 변경 감지 기반 자동화
```

## 주요 기능

- 작업공간·프로젝트 묶음·개별 프로젝트 탐지와 상대 경로 기반 `projectId`
- 민감 파일·제외 경로 차단, 5 MB 제한, 엄격한 UTF-8 처리와 링크·경로 우회 방어
- 코드 구조를 고려한 청크 분할과 1024차원 Ollama Embedding
- 프로젝트 범위 코사인 유사도 검색, 출처 추적 및 `NO_EVIDENCE` 처리
- Git/Docker/DB/Ollama/로그를 조회하는 읽기 전용 Agent
- 오류 이력, 검증 기록, 유사 오류 벡터 검색
- 진행 상태·최근 작업 분석, 변경 감지 자동화와 알림 후보
- 하나의 채팅에서 기존 도구를 선택하는 통합 채팅과 실제 파일 기반 온보딩
- React/Vite 기반 한국어 중심 10개 화면, 프로젝트 언어·Git 작업/원격 상태 구분
- Tauri 2 기반 Windows 데스크톱 앱과 NSIS 설치 파일

## 빠른 시작 — Windows 앱

1. **LocalRAG 실행**

설치된 LocalRAG를 실행하면 시작 화면이 즉시 열립니다. Docker Desktop → LocalRAG용
PostgreSQL → Ollama → 필수 모델 → 백엔드를 확인하고, 필요한 서비스만 백그라운드에서
시작합니다. 모두 READY(준비 완료)가 되면 대시보드로 이동합니다. 실패 시 같은 창의 **다시 준비**를
사용합니다. 매번 PowerShell, Docker UI, Ollama 터미널 또는 브라우저를 열 필요가 없습니다.

미리 준비해야 하는 환경은 Docker Desktop, Java 17, Ollama와
`qwen3:8b` / `qwen3-embedding:0.6b`입니다. Java 17은 JAVA_HOME 또는 PATH에서 찾습니다.
WebView2와 PostgreSQL용 pgvector/pgvector:0.8.6-pg17 이미지도 필요합니다.
앱은 프로그램 설치·모델 다운로드·운영체제 설정 변경을 자동 수행하지 않습니다.

- 실행 파일: `frontend/src-tauri/target/release/localrag-desktop.exe`
- 설치 파일: `frontend/src-tauri/target/release/bundle/nsis/LocalRAG_0.1.0_x64-setup.exe`
- exe를 직접 실행할 때에는 옆의 `backend/`, `runtime/` 리소스 폴더도 함께 유지합니다.
- 시작 실패·모델 누락·18080 포트 충돌은 앱 안에 표시됩니다.
- 로그: `%LOCALAPPDATA%/com.localai.localrag/logs/`
- 앱을 닫아도 공용 실행 서비스는 종료하지 않으며 다음 실행에서 재사용합니다.

대시보드의 전체 프로젝트 목록에서 상태와 상세 정보를 확인할 수 있습니다.
CLEAN/DIRTY는 파일 변경 여부이며, PUSHED/UNPUSHED와 ahead/behind는 연결된 원격 브랜치의
**로컬 참조 기준**입니다. 자동 fetch는 하지 않으므로 원격 서버 최신 상태를 보장하지 않습니다.
상세를 열거나 메타데이터를 갱신하는 동작은 LLM을 호출하지 않습니다.

## 채팅 사용과 현재 한계

프로젝트 선택 후 **채팅**에서 목적, 처음 볼 파일, Git 상태, 진행 상태, 최근 작업을 질문합니다.
기능별 화면을 먼저 고를 필요는 없습니다. **Enter 전송 / Shift+Enter 줄바꿈**을 지원합니다.
전송하면 입력칸이 즉시 비워집니다. 통신 실패 시 원래 질문을 복원하되, 기다리는 동안 새로
작성한 입력은 덮어쓰지 않습니다. 보낸 질문은 응답 결과와 함께 표시됩니다.
이전 RAG와 Agent 화면은 고급 기능의 지식 검색 상세 / 에이전트 상세에 유지합니다.
이 채팅은 단일 질문 단위이며 이전 대화 기억은 제공하지 않습니다.

| 화면 | 역할 | 사용 시점 |
| --- | --- | --- |
| 채팅 | 질문에 맞춰 코드·문서·Git·진행 상태 등 기존 기능을 자동 선택하고 실제 파일 정보로 보완 | 일반 사용의 기본 진입점. 프로젝트 소개·처음 볼 파일·최근 작업 등 |
| 에이전트 상세 | 기존 읽기 전용 Agent의 도구 선택과 실행 결과·시간을 확인 | Git·DB·로그 등의 도구 호출을 자세히 점검할 때 |
| 지식 검색 상세 | 인덱싱된 코드·문서에서 RAG 검색 후 출처를 붙여 답변 | 저장된 자료의 검색 결과와 인용 원문을 확인할 때. 근거가 없으면 답변 보류 |

채팅과 에이전트 상세는 같은 기반 모델과 도구를 재사용합니다. 채팅에는 추가로 안전한 실제
파일 수집과 온보딩 지침이 적용되며, 지식 검색 상세는 저장된 인덱스만 사용합니다.

답변 옆에서 파일 경로·행·인용 출처와 실제 도구 실행 근거를 확인할 수 있습니다.
인덱스가 없거나 관련 검색 결과가 없어도 안전한 README·빌드 파일·코드 일부로 설명할 수 있지만,
이는 전체 프로젝트의 구현 완료나 테스트 통과를 증명하지 않습니다.

주요 언어 비율은 제외 정책을 통과한 **소스 파일 수** 기준이며 코드 줄 수 비율이 아닙니다.
메타데이터는 기본 5분 캐시를 사용합니다. 상세를 여는 동작에는 추가 프로젝트 API 요청이 없습니다.

실제 15개 질문 평가: **통과 4 / 부분 통과 10 / 실패 1**. 현재 일부 답변은 인용을
누락하거나, 기록되지 않은 미완료 작업을 없다고 단정합니다. 기능 연결은 완료했지만 답변 품질의
완전한 종료를 선언하지 않습니다. 상세 결과는 [평가 보고서](docs/unified-chat-evaluation.md),
설계는 [설계 결정 기록 0034](docs/decisions/0034-unified-chat-onboarding-korean-ux.md)에 기록했습니다.

## 개발 환경 설정

소스 개발에는 Node.js/npm이 추가로 필요하며 데스크톱 앱 빌드에는 Rust/MSVC C++ 빌드 도구가
필요합니다.

```powershell
cd frontend
npm run desktop:dev
npm run desktop:build
```

웹 개발용 수동 실행은 프로젝트 루트의 dev-start.ps1을 사용합니다.
이 스크립트는 백엔드와 Vite 개발 서버를 준비하고 브라우저를 엽니다.

```powershell
.\dev-start.ps1
# 브라우저를 열지 않는 개발 실행
.\dev-start.ps1 -NoBrowser
```

앱의 서비스 시작 제어는 지정된 실행 파일과 Compose 서비스만 사용합니다.
컨테이너·볼륨 삭제, 다른 프로젝트 컨테이너 변경, 모델 다운로드, 기존 Java/Ollama 강제 종료는
하지 않습니다. Docker 자체의 OS/소켓 오류는 실패로 보고하며 자동 초기화하지 않습니다.

## 시스템 구조

```text
Tauri 데스크톱 앱 -> React/Vite 화면
  -> 고정된 로컬 API 연결 -> Spring Boot REST API
     -> 작업공간 탐지 -> 안전한 파일 탐색 -> 문서 읽기
     -> Chunk -> Ollama Embedding -> PostgreSQL/pgvector
     -> 검색 -> 최대 8,000자 근거 문맥 -> qwen3:8b
     -> 읽기 전용 도구 -> Agent
     -> 오류 / 진행 상태 / 최근 작업 / 자동화 서비스
```

세부 구조와 신뢰 경계는 [구조 문서](docs/architecture.md), 설계 근거는
[설계 결정 기록](docs/decisions/)에 있습니다.

## 검색·답변 기본 설정

| 항목 | 기준값 |
| --- | --- |
| 청크 | 일반 텍스트/Markdown 2,000자, 소스 코드 2,400자, 중첩 200자 |
| Embedding | `qwen3-embedding:0.6b`, 1024차원 |
| 검색 | 코사인 유사도, 선택한 프로젝트 범위, Top-K 5, 임계값 0.45 |
| 검색 질문 | 검색용 지침 기본 적용, 원문 질문 모드로 전환 가능 |
| RAG 근거 문맥 | 출처 헤더 포함 최대 8,000자, 청크 중간 절단 없음 |
| RAG 전용 답변 | `qwen3:8b`, 출처 인용, 검색 근거가 없으면 LLM 호출 생략 |

통합 채팅은 위 검색 외에도 실제 파일·실행 상태·작업 이력을 사용합니다. RAG 전용 API와
통합 채팅의 근거 부족 처리 방식은 다릅니다.

## 안전 설계

- 외부 식별자는 절대 경로가 아닌 작업공간 상대 경로 `projectId` 사용
- 작업공간 밖 경로, `..`, 심볼릭 링크를 통한 우회 차단
- 민감 파일명, 생성 폴더, Unity 캐시와 5 MB 초과 파일 차단
- 파일·프로젝트 단위 실패 격리와 엄격한 UTF-8 처리
- Agent 도구는 읽기 전용이며 출력 크기·실행 시간 제한과 민감 정보 마스킹 적용
- 검색된 텍스트·Git 커밋 메시지·로그는 명령이 아닌 신뢰하지 않는 근거 자료로 취급
- 자동화는 코드·Git·서비스 상태를 변경하지 않음

## 기존 배포판 측정 기준값

아래는 2026-09-18의 과거 측정값입니다. 캐시, 모델 준비 상태와 시스템 부하에 따라 달라집니다.
2026-09-22 통합 채팅 측정 결과는 [별도 평가 보고서](docs/unified-chat-evaluation.md)를 참고하세요.

- 백엔드: 테스트 180개 / 묶음 67개, 실패 0, 선택 실행하는 실제 모델 테스트 1개 제외
- 프런트엔드: 테스트 13개 / 파일 8개, 실패 0; Rust: 테스트 10개 통과
- 배포 번들: JS 281.24 kB (gzip 85.11 kB), CSS 24.69 kB (gzip 5.99 kB)
- 데스크톱: Windows x64 실행 파일과 서명되지 않은 NSIS 설치 파일 생성
- 실제 RAG: 성공, 출처 5개, 17.7초
- 실제 Agent: 성공, getGitStatus 사용, LLM 28.6초
- 작업공간 요약: 프로젝트 13개, 2,680 ms; Local_Ai_Work 문서 151개 / 청크 259개
- 배포 WebView 1440×1000: 프로젝트 목록·상세와 내부 스크롤 확인, 콘솔 오류·가로 넘침 0
- 원클릭 실행: Docker 소켓 환경 복구 후 모든 서비스가 꺼진 상태에서 자동 시작 통과

상세 수치는 [포트폴리오 주요 성과](docs/portfolio-highlights.md)에 있습니다.

## 기술 구성

- Java 17, Spring Boot 3.5.16, Spring AI 1.1.8, Gradle 8.14.3
- React 19, TypeScript 5.9, Vite 7, Tauri 2.11, Rust 1.98
- PostgreSQL 17, pgvector 0.8.6, Flyway V1–V6
- Ollama, qwen3:8b, qwen3-embedding:0.6b
- Docker Compose, Testcontainers, JUnit 5, Vitest

## 테스트 실행

백엔드 통합 테스트는 pgvector Testcontainer를 사용하므로 Docker Desktop이 실행 중이어야 합니다.

```powershell
.\gradlew.bat test --no-daemon
cd frontend
npm test
npm run build
$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
cargo test --manifest-path .\src-tauri\Cargo.toml
npm run desktop:build
```

프로젝트 루트에서 다음 명령으로 개발 실행 스크립트의 상태 전이와 금지 명령을 검사합니다.

```powershell
.\scripts\DevLauncher.Tests.ps1
```

## 알려진 한계

- 벡터 검색만 사용하므로 DB 마이그레이션·문서가 구현 코드보다 상위에 노출되는 사례가 있습니다.
- 실제 qwen 응답은 환경에 따라 약 15–50초가 걸릴 수 있습니다.
- 2026-09-18 배포 화면에서 Agent Git 답변과 도구 실행 기록을 모두 확인했습니다.
- UI와 Tauri 창의 최소 너비는 720px입니다.
- 외부 Java 17 설치가 필요하며, jlink 기반 Java 실행 환경 동봉은 보류했습니다.
- 앱이 시작한 백엔드의 PID는 추적하지만 앱 종료 시 강제 종료하지 않습니다.
  현재 배포판에는 정상 종료용 API를 추가하지 않았으며, 다음 실행에서 기존 백엔드를 재사용합니다.
- 설치 파일에 코드 서명이 없어 Windows SmartScreen 경고가 표시될 수 있습니다.
- 현재 Docker Desktop은 재부팅 후 오래된 AF_UNIX 소켓 접근 오류가 재발할 수 있습니다.
  이 경우 시작 실패와 재시도 안내가 표시됩니다. 해당 검증에서는 별도 승인 아래 소켓 폴더만
  백업해 복구했으며, 컨테이너·볼륨은 삭제하지 않았습니다. 이 Docker 외부 문제가 해결되기 전에는
  재부팅 후 원클릭 실행 성공을 항상 보장하지 않습니다.
- Docker CLI로 백그라운드 시작을 요청해도 Docker 자체 대시보드·오류창이 나타날 수 있습니다.
- 이전 의존성 검사에서는 Vitest 개발 도구에서 중간 심각도 보안 권고 2건이 확인됐으며,
  당시 자동 수정안은 주 버전 업그레이드를 요구했습니다. 최신 상태는 별도 점검이 필요합니다.
- 실행 도우미는 시작·재사용만 담당하며 서비스 종료 명령은 제공하지 않습니다.

## 관련 문서

- [시스템 구조](docs/architecture.md)
- [포트폴리오 주요 성과](docs/portfolio-highlights.md)
- [면접용 개발 사례](docs/interview-stories.md)
- [배포 점검표](docs/release-checklist.md)
- [Phase 10 측정 결과](docs/phase10-portfolio-metrics.md)
- [통합 채팅 품질 평가](docs/unified-chat-evaluation.md)
