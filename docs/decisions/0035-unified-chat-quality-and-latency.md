# 0035 — Unified Chat 근거 선택·응답 품질·호출 비용 개선

- 시작: 2026-09-22
- 상태: 진행 중. 최종 성공 기준 검증 전이며 완료 선언 아님.
- 사용자 지시: 6b2d854e-8761-4655-84e9-e1eaf7839ebb/pasted-text-1.txt
- 이전 미커밋 README 한글화/입력 초기화 변경은 보존한다. Push 금지.

## 원래 목표와 검증 계획

기능 추가가 아니라 처음 보는 개발자가 프로젝트를 이해할 수 있는 답변을,
프로젝트 핵심 근거·출처와 함께 현재보다 빠르게 제공한다.
Java/Spring과 Unity/C# 양쪽 실제 프로젝트에서 검증한다.

| 요구 | 완료 증거 | 현재 |
|---|---|---|
| A: 모델 HTTP 요청별 횟수/목적/모델/입출력 크기/시간/성공, 단계별 시간 | unit test + 실제 응답 diagnostics | 계측 구현 중 |
| B: 중복 모델·구조화된 Workflow 재요약 제거 | 실제 before/after 호출 수, 회귀 테스트 | 조사 중 |
| C/D: 프로젝트 코드 우선·외부/생성/라이선스 억제·명시적 dependency 질문 허용 | 경로 정책 테스트 + 실제 Unity source 목록 | 미완료 |
| E/F: broad 최소 근거 조합·필요할 때만 bounded decomposition·중복 제거 | routing/context 테스트 + broad 실제 질문 | 미완료 |
| G/H: 미지원 주장 억제·context 내 citation 연결·미확인 표시 | guard 테스트 + 수동 주장별 검토 | 미완료 |
| I/J: context/중복/출력 길이 감소 | 실제 입력·출력 크기 및 시간 비교 | 미완료 |
| K/L/M/N: Java/Unity 실제 질문 16종 및 변형 평가 | per-query JSON/평가표, origin/citation/call/time 수치 | 미완료 |
| O: 같은 PC/model의 의미 있는 지연 감소, 좁은 질문 회귀 없음 | before/after matched set | 미완료 |
| P: 기본 UI는 간결, 처리 경로·근거·시간 상세 | Frontend test + 화면 확인 | 미완료 |
| R: Backend/Frontend/Rust/Tauri, 새 6종 회귀 검증 | 최신 실행 결과 | 미완료 |
| S/T: 원인/측정/한계 문서 및 논리적 커밋, push 없음 | 최종 diff/로그/Git 상태 | 미완료 |

금지 유지: Hybrid/BM25/reranker/model 변경/threshold 변경/multi-agent/MCP/새 Tool·Error·Automation/대규모 UI 변경.
절대 20–30초를 맞추기 위해 근거·품질을 희생하지 않는다.

## 수정 전 실행 경로 확인

- Unified: Controller → AgentChatService → ChatService → Spring AI OllamaChatModel.
- Progress.collect / Activity.collect는 이미 별도 요약 LLM을 생략한다.
- 설치된 Spring AI 1.1.8 소스에서 internalCall은 Ollama 응답의 Tool 호출을 실행한 뒤
  다시 internalCall을 호출한다. 따라서 ChatService 호출 1회는 실제 모델 호출 1회가 아니다.
- 기존 llmDurationMillis는 Tool 시간 차감값일 뿐 개별 모델 요청 수나 목적을 증명하지 않는다.
- 기본 ProjectBrief는 Java 파일명 위주 우선순위여서 Unity 프로젝트 코드의 의미 있는 본문을
  확보하지 못할 수 있다. RAG/Brief 공통 sourceOrigin 필터는 없었다.
- Progress의 completed 항목은 실제로 Recorded commit 문자열이며 완료 기능 증거가 아니다.
- 단일 질문에 같은 Git 상태나 Knowledge를 여러 경로로 수집할 가능성이 있다.

## 계측 설계

우선 동작을 변경하지 않고 Spring Boot RestClientCustomizer로 실제 Ollama /api/chat
요청을 관찰한다. Spring AI 내부 Tool loop와 HTTP retry도 각각 센다.
메시지/도구 schema 길이, 실제 provider token 수, 출력/생각 길이, 모델 준비/prefill/generation
시간을 기록한다. 원문 질문·근거·답변·비밀값은 metric/log에 저장하지 않는다.
숫자 diagnostics는 요청 응답과 로그에서 확인할 수 있다.

Tool 선택 응답은 TOOL_SELECTION, 텍스트 최종 응답은 FINAL_ANSWER,
실패는 FAILED_REQUEST로 분리한다. 독립적인 decomposition 호출은 현재 없으므로 0과
NO_SEPARATE_DECOMPOSITION_CALL을 표시한다. 측정되지 않은 작업을 임의의 시간으로 채우지 않는다.
vector search는 DB 검색, query embedding은 별도 부분합이다. Tool/Progress/evidence 수치는
중첩된 구간이며 단순 합산하지 않는다.

