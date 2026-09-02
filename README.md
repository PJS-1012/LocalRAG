# LocalRAG

등록된 Workspace 안의 개발 프로젝트와 문서를 로컬에서 인덱싱하고 검색·분석하는 Local Development Knowledge & Workflow AI Agent 프로젝트입니다.

## 현재 단계

Phase 4 — Local text embeddings

## 기술 기준

- Java 17
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper
- PostgreSQL 17
- pgvector 0.8.6
- Spring AI 1.1.8
- Ollama + Qwen3 8B
- Qwen3 Embedding 0.6B

현재 Embedding API는 텍스트를 1024차원 Vector로 변환합니다. Vector 저장과 검색은 이후 Phase에서 추가합니다.

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

Chat API:

```powershell
$body = @{ message = "Java 17의 장점을 한 문장으로 설명해줘." } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:18080/api/chat `
  -ContentType "application/json" `
  -Body $body
```

기본 모델은 `qwen3:8b`이며 `OLLAMA_CHAT_MODEL` 환경 변수로 변경할 수 있습니다. 애플리케이션은 모델을 자동으로 내려받지 않습니다.

Embedding API:

```powershell
$body = @{ text = "예약 생성 로직" } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:18080/api/embeddings `
  -ContentType "application/json" `
  -Body $body
```

기본 Embedding 모델은 `qwen3-embedding:0.6b`이며 실제 출력은 1024차원입니다. API는 확인용으로 앞 8개 값만 반환합니다.

## 테스트

```powershell
.\gradlew.bat test
```

통합 테스트는 Testcontainers로 실제 pgvector 컨테이너를 실행하므로 Docker Desktop이 실행 중이어야 합니다.
