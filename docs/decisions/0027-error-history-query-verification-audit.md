# 결정 0027: 오류 이력 조회와 검증 변경 감사

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 범위

Phase 8 Step 2는 관계형 오류 이력 조회와 상태 변경 추적을 완성했다. 오류 임베딩/벡터 검색, 자동 저장·해결 추천, 스케줄·알림·진행 상태·다중 에이전트·UI는 이 단계 범위가 아니다.

## 조회 API와 경계

- `GET /api/workspaces/projects/errors/history/{id}?projectId=...`: 정규화된 프로젝트와 `id + projectId`로 조회한다. 없거나 다른 프로젝트의 ID면 404다. 저장 근거, 현재 상태, 시간순 검증 변경 이력을 반환한다.
- `GET /api/workspaces/projects/errors/history`: 프로젝트 범위를 유지하며 status, errorType, errorMessage, occurredFrom/To, recordedFrom/To, relatedFile, relatedCommit, page, size를 받는다. size는 1..100, 기본 정렬은 recordedAt DESC와 id DESC다.

Spring Data JPA에서 매개변수화한 PostgreSQL SQL을 사용한다. errorType은 trim/소문자 정규화 후 정확히 비교한다. errorMessage는 대소문자를 구분하지 않는 문자 그대로의 부분 검색이며 LIKE 와일드카드를 이스케이프한다. 날짜 양 끝은 포함한다. 파일/커밋은 JSONB 배열 원소와 정확히 비교한다. 구분자는 `/`로 통일하고 경로 이탈·절대 경로는 거부하며 커밋은 7..40자리 16진수로 제한한다.

관리 화면에서 전체 건수/페이지 수가 필요하므로 Slice 대신 Page를 사용하고 count 쿼리를 허용했다. 당시 데이터가 적어 상태·유형·날짜/JSONB 인덱스만 두고 캐시를 추가하지 않았다.

## 감사 이력과 N+1 방지

`error_history_verification`에는 이력 ID, 이전/다음 상태, 마스킹한 원인·해결·메모, 처리 출처, changedAt, 변경 전 낙관적 잠금 version을 추가 저장한다. ErrorHistory 외래 키와 시간순 인덱스가 있다. ErrorHistory에 감사 이력 ORM 컬렉션을 매핑하지 않아 목록 조회 시 행마다 감사 쿼리가 발생하지 않는다. 상세 조회에서만 별도 정렬 쿼리 한 번을 실행한다.

`LOCAL_USER_ATTESTATION`은 로컬 처리 출처이며 인증 사용자 ID가 아니다. 인증·감사 이력 수정 권한 정책은 별도 요구사항이다.

## 상태 전이와 트랜잭션

`UNVERIFIED -> VERIFIED -> RESOLVED`만 허용한다. 직접 UNVERIFIED -> RESOLVED, 동일 상태, 역방향, RESOLVED 이후 전이는 거부한다. VERIFIED에는 rootCause/verificationNote, RESOLVED에는 rootCause/solution/verificationNote가 필요하다. 저장 경계에서 사용자 필드를 다시 마스킹한다.

expectedVersion을 확인한 후 현재 행 수정/flush와 감사 행 삽입/flush를 하나의 트랜잭션에서 수행한다. 오래된 version의 경쟁 요청은 409이며 감사 행도 남기지 않는다. 감사 행에는 previousVersion, 현재 행에는 새 version을 노출한다. 최초 저장된 JSONB 근거는 불변이다.

## 측정과 당시 검증

목록/상세 시간은 commit 이전 서비스·저장소 처리 시간이다. 상태 변경은 프로젝트 검증, 행/버전 확인, 현재 행과 감사 행 flush를 포함하고 auditSaveDuration은 그중 감사 행 삽입/flush 부분이다. 운영 지연 보장이 아닌 로컬 Testcontainers 단일 측정이다.

통합 테스트로 동일/타 프로젝트와 없는 ID, 모든 필터, 날짜 경계, 문자 그대로의 와일드카드, Page 전체 건수/정렬, 정상/역방향/직접 전이, 오래된 version 409, 감사 중복 방지, 현재/과거 값의 민감 정보 마스킹을 검증했다.

| 항목 | 당시 측정값 |
| --- | ---: |
| 목록 조회 | 7 ms |
| 감사 이력을 포함한 상세 | 11 ms |
| 상태 변경 및 두 flush | 5 ms |
| 감사 삽입/flush 부분 | 1 ms |
| 최초 이력 저장/flush | 118 ms |

전체 152개 중 151 통과, 선택 실행 실제 모델 테스트 1개 제외, 실패 0이다. Java 17에서 `gradlew.bat test --no-daemon`은 38초였다. 프레임워크 준비 상태에 따라 수치는 달라진다. 모델 동작을 바꾸지 않아 실제 qwen 품질 평가를 반복하지 않았다. 당시 커밋·푸시 없음은 현재 Git 상태와 구분한다.
