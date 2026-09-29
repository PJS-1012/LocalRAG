# 결정 0026: 오류 분석과 명시적 오류 이력 저장

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 범위와 API

Phase 7 승인 후 Phase 8 Step 1에서 오류 분석과 사용자가 요청한 이력 저장을 추가했다. 자동 복구, 쓰기 도구, 자동 커밋, 오류 임베딩, 스케줄러, 알림, UI, 다중 에이전트는 당시 범위에서 제외했다.

기본 경로는 `/api/workspaces/projects/errors`다.

- `POST /analyze`: projectId/query로 기존 읽기 전용 도구를 실행하고 만료 시간이 있는 analysisId와 근거를 반환한다. DB에는 저장하지 않는다.
- `POST /history`: projectId/analysisId로 서버에 보관된 분석을 저장한다. 클라이언트가 보낸 근거는 저장하지 않는다. 반복 저장은 기존 기록을 반환하고, 동시 고유 키 충돌은 409로 응답한다.
- `GET /history?projectId=...&page=0&size=20`: 프로젝트 범위에서 페이지 조회하며 size 상한은 100이다.
- `PATCH /history/{id}/status`: projectId/status/verificationNote/rootCause/solution/expectedVersion으로 사용자의 명시적 검증과 낙관적 동시성 제어를 수행한다.

기존 ProjectDiscoveryService의 상대 식별자, 경로 이탈·절대 경로·Container 차단을 재사용한다. `LOCAL_USER_REQUESTED`, `LOCAL_USER_ATTESTATION`은 처리 출처 표시이지 인증된 사용자 신원이 아니다. 인증 도입 전에는 신뢰할 수 있는 로컬 환경에서 사용한다.

## 데이터와 근거 정책

ErrorHistory 필드: id, analysisId, projectId, occurredAt, recordedAt, errorType, errorMessage, symptom, rootCause, solution, status, relatedFiles, relatedCommits, evidenceSummary, createdBy, verificationNote, verifiedBy, statusChangedAt, version.

Flyway V3는 관계형 테이블, JSONB 근거·경로·해시 목록, 프로젝트/시간 인덱스를 만든다. 이 단계에는 벡터가 없다. 초기 상태는 항상 UNVERIFIED이며 rootCause/solution은 null이다. 모델의 설명은 검증되지 않은 분석으로 보관한다. VERIFIED/RESOLVED에는 원인과 검증 메모가 필요하고 RESOLVED에는 해결 내용도 필요하다. 이는 사용자의 확인이지 자동 입증이 아니다. 당시에는 최신 검증 정보만 저장했으며 변경 감사 이력은 다음 단계에서 추가했다.

Log/Git/Knowledge/환경 도구 중 관련 도구만 사용한다. ConfirmedEvidence는 실제 도구 DTO와 시각, 제한된 실패 관찰을 기록한다. 추론은 미검증 설명으로, 부족한 근거와 원인 불확실성은 Unknown으로 구분한다. 관찰된 오류가 없으면 모델 설명 대신 고정된 근거 부족 안내를 반환한다.

파일·커밋은 도구/RAG DTO에서만 추출한다. 모델이 만든 경로·해시, 위험한 경로와 다른 프로젝트 DTO는 거부한다. 로그 경로는 근거 위치이지 원인 코드가 아니며, Git 시간 관계도 인과관계가 아니다. occurredAt은 시간대가 포함된 로그 시각을 해석할 수 있을 때만 기록하고 recordedAt과 구분한다.

구조화된 상태와 원인 필드는 코드로 제한하지만 자유 서술은 여전히 원인을 과장할 수 있다. 인용 ID 검증은 의미 검증이 아니므로 검토가 필요하다.

## 민감 정보와 임시 분석

기존 로그/검색 마스킹에 더해 질문·모델 설명·근거와 저장 직전 전체 스냅샷/민감 JSON 키를 다시 검사한다. Authorization/Cookie/password/JDBC/JWT/email 외에 따옴표로 감싼 자격 증명, 개인 키, 일반적인 API 키도 처리한다. 모든 비밀 형식을 탐지한다는 보장은 없다.

임시 분석은 직렬화한 불변 스냅샷으로 메모리에 보관한다. 기본값은 `localrag.errors.draft-ttl=PT30M`, `max-drafts=100`이며 개별 상한은 512,000자, 도구 출력은 최대 32개다. 재시작·만료·퇴거 후에는 다시 분석해야 하며 자동 저장하지 않는다.

## 시간 제한과 측정

`gradlew.bat errorAnalysisLiveEval`은 합성 도구 근거와 실제 qwen3:8b로 NPE/DB/EMPTY를 각 한 번 평가한다. 운영 장애나 실제 검색 성능의 검증은 아니다. 자식 JVM별 시작 포함 90초를 제한하며 초과 시 해당 평가가 소유한 프로세스만 종료하고 FAIL과 중간 결과를 기록한 뒤 다음 사례를 진행한다. 공유 Ollama/Docker는 종료하지 않는다. 클라이언트 종료 직후 서버 추론까지 멈춘다는 보장은 없다.

기존 AgentDiagnosisLiveEvaluationTest도 같은 실행기를 사용한다. 합성 시간 초과 후 다음 사례 성공을 확인했다. 실행기 정상 종료는 답변 품질 PASS가 아니며 서비스의 Agent 시간 제한과도 구분한다.

근거 시간은 도구 시간 합계, RAG 시간은 그중 Knowledge 부분이다. LLM 시간은 도구 시간을 제외한 Agent 경과 시간으로 조정 처리도 포함하며 순수 GPU 시간이 아니다. 분석 시간은 최종 임시 분석 직렬화를 제외한다. DB 저장 시간은 조회·마스킹·INSERT flush까지이며 외부 트랜잭션 commit은 제외한다.

## 당시 검증

[평가 기록](../phase8-step1-evaluation.md): NPE PARTIAL, DB 설명/인용 FAIL, EMPTY PASS. 세 사례 모두 90초 이내였고 평균 LLM 49,063.7 ms, 분석 49,282 ms였다. 모든 실제 결과에서 rootCause=null, status=UNVERIFIED를 유지했다.

PostgreSQL 17/pgvector Testcontainers로 Flyway V3, 명시적 저장, 마스킹, 프로젝트 격리, 상태 전이를 검증했다. JSONB 변경 감지가 commit에서 version을 추가 증가시키던 문제는 최초 저장 후 JSON 값을 불변으로 만들어 수정했고 반환/저장 version 일치를 검사했다. 합성 저장/flush 기준값은 115 ms다.

전체 149개 중 148 통과, 선택 실행 실제 모델 테스트 1개 제외, 실패 0, 46초였다. 당시 단계에서는 커밋·푸시하지 않았으며 이는 현재 Git 상태를 뜻하지 않는다.
