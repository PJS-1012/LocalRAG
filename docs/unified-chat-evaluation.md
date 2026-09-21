# Unified Chat 실제 평가 — 2026-09-22

## 범위와 판정

- Project: `Room_Reservation/RoomReservation`. 기존 대표 11문장과 변형 4문장, 순차 1회 최종 평가.
- qwen3:8b / qwen3-embedding:0.6b, Top-K 5, threshold 0.45, Query Instruction ON 유지.
- 기존 RAG formatted context 8,000자 유지. Unified에서는 별도의 bounded live Project evidence를 합친다.
- HTTP 성공, Tool 실행 성공, 답변 품질은 서로 다른 지표다.
- PASS: 질문의 핵심에 근거로 답하고 필요한 인용이 의미적으로 연결됨.
- PARTIAL: 유용한 근거/답변이 있지만 인용 누락·범위 부족·일부 미검증 주장이 있음.
- FAIL: 질문 핵심인 현재 구현 상태를 확인하지 않고 높은 완성도를 단정함.
- 수동 검토 결과: **PASS 4 / PARTIAL 10 / FAIL 1**. API는 15/15 HTTP 200,
  모델 응답 상태는 SUCCESS 8 / SUCCESS_WITH_WARNINGS 7이다. 후자를 품질 통과율로 사용하지 않는다.

## 대표 질문

K = searchProjectKnowledge, P = analyzeProjectProgress, A = summarizeRecentDevelopment,
G = getGitStatus, E = getRecentErrors. Tool 시간은 비-LLM 근거 수집 합계이며,
LLM 시간은 Tool 시간을 제외한 모델 왕복/생성 시간이다.

| # | 질문 | 실제 Tool | 근거 수집 / LLM / 전체 (ms) | 판정 | 근거와 한계 |
|---|---|---|---|---|---|
| 1 | 이 프로젝트의 주요 구조와 현재 구현 상태를 설명해줘 | K | 4,851 / 45,820 / 50,671 | FAIL | 실제 README/build/code 6개 확보. Progress를 호출하지 않고 대부분 기능 완료·높은 완성도를 단정. 인용 없음 |
| 2 | 이 프로젝트 처음 보는데 전체적으로 설명해줘 | K | 518 / 27,464 / 27,982 | PARTIAL | 실제 Java/Spring/예약 프로젝트 설명. 인용 없음 |
| 3 | 이 프로젝트 인수인계 받았다고 생각하고 설명해줘 | K | 410 / 54,486 / 54,896 | PARTIAL | 실제 구조/파일 확보. 최근 작업·미완료 확인 부족, 일부 endpoint 표현은 반환된 근거로 검증 불가. 인용 없음 |
| 4 | 주요 기능이 뭐야? | K | 922 / 18,087 / 19,009 | PARTIAL | README의 예약·락·캐시 설명과 [K1-S1] 연결. AdminController 주장에는 별도 코드 근거 [K1-S4]가 필요하나 README 인용만 사용 |
| 5 | 현재 어디까지 구현됐어? | P | 1,000 / 16,259 / 17,259 | PARTIAL | 실제 Git/Workflow 근거 사용. 빈 계획·진행 목록을 “없음”으로, 커밋 기록을 기능 완료로 해석하는 문제 |
| 6 | 최근 작업이랑 남은 문제 알려줘 | A + P | 901 / 14,554 / 15,455 | PARTIAL | 두 기존 서비스를 결합. 최근 작업은 근거 있음. 남은 작업 미기록과 실제 부재를 구분하지 못함 |
| 7 | 처음 보면 어떤 파일부터 보면 돼? | K | 596 / 27,167 / 27,763 | PARTIAL | 추천한 README/build/Application/AdminController/AuthService는 실제 파일. 인용 없음 |
| 8 | ReservationLockService 어디 있어? | K | 753 / 26,181 / 26,934 | PARTIAL | 실제 구현·테스트·설정 경로 회수, 총 11 source. 답변의 inline 인용 없음 |
| 9 | 현재 Git 상태 알려줘 | G | 66 / 7,050 / 7,116 | PASS | 실제 main / clean 근거와 일치. RAG 0건이어도 정상 답변 |
| 10 | 왜 DB 오류 난 것 같아? | E + K | 461 / 29,457 / 29,918 | PARTIAL | 로그 없음과 원인 불명을 표시, LocalRAG DB와 Project DB 구분. DB 상태 Tool 미선택, 일부 설정값 주장은 반환된 발췌에서 확인 불가, 인용 없음 |
| 11 | Java HashMap 설명해줘 | 없음 | 0 / 7,515 / 7,515 | PASS | 일반 지식 답변, 불필요한 파일/Tool 조회 없음 |

## 표현을 바꾼 질문

| # | 질문 | 실제 Tool | 근거 수집 / LLM / 전체 (ms) | 판정 | 관찰 |
|---|---|---|---|---|---|
| 12 | 이거 뭐하는 프로젝트임? | K | 435 / 16,135 / 16,570 | PASS | 실제 목적/구성 설명. README/build/코드 인용 연결 |
| 13 | 처음 왔는데 뭐부터 보면 됨? | K | 630 / 23,456 / 24,086 | PASS | 실제 6개 파일과 각 source ID 연결 |
| 14 | 대충 어디까지 만들어짐? | P | 737 / 17,732 / 18,469 | PARTIAL | 의미상 Progress 선택 성공. 미기록 작업을 없음으로 단정하는 동일 문제 |
| 15 | 전체 흐름 좀 알려줘 | K | 465 / 28,153 / 28,618 | PARTIAL | README 기반 전체 흐름. 인용 없음 |

