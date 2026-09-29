# 0017. 질문 임베딩 지침 기본 적용

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태와 결정

Phase 6 Step 4.3에서 승인했다.

- 0016에서 평가한 지침을 기본으로 켠다.
- `localrag.search.query-instruction.enabled`, `localrag.search.query-instruction.template`으로 분리한다.
- template에 `<USER_QUERY>`를 필수로 두고 정규화한 질문으로 치환한다.
- 요청 `instructionEnabled`가 없으면 설정값, `false`이면 Raw 임베딩을 사용한다.
- 응답에도 적용 모드를 반환한다. 응답 질문에는 지침을 붙이지 않고 모델 입력에만 적용한다.
- Top-K 5, threshold .45, corpus, 분할, pgvector SQL은 유지한다.

## 기본 template 원문

```text
Instruct: Retrieve the most relevant source code or project documentation for the software project query.
Query: <USER_QUERY>
```

## 실제 API 검증

동일한 208청크 인덱스에서 핵심 6질문을 기본/Raw 모드로 실행했다.

| 질문 | 기본 지침 | 기본 결과 | Raw 결과 | 기본 1위 |
|---|---|---:|---:|---|
| 프로젝트 타입 탐지 | true | 5 | 5 | ProjectType.java |
| 민감 파일 제외 | true | 5 | 0 | ProjectFileScanner.java |
| Text → Vector | true | 5 | 5 | vector 확장 Migration |
| 없는 Kafka 설정 | true | 0 | 0 | 없음 |
| 작업공간 경계 보호 | true | 5 | 0 | Phase 5 보안 기록 |
| 중첩 프로젝트 탐지 | true | 5 | 5 | 한 단계 탐지 기록 |

모든 기본 응답은 Top-K 5/threshold .45/`instructionEnabled=true`, Raw override는 false였다.
민감/경계 회수와 Kafka 차단을 유지했다.

## 영향

사용자가 모델 지침을 알지 못해도 기본 검색에 적용되며 Raw는 회귀 진단·원복 비교에 남긴다.
Migration·문서·테스트가 실제 구현보다 높은 기존 순위 문제는 이 설정 변경으로 해결하지 않았다.
