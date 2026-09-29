# 결정 0033: 원클릭 시작과 프로젝트 요약

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

일자: 2026-09-18. 후속 자동 시작 검증: 2026-09-21.

## 범위와 결정

설치된 Windows 앱이 이미 설치된 실행 환경을 준비하고 저비용 프로젝트 메타데이터를 보여준다. Java 17, 모델, RAG/Agent 프롬프트, 검색/스케줄 정책은 유지했다. 프로그램 설치, 모델 다운로드, Git push, 컨테이너 삭제, 볼륨 초기화는 추가하지 않았다.

창 표시 → 비동기 네이티브 작업 → Docker → PostgreSQL → Ollama → 모델 목록 → 백엔드 → 대시보드 순서다. 퍼센트 대신 단계별 상태를 표시한다. 실패 이유를 남기고 뒤 단계는 WAITING으로 유지한다. 재시도는 직렬화하고 정상 서비스를 재사용한다.

Docker는 고정 설치 CLI와 로컬 named pipe만 사용하며 `desktop start --detach`로 시작한다. Docker 자체 창은 나타날 수 있지만 Java/Ollama/CLI 콘솔은 숨긴다. `local_ai_work`/`postgres` Compose 레이블이 맞는 `local-ai-postgres`만 시작한다. 없으면 동봉된 compose.yml의 같은 프로젝트/볼륨 이름으로 postgres만 `--no-deps`, `--pull never`로 생성한다. 다른 소유권과 UNHEALTHY는 실패다. 모델 이름은 정확히 비교한다.

Java 17을 JAVA_HOME/PATH에서 확인해 고정된 JAR만 실행한다. 18080은 LocalRAG health로 식별하고 구버전은 탐지 API로 보완한다. 준비 제한은 Docker 180초, PostgreSQL 90초, Ollama 60초, 모델 5초, 백엔드 90초다. 출력 읽기까지 각 명령/요청을 제한하되 진행 중 명령의 자체 제한만큼 단계 시간이 연장될 수 있다. 프런트엔드는 510회 확인 후 중단한다. 창을 닫아도 공유 서비스는 종료하지 않고 시간 초과 정리는 소유한 단기 명령에만 적용한다.

## Java 17 리소스 경로 문제

실제 Windows 리소스 경로의 verbatim 접두사 때문에 Java 17이 존재하는 JAR를 열지 못했다. Java에 전달하기 전 로컬/UNC 경로를 정규화하고 회귀 테스트를 추가했다. 이후 동봉 백엔드는 약 7초 만에 READY에 도달했다.

## 대시보드와 Git 근거

`GET /api/workspaces/overview`는 한 번 탐지하고 DB 일괄 메타데이터 쿼리 4개와 제한된 저장소별 Git 조회를 실행한다. 프런트엔드 요청 한 번으로 목록/상세를 채운다. 행별 추가 HTTP, LLM, 벡터 검색, 자동 인덱싱은 없다.

고유 인덱싱 경로/문서·청크, 오류, 알림 후보, 저장된 자동화 활성 상태를 센다. 일부 실패는 UNKNOWN/null과 경고다. 목록은 340px 내부에서 스크롤하며 상세는 받은 값을 사용한다.

push 상태와 ahead/behind는 설정된 upstream SHA의 조상 관계로 판단하고 CLEAN/DIRTY와 분리한다. upstream/ref 누락이나 명령 실패를 push 완료/미완료로 추정하지 않는다. 자동 fetch가 없어 로컬 참조는 오래됐을 수 있다. 최초 커밋 전에도 실제 브랜치 이름을 유지한다. 당시 해시는 15px 고정폭/전체 SHA 복사·툴팁, 메타데이터 13~14px, 배지 12px였다.

## Docker 외부 문제와 명시적 복구

