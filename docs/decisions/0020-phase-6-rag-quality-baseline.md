# 0020. Phase 6 RAG 답변 품질 기준

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태와 평가 조건

승인됨. 검색·근거 한계를 기록한 상태로 Phase 6를 종료했다.

- 당시 `Local_Ai_Work` 재인덱싱: 문서 151개 / 저장 청크 259개.
- 텍스트/Markdown 2,000자, 코드 2,400자, 중첩 200자.
- qwen3-embedding:0.6b, 1024차원, cosine exact 검색, Top-K 5, threshold .45.
- Query Instruction ON, formatted context 8,000자, qwen3:8b.

이전 208청크에는 Step 5/6 구현이 없어 동일 baseline으로 갱신해 259개가 됐다.
평가 도중 분할·검색·Prompt 설정은 바꾸지 않았다.

## 수작업 품질 평가

PASS/부분 통과 PARTIAL/FAIL로 판단했다. N/A는 해당 없음이다. “환각” 열의 PASS는 문제 미관찰을 뜻한다.

| # | 질문 | 검색 | 근거 충실도 | 인용 의미 | 환각 | 언어 | 근거 없음 | 종합 |
|---:|---|---|---|---|---|---|---|---|
| 1 | 프로젝트 타입 탐지 | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 2 | 민감 파일 제외 | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 3 | 작업공간 경계 | PARTIAL | PARTIAL | PARTIAL | PASS | PASS | N/A | PARTIAL |
| 4 | 문서 분할 | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 5 | 청크→벡터 변환 | PARTIAL | FAIL | PARTIAL | FAIL | PASS | N/A | FAIL |
| 6 | 중복 재인덱싱 | PARTIAL | PASS | PASS | PASS | PASS | N/A | PASS |
| 7 | Unity 생성 폴더 제외 | PASS | PARTIAL | PASS | PARTIAL | PASS | N/A | PARTIAL |
| 8 | 중첩 프로젝트 탐지 | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 9 | 잘못된 UTF-8 격리 | PARTIAL | PASS | PASS | PASS | PASS | N/A | PASS |
| 10 | 없는 Kafka 설정 | PARTIAL | PASS | PARTIAL | PASS | PASS | PARTIAL | PARTIAL |
| 11 | 임베딩 차원 불일치 | PASS | PARTIAL | PARTIAL | PARTIAL | PASS | N/A | PARTIAL |
| 12 | 문맥 8,000자 예산 | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 13 | 5 MB 초과 파일 | PASS | PASS | PASS | PASS | PASS | N/A | PASS |
| 14 | 없는 RabbitMQ 재시도 정책 | PASS | PASS | N/A | PASS | PASS | PASS | PASS |

총 9 PASS / 4 PARTIAL / 1 FAIL이었다. 한국어 질의는 모두 한국어였고 생성 ID는 모두 존재했다.
인용 의미 열은 ID 검사와 별개로 주장을 뒷받침하는지 판단한 결과다.

## 주요 발견

1. 청크→벡터 질문이 `EmbeddingService`/주요 orchestration을 회수하지 못했다. `EmbeddedChunk` 레코드가
   변환을 수행한다고 잘못 설명하고 테스트의 `float[]`를 근거로 삼았다. 클래스 이름이 존재해도 동작 설명은 환각이었다.
2. Workspace 답변은 경로 검사 메서드보다 설계 기록·설정/예외 타입에 의존했다. 정책 설명은 맞아도 구현 근거가 부족했다.
3. 차원 불일치 답변은 다수 차원·부분 실패를 설명했지만 구현에 없는 사전 필터 동작을 과장했다.
4. Unity/재인덱싱/중첩 탐지/UTF-8/분할/크기 질문에서 문서·테스트가 구현보다 높았다.
   대부분 답변은 맞아 실패보다는 출처 우선순위 문제로 구분했다.
5. 재인덱싱 후 Kafka는 0018의 “Kafka 없음” 평가 문서를 회수했다. 설정을 지어내지는 않았지만
   평가 자료의 corpus 오염으로 더 이상 0출처 검증이 아니었다. 별도 RabbitMQ 질문은 0출처/NO_EVIDENCE/LLM 생략이었다.

## 당시 성능

14질문 평균 검색·문맥 140 ms, LLM 호출한 13질문의 평균 qwen 시간 10,990 ms,
전체 14질문의 평균 총 시간 10,345 ms였다. RabbitMQ는 전체 151 ms/LLM 0 ms였다.
모집단이 달라 전체 평균이 LLM 평균보다 작을 수 있으며 시간 순서 오류가 아니다.
모델 상주·DB/파일 캐시·부하에 따라 달라진다.

## 종료 판단과 당시 후속 과제

대부분 주요 질문이 사용 가능했고 없는 Kafka/RabbitMQ 설정을 지어내지 않았으므로 Phase 6를 종료했다.
한 건의 실패와 네 부분 통과를 남기고 새 검색 기술로 MVP 종료를 계속 미루지 않기로 했다.

당시 기록한 과제는 구현 코드 우선 필터/가중치, 평가 문서와 음성 질의 분리,
reranking, 필요성에 따른 주장별 검증, Prompt Injection 평가였다.
Hybrid Search/BM25/HNSW/대화 기억/Streaming/Agent/UI는 후속 단계 후보로 남겼다.
이 목록은 당시 계획이다. 이후 Agent/UI는 구현됐지만 나머지가 모두 구현됐다는 뜻은 아니다.
현재 종료 상태는 [0036](0036-final-quality-guards-and-release.md)을 참고한다.
