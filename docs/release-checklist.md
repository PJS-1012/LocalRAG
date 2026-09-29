# 배포 점검표

## 최신 배포 기준 — 2026-09-28

기능 코드 기준은 `f607670`이다. 2026-09-29 문서 정리에서는 코드를 바꾸거나 아래 테스트를 재실행하지 않았다. 과거 단계의 NOT_DONE/DEFERRED는 당시 상태이며 현재 Git 상태나 기능 상태로 해석하지 않는다.

| 항목 | 결과 | 근거와 한계 |
| --- | --- | --- |
| 백엔드 테스트 | PASS | 215개 중 214 통과, 선택 실행 1개 제외, 실패 0 |
| 프런트엔드 테스트 | PASS | 43/43 통과 |
| Rust 테스트 | PASS | 10/10 통과 |
| 배포 빌드 | PASS | 백엔드 JAR, Vite, Tauri 실행 파일, NSIS 재빌드 |
| 배포본 일치 기록 | RECORDED | `build/release-20260928-manifest.json`에 해시 기록; Git 제외 산출물 |
| 불완전 응답 | PASS_WITH_LIMITATION | 종료 미확인 응답을 LLM_FAILED로 차단; Unity 생성 중단 자체는 미해결 |
| 근거 밖 심볼 | PASS_WITH_LIMITATION | 한국어 조사가 붙은 허위 클래스 설명 차단; 의미 전체를 검증하지는 않음 |
| 대표 질문 5개 | MIXED | Spring 소개 정상, 클래스 설명 일부 의미 오류, HashMap 일반 지식 오류; 전체 품질 PASS 아님 |
| 설치·서명 | LIMITED | NSIS 빌드와 실제 설치 검증은 다름. 서명 없음 |
| 문서 | UPDATED | 최신 코드와 과거 측정값 구분, 한글화, 경로·식별자 보존 |

세부 결과는 [최종 품질 기록](decisions/0036-final-quality-guards-and-release.md), 코드 대조는 [사실 검증 문서](portfolio-facts-audit.md)를 따른다. 기능/품질 개발은 남은 한계를 기록하고 종료했다.

## Phase 11 — 2026-09-16 당시 검증

| 항목 | 결과 | 당시 근거 |
| --- | --- | --- |
| Windows 실행 도우미 | PASS | 정상 시작, 중지된 PostgreSQL 시작, 반복 실행 재사용 |
| Docker/Ollama/PostgreSQL 안내 | PASS | 고정 대상, 명시적 실패 코드, 필수 모델 확인 |
| 백엔드 테스트 | PASS | 173개, 실패/오류 0, 선택 실행 1개 제외 |
| 프런트엔드 테스트 | PASS | 6파일 7개 |
| 배포 빌드 | PASS | Vite 모듈 49개, JS/CSS 수치 기록 |
| Flyway | PASS | V1–V6, 스키마 버전 6 검증 |
| Chrome 화면 | PASS | 9화면+유사 오류 탭, 1440px에서 콘솔/넘침 문제 없음 |
| RAG 종단 간 흐름 | PASS | 출처 5개/사용 3개/잘못된 인용 0개 |
| Agent Git | PARTIAL | 요청 완료 후 외부 실행기가 응답 JSON을 반환하지 못함 |
| 오류 이력/유사 오류 | PASS | 빈 결과 정상 응답 |
| 진행 상태 | PASS | 실제 qwen 응답과 구조화된 항목 |
| 자동화/이력/알림 | PASS | LLM 생략 실행, 이력 2개/후보 2개 |
| 의존성 점검 | PASS_WITH_BACKLOG | npm High/Critical 없음, Vitest 중간 심각도 2건 |
| README/구조 문서 | PASS | 당시 배포·시작·신뢰 경계 반영 |
| Tauri | DEFERRED | 당시 Cargo 없음, 웹 UI와 실행 도우미 기준 |
| Push | NOT_DONE | 당시 별도 승인 필요 |

## Phase 12 데스크톱 패키징 — 2026-09-16

