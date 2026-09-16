# LocalRAG Interview Stories

## A. Unity Library false-positive scan

**문제:** `Toy_Sports_Day`가 UNKNOWN으로 잡혀 Unity `Library` 파일까지 Scan 대상이 됐다.

**원인:** 실제 Unity marker는 바로 아래 `toy_sports_day`에 있었지만 탐지는 Workspace direct
child만 Project로 취급했다.

**해결:** UNKNOWN direct child에 한해 depth 1만 확인하고, 부모는 Container, marker가 있는
자식만 Project root로 분리했다. depth 2 이상 추정은 금지했다.

**검증:** 포함 파일이 8,614개에서 680개로 약 92.1% 감소했고 oversized 후보는 1개에서
0개가 됐다. `Library`, `Logs`, `Temp`, `UserSettings` 제외도 정상 적용됐다.

**배운 점:** 성능 문제처럼 보이는 과도한 Scan은 병렬화보다 경계 모델 오류를 먼저 의심해야 한다.

## B. Retrieval threshold 0.50 recall loss

**문제:** 0.50 baseline에서 관련 구현이 threshold 아래로 떨어져 검색 결과가 누락됐다.

**원인:** 전역 임계값이 표현 차이가 있는 semantic query의 실제 similarity 분포보다 높았다.

**해결:** 동일한 10개 Query, corpus, Top-K 5를 유지하고 threshold만 0.45로 낮춰 효과를
분리 측정했다.

**검증:** 관련 결과가 복구됐고 존재하지 않는 Kafka query는 계속 0건이었다. 비관련 증가는
Top-K 범위에서 제한적이었다.

**배운 점:** 검색 tuning은 여러 변수를 동시에 바꾸지 않고 recall과 false positive를 함께 봐야 한다.

## C. Migration ranked above implementation

**문제:** Text-to-Vector 질문에서 실제 `EmbeddingService`보다 vector extension migration이
더 높은 similarity를 받았다.

**원인:** threshold는 관련성 통과 여부만 정하며 source type 또는 구현 의도를 재정렬하지 않는다.

**해결:** threshold를 계속 낮추지 않고 ranking 문제로 분리했다. Query Instruction 실험에서도
구현 rank는 5위에서 4위로 개선됐지만 migration 1위는 유지됨을 기록했다.

**검증:** 동일 corpus A/B 결과와 Source 의미 검토로 score 상승과 품질 개선을 구분했다.

**배운 점:** recall threshold로 ranking 문제까지 해결하려 하면 precision만 악화된다. 향후
source weighting/reranker 문제다.

## D. Docker Compose inherited output pipe

**문제:** Docker CLI timeout 후에도 Agent 평가가 끝나지 않고 `CompletableFuture.join()`에서
대기했다.

**원인:** 부모 Docker CLI는 종료됐지만 Compose plugin child가 output pipe를 계속 보유했다.

**해결:** timeout이 난 자체 CLI의 descendants를 부모보다 먼저 종료하고 output future wait도
별도로 제한했다. 컨테이너나 임의 사용자 프로세스는 종료하지 않는다.

**검증:** child가 pipe를 잡은 회귀 fixture에서 bounded return과 owned-child 종료를 확인했다.

**배운 점:** 외부 프로세스 timeout은 parent 종료만이 아니라 process tree와 stream 수명까지
경계로 관리해야 한다.

## E. JSONB dirty checking and optimistic-lock version

**문제:** Error History 저장 응답 이후 transaction commit에서 version이 한 번 더 증가했다.

**원인:** mutable JSONB evidence가 Hibernate dirty checking 대상이 되어 추가 update를 만들었다.

**해결:** write-once JSON evidence를 immutable로 지정하고 persistence 결과와 commit 이후
version을 같은 값으로 검증했다.

**검증:** Testcontainers PostgreSQL에서 returned/committed version 일치, stale version 409와
Verification Audit을 회귀 테스트했다.

**배운 점:** JSON column의 객체 변경 가능성은 optimistic locking과 API version 계약까지 영향을 준다.

## F. Automation no-change economy

**문제:** 변화가 없는 Project도 Progress와 Activity LLM 분석을 반복하면 로컬 GPU 시간을 낭비한다.

**원인:** schedule 실행과 비싼 분석 실행 사이에 deterministic change gate가 없었다.

**해결:** Git, Error History와 Index metadata fingerprint를 만들고 동일하면 `NO_CHANGE`로 끝내
두 model workflow를 호출하지 않게 했다.

**검증:** no-change service baseline은 2 ms, LLM 호출 0회였다. 실제 Progress 약 18.5초와
Activity 약 16.4초 기준으로 실행당 약 34.9초 작업을 피한다.

**배운 점:** 로컬 AI 자동화는 모델 최적화 전에 불필요한 호출 자체를 제거하는 것이 가장 크다.
