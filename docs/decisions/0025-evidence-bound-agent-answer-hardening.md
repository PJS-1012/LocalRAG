# 0025. 근거 범위에 맞춘 Agent 답변 보강

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 당시 상태와 범위

Phase 7 Step 4.1을 제한된 보강 단계로 종료했다. 기능적 근거/인용 계약은 승인하되
qwen3:8b의 남은 문장 품질 문제 때문에 같은 실제 평가를 반복하지 않기로 했다.
새 도구·검색·쓰기·자동화·Multi-Agent·UI·성능 최적화는 추가하지 않았다. Agent 문맥은 16,384다.

## 사실·추론·미확인 정책

확인 사실/추론/확인 한계를 나누고 확신은 확인됨, 현재 근거상 가능성 높음, 가능성 있음,
현재 근거로 판단 불가로 제한한다.

- 일부 점검을 전체 시스템 건강으로 확대하지 않는다. JDBC/pgvector 연결은 DB 전체 정상의 증거가 아니다.
- 컨테이너 상태/종료 코드만으로 원인을 확정하지 않는다.
- `NO_LOG_FILES`는 허용 범위에서 로그를 못 찾았다는 뜻이며 도구 실패/오류 부재가 아니다.
- Git 시간 순서와 동시 관찰 오류만으로 인과를 단정하지 않는다.
- 실패 도구를 이유로 무관한 하위 시스템까지 조회하거나 네트워크·권한·경로·소켓·설정을 원인으로 만들지 않는다.
- 명령·권고·다음 단계·가능한 원인 목록을 덧붙이지 않는다.

성공 JSON에 `evidenceAvailable`과 도구별 `answerBoundary`를 추가한다.
실패 기본값은 계속 제거하고 이유 미확인/생략값 해석 금지를 명시한다.

## 인용 계약

기존 `RagCitationValidator`가 S1과 K1-S1을 모두 인식한다.
`AgentToolExecution`은 성공한 `searchProjectKnowledge`의 ID·마스킹 경로·줄만 수집한다.
본문은 제한된 도구 결과 내부에 두고 API Citation 메타데이터에 복제하지 않는다.
`AgentChatResponse`에 `knowledgeSourceCount`, `knowledgeSources`, `usedSourceIds`, `invalidSourceIds`를 추가한다.
없는 ID/근거가 있는데 인용 없음은 경고다. 실패/빈 검색은 유효 ID를 제공하지 않는다.
실행 상태 사실은 `toolsUsed`/`toolCalls`로 추적한다.

## 합성 회귀

DB 범위 제한, 실패 DTO 기본값 제거, 로그 부재 의미, Git 비인과성,
K1-S1 성공 매핑, 누락/가짜 인용 경고, 본문 비복제, 도구 실패 격리/성공 기록 보존을 검증했다.

## 첫 실제 평가

6개 시나리오가 4분 48초에 완료됐다. 평균 도구 2,239.7/LLM 43,045.3/전체 45,328.3 ms였다.

| 사례 | 판단 |
|---|---|
| 전체 환경 | 관찰은 맞으나 종료 컨테이너의 일반 원인 추정, PARTIAL |
| DB 원인 | DB 실패는 미확인 유지, 컨테이너/설정 추측·조언, PARTIAL |
| Docker 원인 | 상태는 맞으나 네트워크/내부 오류·명령 생성, FAIL |
| 로그+Git | 커밋 인과 단정 없음, NO_LOG_FILES를 실패로 오해/추가 확인 조언, PARTIAL |
| 지식 구조 | PostgreSQL 중단으로 검색 실패, 가짜 K1-S1 탐지: validator PASS/답변 FAIL |
| DB 성공+로그 실패 | DB 유지, 실패 원인 생성/언어 혼합, PARTIAL |

Prompt만으로 근거 충실도를 보장하지 못하며, 생성 후 잘못된 ID를 탐지할 수 있음을 확인했다.

## 마지막 제한 재평가와 중단

기존 `local-ai-postgres`를 재생성/볼륨 변경 없이 시작해 healthy를 확인한 뒤 같은 6개를 재평가했다.
구조화한 `answerBoundary` 이후 Q1/Q10/Q11이 `build/reports/agent-step4-1/evaluation-selected.json`에 기록됐다.
평균 도구 475/LLM 42,739/전체 43,287.7 ms였다.

- Q1: 4개 환경 결과의 범위를 유지하고 연결≠전체 정상 표시, PASS.
- Q10: getDatabaseStatus만 호출, 전체 정상 단정은 없지만 무관한 원인/도구 권고, PARTIAL.
- Q11: 상태는 맞으나 getRecentErrors 추가와 근거 없는 원인 목록, PARTIAL.

Q12 로그+Git 인과 사례가 qwen 응답을 비정상적으로 오래 기다렸다.
확인 시 JSON은 앞 3개뿐이었고 Gradle wrapper/daemon/Test worker는 살아 있었지만 Git/Docker 자식 대기는 없었다.
실행을 중단해 exit code 1을 확인하고 Gradle/Test worker 트리가 종료됐음을 확인했다. 추가 실평가는 하지 않았다.
성공 Knowledge 사례까지 도달하지 못했으므로 그 성공/잘못된 인용 경로는 단위 테스트 근거로 구분한다.

## 자동 테스트와 종료

최종 전체 Gradle은 33초, 135사례 중 134 통과/실모델 1 제외/실패·오류 0이었다.
이전 Docker 실패의 Application/IndexRepository/SearchRepository 통합 테스트도 복구된 Engine에서 별도 통과했다.
테스트를 삭제·약화·추가 제외한 것이 아니라 환경을 복구한 결과다.

도구 선택·부분 실패 보존·기본값 격리·인용 검증·API 추적성을 근거로 Step 4/Phase 7 종료를 권고했다.
남은 기록: 근거 없는 가능 원인/조언, 모델 언어 품질, 당시 평균 LLM 약 43초,
지식 검색 순위. 구조화 응답·결정적 주장 정책·모델 비교·평가별 timeout·결과 압축 등의 후보는
당시 후속 검토 항목이며 구현 완료 목록이 아니다.