## Before / After

Before 원본: `build/unified-quality/before/` (2026-09-22, 계측만 적용한 JAR).
After 원본: `build/unified-quality/after-20260924/` (2026-09-24, 순차 평가).
다른 프로젝트 원문이 담긴 JSON은 build 아래 로컬 검토 자료로 보존하며 Git에는 추가하지 않는다.

| Before 사례 | 전체 ms | LLM ms | 근거 ms | 마지막 입력 문자 | 모델 호출 |
|---|---:|---:|---:|---:|---:|
| Spring 소개 | 64,419 | 60,099 | 4,053 | 23,887 | 2 |
| Spring 인수인계 | 65,355 | 64,356 | 940 | 18,690 | 2 |
| Spring 회원가입 | 17,553 | 16,388 | 1,113 | 19,500 | 2 |
| Spring ReservationLockService | 28,205 | 27,869 | 283 | 23,244 | 2 |
| Unity 소개 | 26,647 | 26,143 | 461 | 15,590 | 2 |
| Unity 인수인계 | 39,064 | 38,923 | 100 | 15,539 | 2 |
| Unity 처음 볼 파일 | 19,786 | 19,645 | 100 | 15,539 | 2 |

Before 각 마지막 호출에는 도구 schema 8,553자가 별도로 붙었다. Unity 3개 사례 모두
8개 파일 중 TextMesh Pro 관련 자료 5개를 사용했고, 프로젝트 런타임 코드 대신 Editor 코드가 선택됐다.
Spring 일부 사례에는 performance/k6/results 생성 결과가 포함됐다. 초기 JS 평가 분류기가 이
경로를 PROJECT로 세었으므로 원본 before의 origin 카운트를 그대로 신뢰하지 않는다. 개선 코드에서는
GENERATED로 분류한다. 비교 보고서는 경로 원문을 재검토한다.

Spring 첫 before 호출은 모델 로딩 4,103ms, 마지막 생성 46,808ms였다.
첫 after는 모델 로딩 6,082ms, 마지막 생성 7,334ms, 전체 37,126ms, 마지막 입력 6,870자였다.
첫 after 질의 시작 시 백엔드 전체 테스트는 이미 종료됐다. 콜드 시작과 OS/모델 cache 영향이 있으므로
한 사례만으로 평균 향상률을 주장하지 않는다. 모든 사례는 동일 PC/model이며 corpus 재인덱싱은 하지 않았다.

## 구현 결정 (2026-09-24)

- 기존 통합 채팅의 Tool 선택을 Spring AI 내부 재귀 루프에서 분리한다. 일반 지식은 1회,
  프로젝트 근거 답변은 선택 1회 + 최종 생성 1회. HTTP retry는 실제 HTTP 계측에 별도로 잡힌다.
  근거 없는 초안의 자동 재시도도 제거한다. 이미 정상적으로 2회였던 before 사례를 1회로 줄였다고 주장하지 않는다.
- 최종 생성에는 Tool schema와 버린 초안을 보내지 않는다. 도구 결과 중 실제 source 본문과 위치,
  관찰값, 실패 상태, unknown은 유지하고 중복 요약/통계/경로 나열은 생략한다.
- Unified 전용 context window 16,384→8,192, 출력 상한 1,200→650 tokens.
  모델/embedding/threshold/Top-K/청크 설정은 유지한다. 실제 `/api/ps`에서 after qwen3:8b의
  size와 size_vram이 모두 6,186,378,198바이트로 관측됐다. 이것만으로 모든 시간 차이를 GPU에 귀속하지 않는다.
- 기존 searchProjectKnowledge의 선택 인자 intent로 CODE_SPECIFIC/PROJECT_OVERVIEW/ONBOARDING/HANDOVER를 구분한다.
  한국어 특정 문구 분기나 새 Agent Tool을 만들지 않는다. HANDOVER는 기존 Progress/Activity의
  구조화된 결과를 사용하며 별도 중간 요약 모델은 호출하지 않는다. Progress가 기존 Error History를 수집한다.
- 경로 기반 sourceOrigin은 읽기 권한이 아니다. 실제 read는 기존 Reader/ScanPolicy/크기/경계 검사를 그대로 거친다.
  생성물은 제외, 제3자 자료는 사용자가 dependency를 직접 명명했을 때 허용한다. 모델의 검색문 재작성으로
  허용 범위를 넓히지 않도록 마지막 필터는 사용자 원문을 사용한다.
