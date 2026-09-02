# LocalRAG

등록된 Workspace 안의 개발 프로젝트와 문서를 로컬에서 인덱싱하고 검색·분석하는 Local Development Knowledge & Workflow AI Agent 프로젝트입니다.

## 현재 단계

Phase 2 — PostgreSQL + pgvector

## 기술 기준

- Java 17
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper
- PostgreSQL 17
- pgvector 0.8.6

Spring AI와 Ollama 연동은 각 Phase에서 개념과 역할을 확인한 뒤 필요한 의존성만 추가합니다.

## Database

```powershell
docker compose up -d postgres
docker compose ps
```

개발용 기본 접속 정보는 `.env.example`에 있습니다. 개인 설정은 `.env`에 작성하며 Git에 포함되지 않습니다.

## 실행

Windows PowerShell:

```powershell
docker compose up -d postgres
.\gradlew.bat bootRun --args="--server.port=18080"
```

Health API:

```powershell
Invoke-RestMethod http://localhost:18080/api/health
Invoke-RestMethod http://localhost:18080/actuator/health
```

## 테스트

```powershell
.\gradlew.bat test
```

통합 테스트는 Testcontainers로 실제 pgvector 컨테이너를 실행하므로 Docker Desktop이 실행 중이어야 합니다.
