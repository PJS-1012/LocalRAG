# 결정 0028: 유사 오류 검색과 개발 흐름 분석

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 범위와 구조

Phase 8 Step 3~5에서 기존 인프라 위에 세 가지 읽기 전용 흐름을 추가했다.

1. 유사 오류 검색: 명시적으로 저장한 Error History, `qwen3-embedding:0.6b`, 1024차원, PostgreSQL/pgvector를 사용한다.
2. 프로젝트 진행 상태: 제한된 Git 근거, 프로젝트 지식 검색 두 번, 미해결 오류 이력을 결합한다. 모델은 코드로 구성한 항목을 설명할 뿐 진행률이나 테스트 성공을 판정하지 않는다.
3. 최근 작업: 최근 커밋, 현재 상태, 제한된 변경 요약, 관련 인덱싱 Decision Log를 결합한다. since는 커밋 시각 기준이며 범위 미지정 시 5개, 기간 조회는 최대 20개 커밋을 확인한다.

각 API는 독립적으로 호출할 수 있고 Agent 도구는 같은 서비스를 재사용한다. 임의 셸, 쓰기 도구, 스케줄러, 알림, 캐시, 혼합 검색, 재정렬기, 다중 에이전트는 이 단계에서 추가하지 않았다.

## 유사 오류 검색

정확한 상태/유형/날짜/파일 필터와 의미가 비슷한 오류 검색은 분리한다. 예를 들어 "Connection refused while connecting to PostgreSQL"과 "Could not connect to database server"는 관계형 문자열 필터만으로 연결하기 어렵다.

`error_history_embedding`은 Error History를 삭제 연계 외래 키로 참조하고 프로젝트 ID, 모델, 차원, 원문 지문, 인덱싱 시각과 벡터를 저장한다. 벡터 원본은 반환하지 않는다. SQL은 벡터 행과 결합한 이력 행 양쪽의 프로젝트 범위를 확인한 후 코사인 유사도로 정렬한다. HNSW/IVFFlat 없이 순차 검색한다.

임베딩 입력 형식은 다음과 같이 고정한다. 아래 영문 레이블은 실제 입력 형식이므로 번역하지 않는다.

```text
Error type: <errorType when present>
Error message: <errorMessage>
Symptom: <symptom when present>
Verified root cause: <rootCause only after explicit verification>
Verified solution: <solution only after explicit verification>
```

미검증 Agent 설명과 근거 JSON은 제외하고 검색 입력은 마스킹한다. 명시적 최초 저장 및 VERIFIED/RESOLVED 전이 때 파생 벡터를 갱신한다. 내용 지문·모델·차원이 모두 같으면 재임베딩을 생략한다. 공급자 오류는 저장 결과에 표시하되 관계형 이력을 기준 데이터로 유지하고 멱등 저장으로 재시도할 수 있다. DB/스키마 실패를 숨기지는 않는다.

오류 검색 전용 기준값은 Top-K 5, threshold 0.65다. 실제 한 배치에서 PostgreSQL 접속 거부의 다른 표현 0.8380, PostgreSQL 인증 오류 0.7713, Redis 접속 거부 0.6095, Kafka 토픽 누락 0.4923, NullPointerException 0.4689였다. 소수 사례의 초기 기준이지 모든 오류에 통하는 최종 임계값은 아니다.

모든 상태가 후보가 될 수 있지만 RESOLVED는 `PAST_RESOLVED_CASE`, VERIFIED는 `PAST_VERIFIED_ANALYSIS`, UNVERIFIED는 `PAST_UNVERIFIED_ANALYSIS`로 구분한다. 미검증 원인/해결 내용은 숨긴다. 과거 사례와의 유사성은 현재 원인 입증이 아니며 별도 검증이 필요함을 알린다.

## 진행 상태와 최근 작업의 신뢰 경계

진행 상태는 기록된 커밋, 미커밋 경로, 계획 문서, 미해결 오류, 문서 불일치 후보, 확인 불가 항목으로 구분한다. 커밋은 기록된 작업이지 테스트 통과 증거가 아니다. 테스트를 실행하지 않으므로 현재 테스트 상태는 확인 불가로 표시한다. README와 Decision Log를 모두 확보했을 때만 Phase 차이를 보고한다. 코드만 바뀌고 README가 안 바뀐 경우에도 오래된 문서라고 확정하지 않고 검토 후보로 제시한다.

프로젝트 지식은 인덱스 시점의 자료다. 없는 출처는 근거 한계로 표시한다. 커밋 메시지·경로·검색 텍스트는 신뢰하지 않는 자료이며 모델 입출력에서 마스킹한다. 설명은 저장하거나 검증된 계획으로 취급하지 않는다. 설명 생성 실패 시에도 구조화된 근거는 반환한다.

TODAY는 호스트의 현지 날짜 기준이다. since 결과는 20개 상한 때문에 불완전할 수 있다. 변경 영역은 패키지/최상위 경로 단위이며 의도를 임의로 추정하지 않는다. Decision Log 검색 결과가 없다고 의사결정이 없었다고 해석하지 않는다.

## 도구 선택과 품질

`findSimilarErrors`, `analyzeProjectProgress`, `summarizeRecentDevelopment`는 서비스 호출 전 정확한 프로젝트 일치를 검사한다. 실제 qwen 평가에서 각 질문에 새 도구를 한 번씩 선택했고 DB 질문에는 기존 DB 도구만, HashMap 질문에는 도구를 선택하지 않았다. 다섯 자식 프로세스는 모두 90초 이내였다.

도구 선택은 통과했지만 설명은 완전하지 않다. 유사 오류가 없는 사례에서 제한된 부재를 일반화하고 불필요한 도구를 제안했으며 최근 작업 서비스는 한 번 영어로 답했다. 저장된 검증 상태/서비스 결과와 분리해 품질 한계로 남기고 반복 평가하지 않았다.

## 당시 측정과 검증

- 1024차원 입력 6개 임베딩: 2,335.8 ms. 고정된 통합 테스트 벡터 조회: 0~2 ms.
- 실제 LocalRAG 진행 상태: 근거 448 ms, RAG 193 ms, LLM 17,780 ms, 전체 18,490 ms.
- 최근 작업: Git 267 ms, RAG 2,881 ms, LLM 13,247 ms, 전체 16,421 ms.
- qwen Agent 자식 실행 전체 시간: 유사 오류 53,169 ms, 진행 상태 49,140 ms, 최근 작업 33,091 ms, DB 32,332 ms, 도구 없음 22,727 ms.
- 전체 회귀: 56개 묶음, 159 통과, 선택 실행 실제 모델 테스트 1개 제외, 실패 0.

파일시스템 캐시·모델 준비·시스템 부하에 영향을 받는 단일 실행값이다. 이 값만으로 성능 인덱스, 캐시, 모델 변경이 필요하다고 판단하지 않았다. 세부 평가는 [평가 기록](../phase8-step3-5-evaluation.md)에 있다.
