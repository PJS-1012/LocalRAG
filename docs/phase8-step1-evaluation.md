# Phase 8 Step 1 평가 — 2026-09-09

과거 평가 기록이다. 현재 기능/배포 기준은 [시스템 구조](architecture.md)와 [배포 점검표](release-checklist.md)를 따른다.

## 실제 모델 한 번 평가

실제 qwen3:8b와 통제된 읽기 전용 도구 표본을 사용했고 검색 기준은 바꾸지 않았다. 원본은 `build/reports/error-analysis-live/2026-09-09T07-33-11.328909600Z/`다. NPE/DB/EMPTY를 각 한 번 실행하고 품질을 맞추기 위한 반복은 하지 않았다.

| 사례 | 실제 선택 도구 | 품질 | 근거/도구 ms | RAG ms | LLM ms | 분석 ms | 자식 실행 전체 ms |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| NPE | getRecentErrors, searchLogs, searchProjectKnowledge | PARTIAL | 11 | 3 | 54,166 | 54,396 | 61,021 |
| DB | searchLogs, getDatabaseStatus | FAIL(설명/인용) | 7 | 0 | 54,076 | 54,295 | 60,002 |
| EMPTY | searchLogs | PASS | 9 | 0 | 38,949 | 39,155 | 44,837 |

평균 근거/도구 9 ms, RAG 1 ms, LLM 49,063.7 ms, 분석 49,282 ms다. 표본 RAG 시간은 실제 벡터 검색 성능이 아니다. 자식 전체 시간에는 시작/정리가 포함되지만 분석에는 포함되지 않는다. 모두 90초 이내였다. 실행기는 완료 사례를 REQUIRES_REVIEW로 기록하며 이 문서는 원본의 수동 의미 검토다.

## 주장과 근거 검토

NPE 표본 로그는 UserService.java:42의 NullPointerException이며 K1-S1은 40~44행의 `String name(User user) { return user.getName(); }`다. user가 null일 가능성은 맞지만 매개변수 누락/초기화 실패로 확장할 근거는 없다. 코드 주장 대신 로그 발생에 Knowledge ID를 인용했고 한국어 중 중국어 단어가 섞였다. 선택/근거 PASS, 근거 일치/인용/언어 PARTIAL이다. 구조화된 경로/해시는 만들지 않았고 rootCause=null, UNVERIFIED를 유지했다.

DB 표본의 과거 Connection refused와 현재 DB 도구의 JDBC/pgvector 사용 가능은 서로 다른 관찰이며 대상 앱 DB 설정 성공을 입증하지 않는다. Knowledge 호출 없이 K1-S1/K2-S2를 만들어 기존 검증기가 둘 다 잘못된 ID로 판정했다. 호스트·포트·방화벽·권한을 추측하고 근거 없는 도구를 제안했다. 도구 선택/상태 경계는 통과했지만 설명/인용 FAIL이다. 추론을 rootCause나 VERIFIED로 승격하지 않았어도 미검증 서술은 표시·저장될 수 있으므로 검토가 필요하다.

EMPTY는 searchLogs 결과가 없어 고정된 한국어 안내, errorMessage/rootCause=null, 파일/커밋 없음, UNVERIFIED를 반환했다. 오류/원인/인용 ID를 만들어내지 않아 PASS다.

## 합성·DB 검증

Git 관계가 원인 입증이 아님, 지식 부재, 비밀 포함 로그, 모델이 만든 경로/해시 거부, 기본 UNVERIFIED를 검증했다. 임시 분석은 불변성·다른 프로젝트 조회 차단·퇴거·만료를 확인했다. 소유한 60초 대기 자식을 테스트 제한 2초에 종료하고 다음 자식 성공을 확인하여 qwen 대기 없이 격리성을 검증했다.

PostgreSQL 17/pgvector에서 명시적 저장·마스킹·멱등성·프로젝트 격리·상태/version·클라이언트 상태 주입 거부·Flyway V3를 검증했다. 최초 실행은 JSONB 추가 UPDATE로 응답 version=1/commit 후=2를 드러냈다. 한 번만 저장하는 JSON/경로/해시를 불변 매핑하고 복사한 목록을 사용해 추가 변경을 막았다. VERIFIED→RESOLVED 전에 응답/저장 버전 일치를 영구 검사한다. 합성 저장/flush는 115 ms이며 commit은 측정 밖이다.

최종 `gradlew.bat test --no-daemon`: 전체 149개, 148 통과, 선택 실행 실제 모델 1개 제외, 실패 0, 46초. 이전 선택 실행의 시간 초과 격리 테스트는 2.207초였다. 최종 git diff 검사도 통과했다.

Docker Desktop 4.79가 WSL 시작 전 오래된 Windows AF_UNIX 소켓 dockerInference/engine.sock 제거 실패로 중단됐다. 런타임 폴더는 삭제하지 않고 시각이 있는 백업으로 이동했으며 Docker AI는 원래 활성 설정으로 복원했다. 이후 Engine 29.5.3과 Testcontainers가 정상 실행됐다. 백업은 저장소 밖에 있다.

## 당시 종료 판단

근거 없는 원인 나열, 허위/의미가 다른 인용, 언어 혼용, 약 49초 LLM 지연을 품질 한계로 남겼다. 모두 PASS를 얻으려고 모델 평가/튜닝을 반복하지 않았다. 구현/자동 검증 조건은 완료했지만 자유 서술은 부분적 품질이며 VERIFIED 데이터가 아니다.
