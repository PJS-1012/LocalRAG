# 결정 0002: PostgreSQL과 pgvector 기준 구성

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 문제

RAG에서 사용할 일반 데이터와 당시 도입 예정이던 임베딩 벡터를 함께 저장할 데이터베이스 환경이 필요하다.

## 원인

문서 메타데이터에는 관계형 데이터베이스가 적합하고, 의미 검색에는 벡터 저장 및 유사도 검색 기능이 필요하다.

## 후보

- 별도 벡터 DB 사용
- PostgreSQL과 별도 벡터 DB를 함께 사용
- PostgreSQL에 pgvector 확장을 추가해 함께 사용

## 선택

- PostgreSQL 17
- pgvector 0.8.6
- Docker 이미지 `pgvector/pgvector:0.8.6-pg17`
- Flyway 마이그레이션으로 확장 활성화

## 이유

프로젝트 초기에는 운영 복잡도를 낮추는 것이 중요하다. PostgreSQL과 pgvector를 함께 사용하면 관계형 데이터와 벡터를 하나의 DB에서 관리하면서 SQL, 트랜잭션, 메타데이터 필터를 활용할 수 있다. 이미지 버전을 고정해 실행 환경도 재현 가능하게 유지한다.

## 결과

Docker Compose의 PostgreSQL 17.11 컨테이너가 `healthy` 상태로 실행되었다. Spring Boot가 HikariCP로 연결했고 Flyway V1 마이그레이션을 적용했다. Compose DB와 Testcontainers 임시 DB 모두에서 pgvector 0.8.6 활성화를 검증했으며 전체 테스트가 성공했다.