## Citation / hallucination

- 존재하지 않는 citation ID: 최종 15개 중 0건.
- Knowledge를 조회했으나 citation을 전혀 쓰지 않은 답변: **7개** (#1,2,3,7,8,10,15).
  UI에서 경고를 표시하고 실제 source 경로/행과 Tool evidence를 별도로 제공한다.
- Citation ID 존재 검사는 의미 검증이 아니다. #4의 README 인용 하나가 코드 클래스 주장까지
  모두 뒷받침하지는 않는다. 이를 PARTIAL로 판정했다.
- #12,13의 인용은 실제 반환된 파일과 주장 연결을 검토했다.
- Git/Workflow 답변에는 toolCalls, toolsUsed, sanitized evidence가 남는다.
- “완료”, “남은 문제 없음”은 작업 목록과 커밋만으로 입증할 수 없다. #1,5,6,14에 문제를 기록했다.
- 모델이 근거를 조회했다고 해서 모든 문장이 grounded인 것은 아니다. #3의 endpoint 표현,
  #10의 설정값 표현은 특히 검증되지 않은 주장으로 남긴다.
- 초기 개발 검증에서는 Tool 없이 Python/main.py를 지어낸 답변도 있었다. 해당 초안은 최종 기준이 아니다.
  근거 없는 no-tool Project 초안을 버리고 Tool 선택을 한 번만 재시도하는 guard를 추가했다.
  최종 인수인계 응답은 실제 Java/Spring 근거를 조회했다.
  일반 지식의 [GENERAL] 선언은 모델 계약이지 독립적인 의미 검증기는 아니므로 한계가 남는다.

## 실패/지연 관찰 및 처리

- 초기 기본 thinking 상태에서 첫 broad 질의가 120초 제한을 넘었다. Unified 호출에만
  thinking OFF와 최대 출력 1,200 tokens를 적용했다. 기존 RAG/Agent 호출은 변경하지 않았다.
- 위 변경과 no-tool guard 후 최종 15개를 한 번 평가했다. 최종 개별 시간은 7.116–54.896초.
- 최종 품질을 맞추기 위해 반복 샘플링하거나 prompt/threshold를 계속 조정하지 않았다.
- 추가로 실제 release WebView에서 Enter Git 질문 1회: getGitStatus, 약 14.9초, console 오류 0.
  이 UI smoke는 위 15개 품질 집계에 넣지 않았다.

## 성능 기준값

| 항목 | 결과 |
|---|---|
| Workspace summary | 13 Projects, 한 API 응답 |
| Cold overview | client 11,183 ms / server 10,961 ms |
| Warm overview | client 3,128 ms / server 3,116 ms |
| Language 계산 | 파일 수 기준, content/LLM 조회 없음, metadata cache TTL 5분 |
| RoomReservation 언어 | Java 82.5%, JavaScript 8.8%, YAML 6.1%, SQL 2.6%; 분모 114 files |
| 상세 열기 | 추가 network request 0 |
| 15개 평균 Tool 시간 | 849.67 ms |
| 15개 평균 LLM 시간 | 23,967.73 ms |
| 15개 평균 전체 시간 | 24,817.40 ms |

Cold 11초는 즉각적인 Dashboard 응답이라고 볼 수 없다. Cache hit에서도 Git 등 기존 순차 조회가
남는다. 이번에는 병렬화/파일 감시/추가 LLM 최적화를 구현하지 않았다.
모델 로딩·GPU 상태·OS filesystem cache에 따라 시간은 달라진다.

## 재현 및 원본

- 순차 평가: `node frontend/scripts/unified-evaluation.mjs`.
  실제 모델을 호출하므로 일반 unit test에는 포함하지 않는다.
- 로컬 원본(미커밋 build artifact): `build/unified-evaluation/query-01.json` ~ `query-15.json`,
  `summary.json`, `overview-0.json`, `overview-1.json`.
- Desktop smoke: `frontend/scripts/desktop-smoke.mjs unified-ui` / `unified-enter`.
  개발용 loopback WebView debugging을 켠 release에서만 실행하며 제품 기본 설정에는 디버그 포트를 추가하지 않는다.
- 결과/스크린샷: `build/desktop-qa/unified-*.json`, `unified-*.png`.
- 출력 원본에는 Project 근거가 포함되므로 Git에 자동 추가하지 않는다.

## 결론

하나의 Chat에서 코드/Project/Git/Progress/Activity/일반 질문을 선택하는 연결과 Korean-first UI는
구현·검증했다. 그러나 인수인계의 상태 근거 수집, citation 준수와 미완료 판단은 성공 기준을
완전히 충족하지 않는다. **전체 품질 완료 선언은 보류**한다.
다음 개선은 근거 종류의 충족 여부, 미기록/미확인 상태 표현, 주장별 citation coverage에 집중해야 한다.
Hybrid/reranker/새 모델/새 Tool 추가가 이번 평가의 결론은 아니다.