- Broad는 live 핵심 파일 먼저, code-specific은 RAG 먼저. 최대 6 source / 파일당 최대 2 비중첩 구간 /
  근거 본문·path 약 6,500자. 중복/겹치는 chunk는 제거하며 예산 초과 chunk는 중간 절단하지 않는다.
  live excerpt는 기존처럼 정확한 연속 행 범위를 표시하는 제한된 표본이다.
- 요청 내부 Git/RAG 동일 인자 조회를 재사용한다. 전역 결과 캐시나 다른 프로젝트/요청으로 결과를 공유하지 않는다.
- 최종 prompt는 구현 완료/실행 성공과 코드 관찰을 구분한다. 지식 전용 답변에 citation이 전혀 없거나
  존재하지 않는 K*-S*를 인용하면 재모델 호출 없이 답변을 보류한다. 의미적 진실을 자동 검증하는 기능은 아니다.
- UI의 기존 근거 패널에 접힌 처리 경로·모델 호출·입출력 크기·시간만 추가한다.

## 검증 준비 중 재현된 Docker 환경 문제

2026-09-24 Docker Desktop 자체가 dockerInference와 engine.sock stale AF_UNIX 파일 접근 오류로 시작 실패했다.
기존 승인 범위에서 이번에 시작한 Docker 프로세스를 종료한 뒤 다음 런타임 폴더를 rename으로 보존했다.

- `%LOCALAPPDATA%/Docker/run.localrag-quality-recovery-20260924`
- `%LOCALAPPDATA%/Docker/run.localrag-quality-recovery-20260924-2`
- `%LOCALAPPDATA%/docker-secrets-engine.localrag-quality-recovery-20260924`

이후 Engine 29.5.3 준비 및 기존 local-ai-postgres healthy를 확인했다.
컨테이너/volume/데이터 삭제, factory reset, 설정 변경 없음. LocalRAG의 자동 복구 기능으로 추가하지 않았다.

## 남은 한계

## 작업 종료 기록 (2026-09-27)

사용자 요청에 따라 추가 튜닝과 실제 모델 재평가를 중단한다. 구현과 평가 자료는 보존하되,
품질 목표를 완전히 달성했다고 판단하지 않는다. 최종 38개 순차 평가 요청은 모두 기록되었으며
원본은 `build/unified-quality/final-20260927/`에 있다. API SUCCESS는 답변 품질 PASS가 아니다.

- 개선: broad 질문에서 third-party/generated 자료 대신 프로젝트 자체 코드 우선 선택.
  명시적 TextMesh Pro 질문에서는 third-party 자료 허용을 확인했다.
- 이후 보정: 모델이 broad로 오분류해도 원문 질의의 RAG 상위 2개를 먼저 보존하고 live brief를 결합한다.
  회원가입 DTO 검색은 복구됐으나 DTO만으로 실제 저장 동작을 단정하는 답변은 여전히 PARTIAL이다.
- 시간: 최종 Spring 소개 19.8초, 구조 8.0초, 인수인계 14.2초. 동일 질문 before는
  소개 64.4초, 인수인계 65.4초였다. 실행 시점과 캐시 상태가 달라 엄밀한 성능 보장은 아니다.
- 주요 미해결: Unity 여러 답변이 문장 중간에서 종료됐다. 해당 호출의 완료 메타데이터가
  unknown/0으로 관측되어 원인은 확정하지 않았다. 이 사례를 정상 답변의 속도 개선으로 집계하지 않는다.
- 주요 미해결: Unity에서 존재 근거 없는 ReservationLockService 설명, Git 상태만으로 정체 여부 단정,
  build.gradle 프로젝트에 Maven 필요 단정, 일부 일반 지식 오류 및 인용 누락이 남는다.
  심볼 누락 방어의 한국어 조사 경계 처리도 재검토가 필요하다. 자동 의미 검증이 완료된 상태가 아니다.
- 기존 검증: Backend 208개 중 207 통과/1 skip, Frontend 42 통과, Rust 10 통과,
  Tauri release/NSIS 빌드 성공. 마지막 trace intent whitelist/test 추가 후 재검증 및 재패키징은 미완료다.
  이번 종료 시 테스트/모델 실행을 추가 반복하지 않았다.
- 9/27 Docker stale runtime 재발은 run.localrag-quality-recovery-20260927 및
  docker-secrets-engine.localrag-quality-recovery-20260927로 rename 보존 후 회복했다.
  컨테이너/volume/데이터 삭제 없음.
- Git: 작업 변경은 미커밋으로 보존한다. push 없음. 완성된 품질 개선 릴리스로 취급하지 않는다.

결론: 이번 작업은 여기서 종료한다. 근거 선택/지연 개선은 확인했지만, Unity 답변 잘림과
근거 없는 단정은 남은 결함이다. 추가 작업은 별도 요청 시 범위를 좁혀 수행한다.