| 항목 | 결과 | 당시 근거 |
| --- | --- | --- |
| Rust/MSVC/WebView2 | PASS | Rust 1.98.1 MSVC, VS C++ 도구, WebView2 |
| Tauri 컴파일 | PASS | cargo check, Rust 테스트 2개 |
| 백엔드 JAR | PASS | Java 17 localrag-backend.jar, 67.84 MB |
| 실행 파일 | PASS | Windows x64, 11.49 MB |
| 설치 파일 | PASS | 서명 없는 NSIS, 62.46 MB |
| 데스크톱 보안 경계 | PASS | core 권한만, 고정 루프백 API, CSP |
| 백엔드 준비 | PASS | 식별된 재사용/시작/포트 충돌 판단과 제한된 확인 |
| 배포 창 | PASS | LocalRAG 창과 `http://tauri.localhost/` WebView |
| 프로젝트/요약 | PASS | 13개, Local_Ai_Work 선택, Docker/DB/Ollama AVAILABLE |
| RAG | PASS | SUCCESS, 출처 5개, UI 오류 없음 |
| Agent Git | PASS | 답변과 getGitStatus 실행 기록 |
| 오류/진행 상태 | PASS | 빈 이력과 진행 상태 동작 표시 |
| 자동화 | PASS | 수동 실행 SUCCESS, LLM 생략 |
| 실패 분기 | PASS_WITH_LIMITATION | 제한/단위 분기 검증; 정상 서비스를 일부러 중단하지 않음 |
| 백엔드 테스트 | PASS | 174개, 실패/오류 0, 선택 실행 1개 제외 |
| 프런트엔드 테스트 | PASS | 7파일 11개 |
| 개발 명령 | CONFIGURED | 기존 Phase 11 실행기가 5173 사용 중 |
| 화면 제어 | FALLBACK | 작업공간 도구 오류로 실제 WebView를 로컬 CDP로 확인 |
| 코드 서명 | DEFERRED | SmartScreen 경고 가능 |
| Push | NOT_DONE | 당시 별도 승인 필요 |

## 원클릭 실행 후속 검증 — 2026-09-18

| 항목 | 결과 | 당시 근거 |
| --- | --- | --- |
| 네이티브 시작 | 복구 후 PASS | Docker/PostgreSQL/Ollama/모델/동봉 Java 17 백엔드 READY |
| 재부팅 후 성공 보장 | 외부 문제로 불가 | Docker AF_UNIX 소켓 재발; 자동 복구/초기화 없음 |
| 시간 초과/재시도 | PASS | 실제 Docker 실패 화면, 고정 테스트, 재시도 시 백엔드 PID 유지 |
| 프로젝트 요약 | PASS | 13개, 프런트 요청 1회, DB 일괄 조회 4회 |
| Git | PASS | upstream, PUSHED/UNPUSHED, clean/dirty, ahead/behind, upstream 없음 |
| 화면 | PASS | 배포 WebView 1440×1000, 내부 스크롤, 가로 넘침/콘솔 오류 없음 |
| RAG/Agent | PASS | 실제 질문 각 1회, 출처 5개/getGitStatus |
| 백엔드 | PASS | 180개, 실패/오류 0, 선택 실행 1개 제외 |
| 프런트엔드/Rust | PASS | 13개/10개 |
| 산출물 | PASS | 배포 실행 파일과 서명 없는 NSIS 생성 |
| 설치 실행 | NOT_TESTED | 빌드만 확인 |
| Push | NOT_DONE | 당시 별도 승인 필요 |

09-21 추가 검증에서는 복구 전 자동 시작이 실패했고 소켓 폴더를 보존하는 외부 복구 후 exe만으로 약 40초에 모든 서비스가 준비됐다. 영구 해결은 아니다. [실제 시각과 데이터 보존 근거](decisions/0033-one-click-startup-and-project-overview.md)를 참고한다.

### 초기 실행 도우미 시나리오 범위

- A: 실행 중인 서비스는 실제 반복 실행에서 모두 재사용했다.
- B: 초기 검증에서는 Docker Desktop을 일부러 끄지 않고 시작 분기와 DOCKER_NOT_RUNNING 시간 초과를 고정 테스트로 확인했다. 이후 실제 중지 상태 검증과 구분한다.
- C: 중지된 local-ai-postgres만 실제 시작했고 볼륨을 보존했다.
- D: Ollama 사용 불가 분기는 고정 테스트로, 고정 ollama.exe 시작과 두 모델 확인은 실제 실행으로 검증했다.
- E/F: 백엔드/프런트엔드 PORT_IN_USE는 무관한 프로세스 종료나 포트 점유 없이 고정 테스트로 확인했다.
