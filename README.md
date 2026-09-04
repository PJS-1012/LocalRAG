# LocalRAG

등록된 Workspace 안의 개발 프로젝트와 문서를 로컬에서 인덱싱하고 검색·분석하는 Local Development Knowledge & Workflow AI Agent 프로젝트입니다.

## 현재 단계

Phase 6 - Chunking and vector indexing

Current: Phase 6 complete - evaluated local RAG MVP

## Workspace discovery and file scan

The default Workspace root is `C:/workspace`. Override it with `LOCALRAG_WORKSPACE_ROOT`.
A direct child with clear framework markers is a Project root. When a direct child is UNKNOWN, discovery checks
only its immediate child directories. If marked Projects are found there, the parent is reported as a Container and
only those child Project roots are returned for scanning. Discovery does not recurse further, and a Git repository
alone does not imply a framework.
Each Project exposes a portable `projectId` relative to the Workspace root, using `/` separators. Canonical
single-Project APIs accept this ID as a query parameter.


List Projects:

```powershell
Invoke-RestMethod http://localhost:18080/api/workspaces/projects
```

Inspect Projects and Containers:

```powershell
Invoke-RestMethod http://localhost:18080/api/workspaces/discovery
```

Scan Project file metadata:

```powershell
Invoke-RestMethod -Method Post "http://localhost:18080/api/workspaces/projects/scan?projectId=Room_Reservation%2FRoomReservation"
```

The scan does not read or embed file content yet. It checks paths, names, extensions, and sizes, then reports:

- `SUPPORTED`
- `SKIPPED_EXTENSION`
- `SKIPPED_EXCLUDED_PATH`
- `SKIPPED_SENSITIVE`
- `SKIPPED_TOO_LARGE`
- `METADATA_FAILED`

The default file-size limit is 5MB. Unity Projects additionally exclude generated directories such as `Library`, `Temp`, `Logs`, and `UserSettings`.

Read exactly one supported UTF-8 text file:

```powershell
Invoke-RestMethod -Method Post `
  "http://localhost:18080/api/workspaces/projects/documents/read?projectId=Room_Reservation%2FRoomReservation&filePath=README.md"
```

The reader reapplies the scan policy before opening the file, enforces the Project boundary after resolving links,
and returns `READ_FAILED` instead of aborting other work when UTF-8 decoding or file access fails.

Read every allowed text file from one Project:

```powershell
Invoke-RestMethod -Method Post `
  "http://localhost:18080/api/workspaces/projects/documents/read-all?projectId=Toy_Sports_Day%2Ftoy_sports_day"
```

The result reports success, failure, and skip counts; per-file paths, statuses, and reasons; total text bytes;
and elapsed milliseconds. Reading is sequential and remains limited to the selected Project.

Scan metadata for every discovered Project root without reading content:

```powershell
Invoke-RestMethod -Method Post http://localhost:18080/api/workspaces/scan
```

The Workspace summary preserves each Project's type, detection hints, counts, oversized-file details, failure reason,
and elapsed time. A failed Project does not stop later Project scans.

## Chunk preview

Create in-memory Chunks for one Project without embedding or persistence:

```powershell
Invoke-RestMethod -Method Post `
  "http://localhost:18080/api/workspaces/projects/chunks/preview?projectId=Local_Ai_Work"
```

Text and Markdown use a 2,000-character maximum, source code uses 2,400 characters, and both use 200 characters of overlap. Structural boundaries are preferred when possible.

## Chunk embedding preview

Embed every in-memory Chunk from one Project without vector persistence:

```powershell
Invoke-RestMethod -Method Post `
  "http://localhost:18080/api/workspaces/projects/chunks/embeddings/preview?projectId=Local_Ai_Work"
```

The response contains only the first eight vector values per Chunk. Full vectors remain internal and are not stored.

## Project vector indexing

Read, chunk, embed, and transactionally synchronize one Project with pgvector:

```powershell
Invoke-RestMethod -Method Post `
  "http://localhost:18080/api/workspaces/projects/index?projectId=Local_Ai_Work"
```

Inspect stored Chunk count and vector metadata without returning vector values:

```powershell
Invoke-RestMethod `
  "http://localhost:18080/api/workspaces/projects/index/stats?projectId=Local_Ai_Work"
```

Chunk IDs are deterministic. Reindexing updates changed Chunks, deletes stale Chunks only inside the selected
Project, and leaves identical rows untouched. Embedding or dimension validation must complete for the whole Project
before database synchronization begins. The current schema accepts only 1024-dimensional vectors.

## Project vector similarity search

Embed a query and search only the selected Project with pgvector cosine similarity:

```powershell
$body = @{
  projectId = "Local_Ai_Work"
  query = "프로젝트 타입을 탐지하는 코드는 어디에 있나?"
  topK = 5
  threshold = 0.45
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/api/workspaces/projects/search" `
  -ContentType "application/json" `
  -Body $body
```

The response returns ranked Chunk content and citation-ready source metadata, never vectors. Search is always scoped
by `projectId`. The current evaluation baseline is Top-K 5 and similarity threshold 0.45; these are configurable
retrieval-tuning values rather than final quality constants. Query instruction is enabled by default through
`localrag.search.query-instruction`. Send `"instructionEnabled": false` in the request to use the raw user Query.
The response's `instructionEnabled` field reports the mode actually used.

## RAG Context preview

Search and assemble citation-ready Context without calling the chat model:

```powershell
$body = @{
  projectId = "Local_Ai_Work"
  query = "민감 파일을 어떻게 제외하지?"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/api/workspaces/projects/rag/context/preview" `
  -ContentType "application/json" `
  -Body $body
```

The `localrag.rag.context.max-characters` setting limits the complete formatted Context, including Source headers,
paths, line ranges, and content. The 8,000-character default is an evaluation baseline. Results are considered in
similarity order, but a Chunk that would exceed the remaining budget is excluded whole rather than truncated.
Similarity remains available in `sources` metadata and is not written into the Context body. A successful search
with no matches returns an empty Context.

## RAG Chat

Retrieve Project evidence and ask qwen3:8b for a cited answer:

```powershell
$body = @{
  projectId = "Local_Ai_Work"
  query = "프로젝트 타입은 어떻게 탐지해?"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/api/workspaces/projects/rag/chat" `
  -ContentType "application/json" `
  -Body $body
```

The response contains the answer, citation-ready Source metadata, used and invalid Source IDs, Context size, and
separate retrieval/Context, LLM, and total durations. Retrieved content is treated as untrusted evidence rather than
instructions. Unknown or missing Citations are surfaced through warnings. A zero-Source search skips the model and
returns `NO_EVIDENCE` deterministically.

## 기술 기준

- Java 17
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper
- PostgreSQL 17
- pgvector 0.8.6
- Spring AI 1.1.8
- Ollama + Qwen3 8B
- Qwen3 Embedding 0.6B

현재 Embedding API는 텍스트를 1024차원 Vector로 변환하며 Project 단위로 pgvector에 저장합니다. 검색 결과는 8,000자 예산 안에서 citation-ready RAG Context로 조립할 수 있습니다.

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