2026-09-17 최초 및 09-18 재부팅 후 Docker Desktop 4.79 자체가 오래된 dockerInference/engine.sock AF_UNIX 재분석 지점을 제거하지 못해 실패했다. LocalRAG는 제한 시간 후 FAILED였다. 무조건적인 콜드 시작 성공이 아니다. 관련 보고: [Docker 문제 460](https://github.com/docker/desktop-feedback/issues/460).

사용자가 별도 승인한 복구에서 해당 검증을 위해 시작한 프로세스만 종료하고 소켓 디렉터리를 삭제 대신 이름 변경으로 보존했다. `%LOCALAPPDATA%` 아래 백업:

- Docker/run.localrag-recovery-20260917 (및 -2, -3)
- Docker/run.localrag-recovery-20260918
- docker-secrets-engine.localrag-recovery-20260917 (및 -2)
- docker-secrets-engine.localrag-recovery-20260918

Docker 데이터·컨테이너·볼륨·설정을 초기화하지 않았고 기존 백업도 건드리지 않았다. 복구는 자동 시작 기능에 포함하지 않았다. 재부팅 후 반복 성공을 보장하려면 외부 문제 해결이 필요하다.

## 09-18 검증

- 백엔드 180개/67개 묶음, 실패·오류 0, 선택 실행 1개 제외. 프런트엔드 13개/8파일 및 빌드 통과. Rust 10개 통과.
- Rust 검증: 재사용, 시작 필요, 모델 누락, 시간 초과/재시도, 충돌, 타 소유 컨테이너, 모델 정확 일치, Java 경로, 상속 stdout 시간 초과, API 경로/메서드.
- 의존 서비스보다 창이 먼저 표시됐다. 소켓 복구 후 모든 서비스를 끈 상태에서 exe가 Docker, 기존 PostgreSQL, Ollama, 모델 확인, 동봉 백엔드를 준비했다. `ONE_CLICK_STARTUP=PASS_AFTER_ENVIRONMENT_RECOVERY`.
- 모델 누락/타 포트/추상 시작 분기는 고정 테스트다. 기존 컨테이너를 삭제해 MISSING 생성 분기를 실험하지 않았다. 모델·무관한 프로세스/포트는 보존했다.
- 프로젝트 13개 요약 2,680 ms. Local_Ai_Work 문서 151개/청크 259개.
- 실제 WebView 1440×1000에서 340px 안의 1274px 목록 스크롤, 상세, 15px 해시, 가로 넘침/콘솔 오류 0 확인.
- RAG 1회 SUCCESS/출처 5개/17.7초. Agent 1회 SUCCESS/getGitStatus/LLM 28.6초. 품질 반복 평가나 설정 튜닝은 하지 않았다.
- 화면 제어 초기화 실패로 개발용 desktop-smoke.mjs의 로컬 CDP를 사용했다. 근거는 Git 제외 build/desktop-qa에 두고 배포하지 않았다.
- exe/NSIS 빌드 성공. 설치 프로그램 실행과 서명은 미검증이다.
- Room 프런트엔드 PUSHED, Local_Ai_Work UNPUSHED, 실제 내부 스크롤, Settings Retry 확인. 백엔드 PID 9080을 유지하고 모든 서비스를 재사용했다.

| 구분 | 대상 | 책임 |
| --- | --- | --- |
| INTERNAL | React UI, 동봉 백엔드 JAR, 시작 작업 | 앱에 포함 |
| BACKGROUND_MANAGED | Docker Engine, LocalRAG PostgreSQL, Ollama | 재사용·시작만 |
| USER_PREREQUISITE | Java 17, WebView2, Docker/Ollama 설치, 모델, pgvector 이미지 | 사전 준비 필요 |

공유 서비스는 창 종료 후 유지된다. Docker 소켓 재발·자체 창 표시는 외부 한계이며 설치 파일은 서명되지 않았다. 당시 구현/문서는 별도 커밋했고 push는 하지 않았다.

## 09-21 자동 시작 재검증

코드/UI/RAG 수정 없이 Docker 자동 시작만 확인했다. 공식 CLI로 Docker Desktop 4.79.0을 종료한 후 LocalRAG/백엔드/Docker 프로세스와 Engine pipe가 없음을 확인했다. 기존 PostgreSQL은 중지 상태였다. 두 시도 모두 사용자가 Docker를 수동 실행하지 않았다.

1. 15:31:32 KST exe 실행, 121 ms 후 창 확인. Docker는 자동 실행됐지만 15:31:34 로그에 `initializing Secrets Engine`, `engine.sock`, `The file cannot be accessed by the system`이 기록됐다. Engine은 준비되지 않았다.
2. 15:34:39 Docker FAILED/UNAVAILABLE, 최초 STARTING 관찰 후 약 186초. 뒤 단계는 WAITING. Docker 자체/오래된 런타임 문제(B/C)이며 시간 초과(E)는 결과다. 별도 LocalRAG 제어 결함이나 권한 거부는 입증되지 않았다.
3. Docker 정상 종료도 실패하여 경로/시작 시각을 확인한 이번 테스트 프로세스만 종료했다. LOCALAPPDATA의 소켓 전용 폴더를 `Docker/run.localrag-verification-20260921-1535`, `docker-secrets-engine.localrag-verification-20260921-1535`로 변경 보존했다. 원래 경로가 비었음을 확인했고 컨테이너·볼륨·데이터·설정·코드는 삭제/변경하지 않았다.
4. 15:36:44 exe만 재실행, 126 ms 후 창 확인, 15:37:24 모든 단계 READY(약 40초). Docker/PostgreSQL/Ollama/백엔드 자동 시작과 두 모델을 확인했다. 백엔드 PID 24248, health UP, 프로젝트 13개, 관찰된 콘솔 오류 0.
5. 컨테이너 ID `6ee48dc093b20644fd3c8703b709cafe5876a14284fcf01565bc2b48a616ac8c`, 볼륨 `local_ai_work_local_ai_postgres_data`, 전체 볼륨 이름 8개를 유지했다. 다른 기존 서비스는 Docker의 재시작 정책으로 복귀했다.

복구 후 실행은 DOCKER_AUTO_START=PASS, ONE_CLICK_STARTUP=PASS지만 복구 전은 FAIL이다. 영구 해결이나 재부팅 후 성공 보장은 아니다.

프로세스 없음+Engine 연결 불가+소켓 존재만으로 Docker 내부 폴더를 자동 변경하지 않는다. 정상 소켓도 같은 속성이며 새로운 일치 오류 근거가 필요하고 동시 시작 경쟁도 있다. 명시적 유지보수와 시작 기능을 분리한다. FAILED/Retry는 유지하되 전용 오래된 소켓 진단 코드는 없다.

근거는 Docker 호스트 로그와 Git 제외 `build/desktop-qa/startup-failed-20260921.json`, `startup-failed-20260921.png`, `startup-recovered-20260921.json`, `dashboard.json`, `dashboard.png`다. 화면 제어 초기화 두 번 실패 후 기존 WebView 관찰기를 사용했다. 콜드 시작은 두 번만 수행했고 코드 변경이 없어 모델 평가/전체 테스트를 반복하지 않았다.
