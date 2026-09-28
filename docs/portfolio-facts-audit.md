# LocalRAG 포트폴리오 제작용 사실·근거 조사

조사 기준: 2026-09-28, Git `f607670` (`chore: finalize localrag release build`). 대상은 현재 저장소의 Backend, Frontend, Tauri, 설정, SQL Migration, 테스트, Git 및 Decision Log다. 완성된 포트폴리오 문구가 아니라 작성자가 선별·검증해서 사용할 원본 자료다.

이번 조사는 읽기 전용 코드 분석과 기존 산출물 대조다. 애플리케이션 수정, 모델 재평가, 테스트 재실행, 커밋, push는 하지 않았다. 이 문서만 새로 작성했다.

## 읽는 기준

- **코드 확인**: 현재 소스·설정·SQL에서 직접 확인한 동작. 모든 실행 환경에서의 성공을 뜻하지 않는다.
- **기록 확인**: 기존 테스트 결과·평가 JSON·Decision Log의 특정 시점 기록. 현재 성능의 새 측정값이 아니다.
- **분석상 위험**: 코드 경로에서 도출한 한계. 실제 장애로 재현하지 않은 것은 명시한다.
- **확인 불가**: 저장소만으로 증명할 수 없는 선택 이유, 성능, 개발자 개인 기여.
- **포트폴리오 추천**: 구현 사실과 별개인 편집 판단. 작업 추가 제안이나 개발 재개 계획이 아니다.

핵심 구분: 현재 메인 **통합 채팅**, 기존 **지식 검색 상세(RAG)**, **에이전트 상세**는 동일한 처리 경로가 아니다. 또한 `SUCCESS`, 테스트 통과, 유효한 Citation ID는 답변의 의미 정확도를 보증하지 않는다.

## 1. 프로젝트 개요

### 해결하려는 문제와 실제 사용자 시나리오

개발 정보가 프로젝트 코드·문서·Git·로그·로컬 실행 환경·오류 이력에 나뉘어 있다는 문제를 다룬다. PC 전체를 무차별 수집하지 않고 지정 Workspace의 Project를 탐지하고 범위를 제한한다.

| 시나리오 | 현재 제공되는 동작 | 과장하면 안 되는 부분 |
|---|---|---|
| 처음 보는 프로젝트 파악 | 프로젝트 선택 → 통합 채팅 → 코드/문서 발췌와 필요 도구를 근거로 설명 | 전체 소스의 완전한 의미 분석이나 기능 완성도 판정은 아님 |
| 특정 구현 찾기 | 수동 인덱싱 → 프로젝트 범위 벡터 검색 → 답변·Source | 모든 관련 구현을 회수한다는 보장 없음 |
| 실행 환경 점검 | Git, Docker, DB, Ollama, 로그 조회 도구 | 임의 Shell 실행·자동 코드 수정 도구 아님 |
| 오류 관리 | 분석 → 명시적 이력 저장 → 사용자 확인 상태 변경·감사 기록 → 유사 오류 검색 | LLM 추측을 자동으로 검증된 원인으로 승격하지 않음 |
| 개발 현황 확인 | Git/문서/오류를 모아 진행 상태·최근 작업 설명 | 테스트를 실제 실행하거나 진척률을 정확히 계산하지 않음 |
| 반복 점검 | 프로젝트별 자동화 설정, 변경 감지, 실행 이력·알림 후보 | OS 알림/메일 발송 시스템은 아님 |
| Windows 실행 | Tauri 창 → 의존 서비스 준비 → Backend → Dashboard | Docker·Java·모델까지 설치해 주는 완전 독립 실행 패키지는 아님 |

### 스택과 사용 위치

| 계층 | 확인된 기술·버전 기준 | 실제 사용 |
|---|---|---|
| Backend | Java 17, Spring Boot 3.5.16, Gradle Wrapper 8.14.3 | REST API, Validation, 서비스 계층, 스케줄러, Actuator |
| AI 연동 | Spring AI BOM 1.1.8, Ollama starter | `ChatClient`, `EmbeddingModel`, Tool Callback 연결 |
| 영속성 | Spring Data JPA, JdbcTemplate, Flyway | 오류·자동화 Entity는 JPA, 벡터 검색/동기화는 직접 SQL, DDL은 Migration |
| DB | PostgreSQL 17, pgvector 이미지 `0.8.6-pg17` | 관계형 이력 + `vector(1024)` 저장·코사인 거리 |
| 모델 | `qwen3:8b`, `qwen3-embedding:0.6b` | 로컬 답변/도구 선택, 문서·질문 임베딩 |
| Frontend | React 19, TypeScript 5, Vite 7 | 프로젝트 선택, 통합 채팅 및 관리/상세 화면 |
| Desktop | Tauri 2, Rust 2021 edition | 로컬 HTTP Bridge, 시작 orchestration, Windows NSIS 배포 |
| 검증 | JUnit 5, Mockito/MockMvc, Testcontainers, Vitest, Testing Library, Rust test | 정책·서비스·SQL 통합·UI 상호작용·시작 단계 회귀 |
| 로컬 Infra | Docker Compose, Docker Desktop, Ollama, Java 17 | PostgreSQL 컨테이너 및 네이티브 프로세스 운영 |

JS/Cargo의 버전 범위와 실제 lockfile 해석 버전은 구분해야 한다. 예를 들어 package.json은 Vite `^7.1.5`이고 마지막 빌드 기록은 7.3.6이다. Rust `rust-version = 1.77.2`는 최소 요구 버전이지 실제 사용 컴파일러 버전을 뜻하지 않는다. 근거: [build.gradle][build], [application.yml][config], [package.json][package], [Cargo.toml][cargo].

### 저장소 구조

```text
src/main/java/com/localai/workspace/
  discovery / scan / document       Workspace·Project 탐지와 안전 읽기
  chunk / embedding / index        청크·임베딩·DB 동기화
  search / rag                     검색·Context·RAG 답변
  chat / agent                     모델 호출·통합 채팅·읽기 전용 도구
  errors                           오류 이력·검증 감사·유사 검색
  workflow / automation            진행/작업 분석·주기 실행·알림 후보
  overview / config / health       개요·설정·상태
src/main/resources/db/migration/   V1~V6, 사용자 테이블 8개
src/test/                         Backend 회귀·통합·선택적 실모델 평가
frontend/src/                     React UI·API client·테스트
frontend/src-tauri/               Rust 시작 제어·로컬 Bridge·패키징
docs/decisions/                   0001~0036 설계·평가 기록
```

Java/Spring Backend + AI/RAG 지원에서는 React 화면 수보다 안전한 수집 경계, 벡터 SQL, 트랜잭션·검증 상태, 관찰 가능한 실패를 중심으로 설명하는 편이 적합하다.

## 2. 실제 RAG Pipeline

### 2.1 인덱싱: Workspace 지정은 설정, UI에서는 Project 선택

현재 UI에 임의 Workspace 경로 등록 CRUD는 없다. `LOCALRAG_WORKSPACE_ROOT`/`WorkspaceProperties`가 기본 경로를 공급한다. 서비스의 Path 인자와 설정 공급을 분리했지만 Workspace DB Entity는 아직 없다.

```text
설정된 Workspace Path
  → Project/Container Discovery → UI Project 선택
  → 명시적 Project 인덱싱 요청
  → Metadata Scan + 정책 필터
  → 허용 파일의 UTF-8 읽기 → WorkspaceDocument
  → 문자/구조 경계 청크 → DocumentChunk
  → Ollama Embedding → EmbeddedChunk
  → 차원·결과 검증 → pgvector 트랜잭션 동기화
```

| 단계 | 클래스/메서드 | 입력 → 출력 | 연결·역할 |
|---|---|---|---|
| 탐지 | [ProjectDiscoveryService][discovery] `discoverWorkspace(Path)`, `findProject(...)` | Workspace Path / 상대 ID → Project·Container | 탐지된 실제 Root만 단일 Project 작업 대상으로 선택 |
| 타입 | [ProjectTypeDetector][detector] `detect(Path)` | Root → 타입·Framework·marker hints | Unity 제외 정책 등의 근거 |
| Scan | [ProjectFileScanner][scanner] `plan(...)` | DetectedProject → ProjectScanPlan | 지원·제외·실패 파일과 통계를 만들고 Reader에 전달 |
| Read | [ProjectDocumentBatchReader][batch] `read(...)`, [ProjectDocumentReader][reader] `read(...)` | 허용 경로 → Document/파일별 상태 | 파일 실패를 결과로 남기며 다른 파일 계속 처리 |
| Chunk | [ProjectChunkingService][projectchunk] `chunk(...)` → [DocumentChunkingService][chunk] `chunk(...)` | Document → 청크·위치·fingerprint | 내용과 추적 Metadata를 함께 유지 |
| Embedding | [ProjectChunkEmbeddingService][embedbatch] `embed(...)` → [EmbeddingService][embed] `embedAll(...)` | 청크 내용 목록 → float vector 및 개별 결과 | 공급자 오류·차원 불일치 등을 상태로 분리 |
| 검증 | [ProjectIndexService][indexservice] `index(...)` | Embedding 결과 → 저장 가능 여부 | 성공 수·1024차원 확인 후 Repository 호출 |
| 저장 | [ProjectIndexRepository][indexrepo] `synchronize(...)` | 프로젝트·EmbeddedChunk 목록 → 쓰기/삭제/저장 수 | upsert와 오래된 청크 삭제를 한 트랜잭션으로 수행 |

여기서 Parsing은 **UTF-8 텍스트 읽기**다. PDF 파싱, OCR, 언어별 AST 구축, 문서 계층 해석을 구현했다고 표현하면 안 된다.

### 2.2 기존 RAG API: 검색된 자료만 사용하는 경로

```text
projectId + query
  → 기본 Query Instruction 적용 / 요청 시 Raw Query
  → Query Embedding
  → project_id 필터 + cosine distance + threshold + Top-K
  → Source 정책 필터·중복 제거·8,000자 Context 조립
  → 근거 없으면 NO_EVIDENCE, LLM 호출하지 않음
  → System Prompt + 질문 + Context → Ollama
  → Citation ID 검사 → Answer·Source·상태·시간 반환
```

| 단계 | 담당 | 입력 → 출력 | 다음 연결 |
|---|---|---|---|
| 검색 요청 | [ProjectSemanticSearchService][searchservice] `search(...)` | ID/query/선택적 옵션 → 검색 응답 | 인덱스 0건이면 `INDEX_NOT_FOUND`, 임베딩 생략 |
| 질의 가공 | [ProjectSearchProperties][searchprops] `QueryInstruction.apply(...)` | 사용자 질문 → retrieval instruction 포함 문자열 | 문서 임베딩과 동일한 EmbeddingService |
| 검색 SQL | [ProjectSemanticSearchRepository][searchrepo] `search(...)` | vector, ID, Top-K, threshold → StoredChunkMatch | cosine distance 오름차순, 동점 chunk ID |
| Context | [RagContextAssemblyService][context] `assemble(...)` | 질문·검색 결과 → formattedContext와 Sources | Project 검증·정책 필터·중복·문자 예산 적용 |
| 답변 | [RagChatService][ragchat] `chat(...)` → [ChatService][chat] `chat(system,user)` | 질문·근거 → 모델 문자열 | 자료를 지시가 아닌 근거로 다루도록 System Prompt |
| 출처 검사 | [RagCitationValidator][citation] `validate(...)` | 답변·허용 Source ID → 사용/잘못된/누락 ID | DTO에 답변, Source, warnings, 시간 포함 |
| UI | [RagPage][ragpage], `ragApi.ask/source` | RagResponse → 답변·출처 미리보기 | Source 클릭 시 파일 읽기 API로 현재 내용 확인 |

### 2.3 현재 메인 통합 채팅: 위 경로와 다르다

[UnifiedChatController][unifiedcontroller] → [AgentChatService][agentservice] → `ChatService.chatUnifiedWithToolCallbacks(...)`가 진입점이다.

1. 질문/의도에 맞는 허용 Tool Schema로 모델이 도구 계획을 만든다. 일반 지식 질문이면 도구 없이 응답할 수 있다.
2. [UnifiedToolRound][toolround]가 등록된 도구만 실행한다. 최대 6개, 동일 도구 중복 실행 제한이 있으며 Project 범위를 유지한다.
3. [ProjectKnowledgeAgentTools][knowledge]는 기존 RAG 자료뿐 아니라 [ProjectBriefService][brief]의 허용된 실제 파일 발췌를 사용한다. 따라서 인덱스가 없거나 벡터 결과가 없더라도 모든 질문이 즉시 끝나는 구조는 아니다.
4. Git·최근 작업·진행 상태 등의 도구 결과를 함께 수집할 수 있다. 통합 경로의 진행/작업 수집은 중간 설명용 LLM 호출을 생략한다.
5. 수집 근거를 정리하고 **도구 스키마 없는 최종 호출**로 답변을 생성한다. 일반 질문은 보통 1회, 도구 질문은 보통 2회 모델 호출 구조다. SDK 재시도까지 항상 정확히 2회라고 보장하지 않는다.
6. 완료 메타데이터, 근거 밖 식별자, 적용 가능한 Citation 검사를 거쳐 결과를 반환한다. `UnifiedRequestTrace`/`OllamaCallProfiler`가 도구·LLM 시간을 분리한다.

이 경로는 문서 RAG와 읽기 전용 도구를 조합한 방식이다. BM25·Hybrid Search·Reranker를 새로 구현한 것이 아니다. 기존 에이전트 상세의 자동 Tool Calling 경로와도 실행 제한 및 검사가 완전히 같지 않다.

## 3. Workspace 인덱싱 구조

### 3.1 Project와 Container, 상대 ID

Workspace 직계 디렉터리를 먼저 검사한다. 그 자체가 알려진 타입이면 Project로 채택하고 더 내려가지 않는다. UNKNOWN이면 **자식 디렉터리 한 단계만** marker 탐지한다. 알려진 자식 Project가 있으면 부모는 Container, 없으면 부모가 UNKNOWN Project로 남는다. 즉 Workspace 직계 + 그 아래 한 단계이지 무제한 재귀 탐지가 아니다.

예: `Room_Reservation/RoomReservation`과 `Room_Reservation/room-reservation-front`는 서로 다른 Project이고 `Room_Reservation`은 Container다. Container 자체를 본문 읽기/인덱싱 Root로 쓰지 않는다.

[ProjectId][projectid]는 Workspace 기준 상대 경로를 `/` 형식으로 정규화한다. 절대 경로, `.`/`..` 경로 요소, 빈 요소 등을 차단하고 탐지된 Root와 대조한다. 같은 이름의 `A/backend`, `B/backend`를 구별할 수 있다. 단 경로 변경 시 ID도 바뀌며 DB Workspace ID가 없으므로 여러 Workspace를 동시에 등록한 식별 체계는 아니다. 일부 Document/탐지 DTO에는 절대 Path가 있으므로 “어떤 API에도 절대 경로가 노출되지 않는다”는 표현은 틀리다.

### 3.2 타입 판별의 정확한 조건

우선순위는 UNITY → JAVA → NODE → DOTNET → PYTHON → UNKNOWN이다.

| 타입 | 실제 조건 | Framework 판단의 범위 |
|---|---|---|
| UNITY | `Assets/`, `ProjectSettings/`, `Packages/manifest.json` 모두 존재 | `UNITY` |
| JAVA | Gradle/Maven/settings marker 중 하나 + `src/main/java/` | build.gradle(.kts)의 `org.springframework.boot` 또는 pom.xml의 `spring-boot` 문자열이면 SPRING_BOOT |
| NODE | `package.json` 존재 | NODE; React/Next/Vue를 정교하게 구분하지 않음 |
| DOTNET | Root 파일 중 `.sln` 또는 `.csproj` | DOTNET |
| PYTHON | `pyproject.toml` 또는 `requirements.txt` | PYTHON |
| UNKNOWN | 위 조건 불충족 | Git 저장소라는 이유로 타입을 추정하지 않음 |

Git 여부는 `.git` **디렉터리** 기준이다. `.git` 파일을 쓰는 worktree 형태까지 일반화하면 안 된다. 순수 Kotlin 구조 등은 Java 조건에서 빠질 수 있다. Spring 탐지는 빌드 파일 문자열 검사이지 빌드 모델/의존성 그래프 해석이 아니다.

주의: 파일 Metadata Scan은 본문을 읽지 않지만, Discovery의 Spring 판별은 빌드 파일을 `Files.readString`으로 읽는다. 따라서 “탐지 전체 과정이 모든 파일 내용에 전혀 접근하지 않는다”는 주장은 부정확하다.

### 3.3 지원·제외 정책

근거: [application.yml][config], [WorkspaceScanPolicy][scanpolicy], [ProjectFileScanner][scanner].

- 지원 확장자: `java kt cs js jsx ts tsx py c cpp h hpp sql md txt yml yaml json properties xml csv log html css gradle kts ps1 sh`.
- 확장자 외 허용 이름: `Dockerfile`, `README`, `LICENSE`.
- 공통 제외 디렉터리: `.git .github .idea .vscode .gradle .mvn target build out dist node_modules bin obj coverage .cache tmp temp venv .venv __pycache__ .next .nuxt vendor`.
- 공통 제외 상대 경로: `logs/archive`.
- Unity 추가 제외: `Library Temp Logs UserSettings MemoryCaptures Recordings Build Builds`.
- 바이너리/생성물 제외: class/jar/war/exe/dll/so/dylib, 압축·이미지·오디오·영상·폰트 형식, package-lock.json/yarn.lock/pnpm-lock.yaml, `*.asset` 등 설정 패턴.
- 민감 파일명 차단: `.env`, `.env.*`, `application-prod.*`, `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*credential*`, `*secret*`, `id_rsa`, `id_ed25519`, `*api-key*`, `*apikey*`.
- 크기 상한: 5 MB 설정 = 5,242,880 bytes. 상한보다 큰 파일을 제외한다.

민감/제외/확장자/크기 정책을 Scan과 Read에서 재사용한다. 확장자별 본격 파서는 없다. PDF·DOCX·OCR·Unity Scene/Prefab 구조 분석을 지원한다고 적지 않는다.

제외 디렉터리도 **제외 파일 수 집계를 위해 내부 Metadata를 순회**한다. 내용 읽기를 제외하는 것과 디렉터리 탐색 I/O 자체를 없애는 것은 다르다. Project 순차 읽기이며 파일 병렬화/Virtual Thread를 구현하지 않았다.

### 3.4 안전 읽기·Document·오류

`ProjectDocumentReader.read(...)`는 상대 경로 정책 확인, normalize 후 containment, `toRealPath`의 실제 경로 containment, regular file/크기 확인 후 제한된 Stream으로 읽는다. 읽는 도중 파일이 커져도 크기 상한을 적용한다. UTF-8 decoder는 malformed/unmappable 입력에 `REPORT`를 사용하며 다른 인코딩을 임의 추정하지 않는다.

`WorkspaceDocument` 정보:

| 필드 | 의미 |
|---|---|
| projectName / projectId | 표시 이름 / Workspace 기준 상대 Project 식별자 |
| fileName / filePath | 이름 / 절대 파일 경로 |
| relativePath | Project Root 기준 상대 파일 경로 |
| extension / size / modifiedAt | 확장자 / 실제 읽은 bytes / 수정 시각 |
| content | 엄격한 UTF-8로 디코딩된 문자열 |

파일 결과에 `READ_SUCCESS`, `READ_FAILED`, `SKIPPED_SENSITIVE`, `SKIPPED_EXCLUDED_PATH`, `SKIPPED_EXTENSION`, `SKIPPED_TOO_LARGE` 등을 남긴다. 읽기 I/O·인코딩 실패를 파일별로 격리하고 Project 전체 결과에는 성공/실패/제외 수, 경로·이유, bytes·시간을 유지한다. Workspace Metadata Scan은 Project별 실패를 별도로 기록한다. Workspace Root 자체에 접근할 수 없는 문제까지 정상 프로젝트 결과로 바꾸는 것은 아니다.

보안 범위의 한계: 외부 경로로 나가는 Symlink 방어는 있으나, 프로젝트 내부 Symlink 대상이 제외/민감 경로인지 **실제 경로 기준 정책 재검사**까지 완결된 것은 아니다. 경로 검사와 open 사이 TOCTOU를 완전히 제거한 구현도 아니다. 일반 허용 파일 본문 안의 모든 비밀값을 DLP처럼 찾아 차단하지 않는다. 이는 코드 분석상 한계이며 이번 조사에서 악용 재현은 하지 않았다.

### 3.5 청크와 재인덱싱

[CharacterChunkSplitter][splitter]는 Text 최대 2,000자, Source 최대 2,400자, overlap 200자 baseline이다. Java String의 UTF-16 길이 단위이며 토큰 수 제한이 아니다.

- Text는 문단/Markdown heading 경계를 우선한다.
- Source는 닫는 중괄호가 있는 줄·빈 줄 등 구조 힌트를 우선한다.
- 적절한 경계가 없으면 줄바꿈 → 공백 → 고정 길이로 분할한다.
- 정확한 AST/메서드 단위 분할은 아니므로 함수가 중간에 나뉠 수 있다.
- 빈 Document는 청크가 없고, 짧은 Document는 단일 청크가 된다.

`DocumentChunk`: `chunkId`, `projectId`, `sourceFilePath`, `sourceFileName`, `extension`, `chunkIndex`, `content`, `startOffset`, `endOffset`, `startLine`, `endLine`, `sourceFingerprint`, `sourceModifiedAt`.

`sourceFilePath`는 현재 코드에서 **Project 기준 상대 경로**다. 초기 Decision Log 0011의 Workspace 상대 경로 표현보다 현재 생성 코드를 우선해야 한다. Offset 끝은 exclusive, 줄 번호는 1부터 시작한다.

문서 fingerprint는 전체 내용의 SHA-256이고, chunk ID는 Project ID·상대 경로·문서 fingerprint·청크 순번을 해시한다. 동일 내용/경로의 재생성은 안정적인 ID를 만든다. 내용 변경 시 문서 fingerprint가 바뀌므로 해당 문서의 청크 ID도 바뀐다.

**재인덱싱은 DB 중복 방지이지 변경 파일만 임베딩하는 증분 인덱싱이 아니다.** 매 요청 전체 허용 문서를 읽고 청크·Embedding을 다시 생성한다. Repository는 같은 ID에 upsert하고 현재 집합에 없는 기존 청크를 삭제한다. 변경 없는 행은 modifiedAt/model/dimension 비교 조건에 따라 DB 쓰기를 생략할 수 있다. Embedding 비용 자체는 이미 발생한 뒤다.

분석상 주의점:

1. `ProjectIndexService`는 Embedding 실패/차원은 검증하지만 앞 단계의 `sourceReadFailedCount`/청크 실패를 동기화 금지 조건으로 직접 사용하지 않는다. 남은 청크의 임베딩이 성공하면 실패 파일의 과거 청크가 stale로 삭제될 가능성이 있다. **재현하지 않은 코드상 위험**이며 “부분 읽기 실패에도 기존 인덱스 완전 보존”을 주장하면 안 된다.
2. chunk ID에 splitter 설정/버전은 포함되지 않는다. 분할 정책 변경 시 자동 일관성 보장을 가정하면 안 된다.
3. 문서가 모두 사라져 청크 0개가 된 경우에는 차원 검증 등 별도 경로가 있어, 어떤 빈 프로젝트든 정상적으로 인덱스를 비운다고 일반화하지 않는다.

## 4. Embedding / Vector Search

| 항목 | 현재 구현 |
|---|---|
| 모델 | `OLLAMA_EMBEDDING_MODEL`, 기본 `qwen3-embedding:0.6b` |
| 차원 | 인덱스 설정 expected-dimensions=1024, SQL `vector(1024)`와 CHECK |
| 호출 | Spring AI `EmbeddingModel.embed(text)` / `embed(List)`를 `EmbeddingService`로 감쌈 |
| 저장 | PostgreSQL `document_chunk_embedding`, 1행=청크 본문·Metadata·벡터 |
| Store | pgvector 확장 + JdbcTemplate 직접 SQL; Spring AI PgVectorStore 구현체 사용 아님 |
| Query | 앞뒤 공백 정리 → 기본 instruction → 같은 모델로 벡터화 → 차원 검사 |
| Project 필터 | SQL `project_id = ?` 강제 |
| Similarity | pgvector `<=>` cosine distance, `similarity = 1 - distance` |
| Top-K | 기본 5, 요청 override 1~20 |
| Threshold | 기본 0.45, 요청 override 허용 범위 0~1 |
| 정렬 | distance ASC, chunk_id ASC, LIMIT K |
| 검색 인덱스 | 일반 B-tree Project 인덱스; HNSW/IVFFlat 없음 |

앱은 청크 목록을 일괄 `embedAll`에 전달한다. 이를 반드시 HTTP 요청 한 번이라고 표현하지 않는다. SDK/공급자의 내부 분할은 별개다. 분류된 공급자 장애는 전체 실패로 반환하고, 일부 일반 batch 예외는 개별 청크 재시도로 분리한다. 반환 vector 수·차원 일관성을 검사하고 저장 단계에서 1024를 강제한다.

질문 instruction은 다음 한 가지가 기본 ON이다.

```text
Instruct: Retrieve the most relevant source code or project documentation for the software project query.
Query: <USER_QUERY>
```

`localrag.search.query-instruction.enabled/template`으로 설정하고 Search 요청의 `instructionEnabled=false`로 Raw 모드를 쓸 수 있다. Raw도 앞뒤 공백 정리를 하므로 byte-for-byte 원문 보존 모드라는 뜻은 아니다.

실제 검색 SQL의 핵심은 다음과 같다. 설명용 축약이며 정확한 원본은 [ProjectSemanticSearchRepository][searchrepo]에 있다.

```sql
-- project 범위에서 거리 계산 → threshold → 가까운 순 K개
embedding <=> CAST(? AS vector) AS cosine_distance
WHERE project_id = ?
... WHERE 1 - cosine_distance >= ?
ORDER BY cosine_distance ASC, chunk_id ASC
LIMIT ?
```

0.45는 확률/정답률 45%가 아니며 특정 모델·corpus에 맞춰 관찰한 유사도 절단값이다. Exact distance 검색이고 ANN은 없다. 실제 실행계획이 항상 `Seq Scan`이라고 단정하려면 EXPLAIN이 필요하다.

Metadata Filter는 Project ID가 핵심이다. 파일 타입/생성물 정책은 후속 애플리케이션 필터에도 있다. 검색 SQL은 저장된 `embedding_model`로 조건을 걸지 않으므로 **차원만 같은 다른 모델로 변경해도 검색 공간이 호환된다는 보장은 없다**. 모델 변경과 재인덱싱·DB 차원은 함께 고려해야 하는 운영 한계다.

## 5. LLM / Ollama 연동

### 호출·Prompt·Context

Spring Boot가 Spring AI의 Ollama ChatModel/ChatClient를 통해 기본 `http://localhost:11434`의 `qwen3:8b`를 호출한다. 직접 외부 상용 LLM API를 호출하는 기본 경로는 없다. Model pull 전략은 `never`다.

`RagChatService`에는 검색 근거만 사용하고 Source 내부 지시를 따르지 않으며 사용자의 언어로 답하고 Source ID를 인용하도록 하는 System Prompt가 있다. 사용자 메시지에 질문과 formattedContext를 넣는다. Context는 `[S1]`, Project, File, Lines, content 구획을 포함한다.

RAG 예산 8,000자는 Source 헤더·경로·줄·내용·구분자를 포함한 **formatted context** 길이다. similarity 높은 순으로 추가하고 넘치는 청크는 자르지 않고 제외한다. 다음 작은 청크는 들어갈 수 있다. similarity는 Metadata에 남지만 모델 Context 본문에는 넣지 않는다. System Prompt·질문까지 포함한 전체 입력을 정확히 8,000자로 제한하는 것은 아니다.

통합 채팅은 다른 제한을 추가한다. 기본 `numPredict=650`, context window 8192, temperature 0이며 thinking을 끈다. Knowledge Tool은 최대 6 Sources, 파일당 최대 2개, 약 6,500자 근거 예산을 사용한다. 실제 파일 Brief는 최대 8개 읽기 후보/6개 발췌, 합계 4,800자, 파일당 1,100자 제한이다. **이 값과 RAG 8,000자를 하나의 공통 전역 한도로 혼동하지 않는다.** 에이전트 상세의 기본 context window는 16,384다.

### 실패와 시간 제한

| 위치 | 확인된 처리 |
|---|---|
| RAG 검색/Context | 검색 실패와 근거 없음 분리; NO_EVIDENCE에서는 생성 호출하지 않음 |
| 모델 호출 | 예외를 서비스 상태·안내 문구로 변환 |
| 통합 채팅 완료 | ChatResponse finish reason + 실제 HTTP의 done/stop 정보 확인, length/unknown/누락 등은 `LLM_FAILED` |
| 통합 근거 검사 | 지원하는 복합 식별자/파일명이 근거에 없으면 `INSUFFICIENT_EVIDENCE` |
| Git/Docker 명령 | 기본 5초와 출력 크기 제한 |
| 환경 HTTP 조회 | 기본 3초; LLM 생성 timeout과 다른 설정 |
| Tauri→Backend Bridge | connect 3초, 전체 요청 120초 |
| Backend Ollama 생성 | 저장소에서 명시적인 생성/Embedding transport timeout 설정은 확인되지 않음; SDK 기본값을 임의로 적지 않음 |

정상 stop도 의미적으로 완전하거나 참인 답을 보장하지 않는다. 완료 검사·어휘 검사는 통합 채팅에 적용된 수정이며 기존 plain Chat/RAG/에이전트 전체 경로에 동일하게 적용됐다고 말하면 안 된다. 자동 재시도·스트리밍을 새로 추가하지 않았다.

### 모델 변경 가능 범위

Backend 모델/URL은 `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `OLLAMA_BASE_URL`로 바꿀 수 있다. 그러나 Desktop 시작 시 필수 모델 체크와 Settings 표시에는 현재 baseline 모델 이름이 고정된 부분이 있다. UI에서 모델을 고르는 기능은 없다. 벡터 DB는 1024차원 고정이며 모델 변경을 완전 동적 지원한다고 표현하기 어렵다.

## 6. Source / Grounding 처리

### Source 생성과 UI 연결

| 항목 | 코드로 확인한 사실 |
|---|---|
| Chunk→Source | Context에 실제 포함한 청크에 Source ID를 순차 배정 |
| Source Metadata | ID, Project, chunk ID, 상대 파일 경로/이름, 줄 범위, similarity 및 content는 DTO별로 선택적으로 포함 |
| Citation 표기 | 실제 형식은 `[S1]`, 통합/Agent Knowledge는 `[K1-S1]` 등. 질문 예시의 `[§1]` 방식이 아님 |
| 생성 책임 | Source ID는 애플리케이션, 답변 속 Citation 위치는 LLM이 작성 |
| 검사 | 정규식으로 단일 ID 표기 추출, 허용 ID 집합과 비교. 주장 의미와 Source 의미의 일치까지 검사하지 않음 |
| RAG 상세 | Source 클릭 → 안전 Document Read API → 현재 파일 내용의 해당 줄 표시 |
| 통합 채팅 | 수집된 Source의 경로·줄·근거와 Tool Evidence 표시; 외부 코드 에디터를 여는 기능은 아님 |

기본 RagChatSource 응답은 경로·줄 등의 추적 정보 중심이다. 내부 RagContextSource처럼 언제나 content/similarity를 전부 포함하는 동일 DTO가 아니다. Context Preview API로 formattedContext를 별도 확인할 수 있다.

Source 클릭 시 **현재 파일**을 다시 읽는다. 인덱싱 당시 스냅샷을 버전 고정해서 제공하지 않으므로 파일 변경 후 줄 번호와 내용이 어긋날 수 있다.

### 환각을 줄이는 실제 장치와 경계

1. 프로젝트 범위 검색·도구 실행, 외부 경로 차단, 민감/제외 정책.
2. 근거 없는 pure RAG 질문은 생성하지 않음.
3. Source를 지시로 신뢰하지 않도록 System Prompt에 명시.
4. Path 기반 [ProjectEvidencePolicy][evidencepolicy]로 생성물·외부 패키지·불필요한 라이선스/Agent 지침 문서를 일반 질문의 주요 근거에서 배제.
5. 통합 채팅에서 완료 여부와 근거 밖 복합 PascalCase/파일명을 검사. `ReservationLockService는/가`처럼 한국어 조사가 붙어도 ASCII 식별자 경계로 대조.
6. 통합 경로의 일부 인용 누락/잘못된 ID는 답변 보류. pure RAG는 Citation 경고를 포함한 성공 상태를 반환할 수 있음.

어휘 검사는 “Source에 이름이 있다”만 확인할 수 있다. 단순 언급/테스트/문서의 이름이 실제 구현을 보증하지 않으며 자연어 기능 주장, 일반 지식 오류, 단순 이름/약어를 모두 검증하지 못한다. `[S1]`이 유효해도 그 Source가 해당 주장을 뒷받침하지 않을 수 있다. 복잡한 자동 의미 검증기나 LLM judge는 구현하지 않았다.

## 7. Frontend

React UI는 현재 10개 화면이다. [App.tsx][app]가 라우트 성격의 화면 전환과 Project 선택 상태를 관리하고 API 모듈이 Backend와 연결한다. Web 환경은 fetch, Desktop은 Tauri 로컬 Bridge를 이용한다.

| 기능/화면 | 실제 구현 | 연결 API 또는 파일 |
|---|---|---|
| 작업공간·Project | 서버의 기본 Workspace 표시, 탐지 Project 선택·localStorage 유지 | `GET /api/workspaces/projects`, `/discovery`; `workspaceApi.ts` |
| 대시보드 | 환경·Project 개요, Git 상태, 파일 기반 언어 분포, 인덱스 상태 | `/overview`, `/index/stats`; `DashboardPage.tsx`, `ProjectList.tsx` |
| 인덱싱 | 선택한 Project 명시적 수동 실행 | `POST /api/workspaces/projects/index?projectId=...`; `indexApi.ts` |
| 메인 채팅 | 통합 답변, 근거·Tool 정보, 경로/실패 안내 | `POST .../chat/unified`; `UnifiedChatPage.tsx` |
| 에이전트 상세 | 읽기 전용 Tool 선택/실행 결과·시간 조회 | `POST .../agent/chat`; `AgentPage.tsx` |
| 지식 검색 상세 | pure RAG 답변, Source 및 파일의 해당 줄 미리보기 | `POST .../rag/chat`, `POST .../documents/read`; `RagPage.tsx` |
| 입력 UX | Enter 전송, Shift+Enter 줄바꿈, 한글 IME 조합/키 반복 방어, 전송 중 중복 방어 | `lib/chatInput.ts`, ChatInput/ChatDraft 테스트 |
| 전송 후 입력 | 즉시 비우고 통신 실패 시 복원. 기다리는 동안 새로 쓴 내용은 덮어쓰지 않음 | Unified/Agent/Rag 페이지 |
| 상태 | Loading, 통신 실패, NO_EVIDENCE/근거 부족/LLM 실패별 안내 | 공통 API client + 각 페이지 |
| 오류 관리 | 분석, 이력 명시적 저장, 필터·상세·검증 상태·유사 오류 | `ErrorsPage.tsx`, `errorApi.ts` |
| 진행 상태/최근 작업 | 근거를 모아 분석·요약 표시 | `/progress/analyze`, `/activity/summary` |
| 자동화 | 옵션/간격 저장, Run Now, Run History | `/automation`, `/automation/run`, `/automation/runs` |
| 알림 | 저장된 Notification Candidate 목록 | `/automation/notifications` |
| 설정/시작 | 서비스 상태·모델 baseline 표시, 시작 재시도 | `SettingsPage.tsx`, `StartupScreen.tsx`, Rust startup commands |

현재 없는 것: UI Workspace 경로 CRUD, 파일 업로드 UI, Top-K/Threshold 조절 UI, 모델 선택 UI, 지속 대화 Memory, Streaming, 실제 외부 에디터 열기. 검색 설정은 API/서버 설정에서 바꿀 수 있으나 화면 문구는 baseline이 고정된 부분이 있다.

추가 주의:

- 화면의 언어 비중은 파일 수 기반 Metadata이지 코드 라인 수/개발 기여도 비중이 아니다.
- Git 원격 ahead/behind는 로컬 upstream 참조 기준이며 자동 fetch로 서버 최신 상태를 보장하지 않는다.
- `NOT_CONFIGURED`는 해당 Project Compose 등의 설정이 없다는 뜻이지 전체 Docker가 고장 났다는 뜻은 아니다.
- UI에는 현재 응답/질문 상태가 있지만 이전 대화를 모델에 누적 전달하는 Conversation Memory는 아니다.

## 8. API 구조

아래 표에서 `P = /api/workspaces/projects`, `W = /api/workspaces`다. `?` 뒤 값은 Query Parameter, `{...}`는 주요 JSON 필드다. 전체 DTO의 모든 필드를 복제한 API 명세가 아니라 포트폴리오용 핵심 계약 목록이다. 동일 이름 Controller와 Request/Response 소스는 [Backend 소스 디렉터리][java]에서 확인할 수 있다.

### 핵심 수집·AI·검색 API

| Endpoint | Method | Request | Response | 역할 / 관련 Service |
|---|---|---|---|---|
| `/api/health` | GET | 없음 | 애플리케이션 상태 | HealthController; 의존 서비스 전체 진단과 다름 |
| `/actuator/health` | GET | 없음 | Actuator health | Spring Boot health |
| `/api/chat` | POST | `{message}` | answer, model | ChatService, 일반 Ollama 호출 |
| `/api/embeddings` | POST | `{text}` | model, dimensions, vector 앞부분 | EmbeddingService; DB 저장 없음 |
| `W/projects` | GET | 없음 | DetectedProject 목록 | ProjectDiscoveryService |
| `W/discovery` | GET | 없음 | WorkspaceDiscoveryResult | Project/Container 구분 |
| `W/scan` | POST | 없음 | WorkspaceScanSummary | WorkspaceMetadataScanService; 여러 Project Metadata만 |
| `P/scan` | POST | `?projectId` | ProjectScanResult | ProjectFileScanner |
| `P/documents/read` | POST | `?projectId&filePath` | DocumentReadResult | ProjectDocumentReader |
| `P/documents/read-all` | POST | `?projectId` | ProjectDocumentReadResult | ProjectDocumentBatchReader |
| `P/chunks/preview` | POST | `?projectId` | ProjectChunkingResult | ProjectChunkingService; 영속화 안 함 |
| `P/chunks/embeddings/preview` | POST | `?projectId` | 청크별 Embedding 결과·차원·preview | ProjectChunkEmbeddingService; DB 저장 안 함 |
| `P/index` | POST | `?projectId` | ProjectIndexResult | ProjectIndexService; 실제 저장 |
| `P/index/stats` | GET | `?projectId` | ProjectIndexStats | ProjectIndexService/Repository |
| `P/search` | POST | `{projectId, query, topK?, threshold?, instructionEnabled?}` | ProjectSemanticSearchResponse | ProjectSemanticSearchService |
| `P/rag/context/preview` | POST | `{projectId, query}` | RagContextAssemblyResult | RagContextAssemblyService |
| `P/rag/chat` | POST | `{projectId, query}` | RagChatResponse | RagChatService; 검색 근거 기반 답변 |
| `P/agent/chat` | POST | `{projectId, query}` | AgentChatResponse | AgentChatService; 상세 Tool Calling |
| `P/chat/unified` | POST | `{projectId, query}` | 답변·상태·도구·근거·진단·시간 | UnifiedChatController/AgentChatService |
| `W/overview` | GET | 없음 | Workspace Summary | WorkspaceOverviewService |
| `P/overview` | GET | `?projectId` | ProjectOverview | ProjectOverviewService |

호환용 `P/{projectName}/scan`, `/documents/read`, `/documents/read-all`도 남아 있다. 중첩 Project를 설명할 때에는 현재 주 경로인 `projectId` Query Parameter 방식을 사용한다. RAG Chat 요청은 Search의 Top-K/Threshold 옵션을 그대로 노출하지 않고 baseline을 사용한다.

### 오류·워크플로·자동화 API

| Endpoint | Method | Request | Response | 역할 / 관련 Service |
|---|---|---|---|---|
| `P/errors/analyze` | POST | `{projectId, query}` | ErrorAnalysisResult | ErrorAnalysisService; 저장 전 분석 draft |
| `P/errors/history` | POST | `{projectId, analysisId}` | 저장 결과 | ErrorHistoryService; 서버 분석 snapshot만 저장 |
| `P/errors/history` | GET | Project, 상태/유형/메시지/시간/파일/commit 필터, page/size | 이력 page | ErrorHistoryQueryService |
| `P/errors/history/{id}` | GET | `?projectId` | 이력·검증 감사 | ErrorHistoryQueryService |
| `P/errors/history/{id}/status` | PATCH | Project, status, verification note, cause/solution, expectedVersion | ErrorHistoryView | ErrorHistoryService; 낙관적 잠금·상태 전이 |
| `P/errors/similar` | POST | Project, errorMessage, errorType/symptom/Top-K 선택값 | ErrorSimilarityResponse | ErrorSimilarityService |
| `P/progress/analyze` | POST | `{projectId}` | ProjectProgressResponse | ProjectProgressService |
| `P/activity/summary` | POST | `{projectId, since?, commitLimit?}` | DevelopmentActivityResponse | DevelopmentActivityService |
| `P/automation` | GET | `?projectId` | ProjectAutomationConfigView | ProjectAutomationConfigService |
| `P/automation` | PUT | Project, 활성 여부/간격/개별 옵션 | 설정 결과 | ProjectAutomationConfigService |
| `P/automation/run` | POST | `{projectId}` | AutomationExecutionResult | AutomationExecutionService |
| `P/automation/runs` | GET | Project, page/size | Run page | AutomationHistoryService |
| `P/automation/notifications` | GET | Project, page/size | Candidate page | NotificationCandidateService |

API의 업무 실패가 모두 HTTP 500은 아니다. 정상적인 HTTP 응답 안의 도메인 status가 NO_EVIDENCE/FAILED일 수도 있다. 이를 모델 품질 평가에서 HTTP 성공률과 구분해야 한다. 인증·사용자별 소유권 체계를 갖춘 공개 SaaS API가 아니라 로컬 사용 전제다.

## 9. DB 구조

근거: [V1~V6 Migration][migrations]. Flyway가 Schema를 만들고 Hibernate `ddl-auto=validate`로 확인한다. 기본 DB 이름은 `local_ai_workspace`다. Flyway 자체 이력 테이블을 제외하면 애플리케이션 테이블은 8개다.

| Table | 주요 Column / Key | 역할·Index |
|---|---|---|
| `document_chunk_embedding` | chunk_id PK, project_id, source_path, source_file_name, extension, chunk_index, content, start/end_offset, start/end_line, source_fingerprint, source_modified_at, embedding_model, embedding_dimension, embedding, indexed_at | Chunk+vector 함께 저장. vector(1024), 차원/범위 CHECK. B-tree project_id 및 (project_id, source_path) |
| `error_history` | id PK, analysis_id UUID UNIQUE, project_id, occurred/recorded time, error_type/message, symptom, root_cause, solution, status, related_files/commits/evidence_summary JSONB, version 등 | 오류 이력. Project/시각·상태·유형 조회 Index, related JSONB GIN |
| `error_history_verification` | id PK, error_history_id FK, from/to status, 원인/해결/확인 메모, actor, changed_at, previous_version | 상태 변경 감사. history FK는 ON DELETE CASCADE, 이력별 시간 Index |
| `error_history_embedding` | error_history_id PK/FK, project_id, embedding_model, embedding_dimension, source_fingerprint, embedding, indexed_at | 오류 정규화 텍스트 vector(1024). History와 1:0..1, Project Index |
| `project_automation_config` | project_id PK, enabled, 작업별 옵션, interval_seconds, 마지막/다음 실행 시각, fingerprint들, version | Project별 자동화 설정. interval>=60, (enabled,next_run_at) Index |
| `automation_run` | id PK, project_id, trigger_type, 시작/종료, status, change_detected, summary/error, result_details JSONB, duration | 자동/수동 실행 이력. Project·시작 시각 Index |
| `detected_error_event` | id PK, project_id, fingerprint, 로그 위치/시간/메시지, similar 상태/수, top_similar_history_id/similarity | 새 오류 감지 기록. UNIQUE(project_id,fingerprint), Project·시간 Index |
| `notification_candidate` | id PK, project_id, automation_run_id FK, type, fingerprint, title/summary, created/acknowledged time | 알림 후보. UNIQUE(project_id,type,fingerprint), Project·시간 Index |

실제 FK 관계만 그리면 다음과 같다.

```text
error_history 1 ── N error_history_verification
              └─ 0..1 error_history_embedding
automation_run 1 ── N notification_candidate
```

`detected_error_event.top_similar_history_id`는 연관 ID 값이지만 SQL FK 선언은 없다. `notification_candidate.automation_run_id`는 실행 이력 삭제 시 NULL이다. Project ID는 문자열 범위 키이며 **Workspace/Project/Document를 각각 Entity로 정규화한 테이블은 없다**. 그러한 ERD를 새로 그려 현재 구현처럼 넣으면 안 된다.

### Backend 관점에서 설명할 가치가 있는 지점

- 벡터와 추적 Metadata를 한 행에 두어 검색 결과에서 원문 위치를 바로 반환한다.
- ID 기반 upsert + Project 단위 stale 삭제를 트랜잭션으로 묶는다.
- 오류 분석은 서버에 보관한 draft를 명시적으로 저장하며, analysis_id UNIQUE로 중복 저장을 방어한다.
- 오류는 UNVERIFIED → VERIFIED → RESOLVED 상태 전이와 확인 메모/원인/해결 요건이 있다. expectedVersion 및 JPA `@Version`으로 경쟁 업데이트를 방어하고 감사 기록을 남긴다.
- `LOCAL_USER_ATTESTATION`은 로컬 사용자의 확인 기록이지 로그인 인증된 사용자 신원의 증명이 아니다.
- 유사 오류는 같은 Project, 별도 threshold 0.65/Top-K 5로 검색한다. 미검증 원인/해결책을 확정 사실처럼 노출하지 않는다. “유사한 오류=같은 원인”으로 취급하지 않는다.
- 오류 이력 Embedding은 정규화 텍스트 fingerprint/model/dimension으로 변경 없는 재생성을 피하는 경로가 있다. **Project 전체 인덱싱의 비증분 방식과 구분**해야 한다.

ERD의 포트폴리오 가치는 있다. 다만 RAG만 소개하는 3쪽 구성이라면 Chunk 테이블 + 검색 SQL이 전체 8-table ERD보다 중요하다. Backend 상태 관리까지 강조하는 4쪽 구성에서는 위 3개 관계와 낙관적 잠금·감사 흐름을 작은 보조 그림으로 쓰는 것이 좋다.

## 10. Docker / 실행 환경

### 실제 구성

[compose.yml][compose]은 PostgreSQL 서비스만 정의한다. 이미지 `pgvector/pgvector:0.8.6-pg17`, 기존 컨테이너 이름 `local-ai-postgres`, named volume, `pg_isready` healthcheck를 사용한다. Backend와 Ollama까지 모두 Docker Compose로 운영하는 구성은 아니다.

Ollama는 Windows의 로컬 서버 프로세스이고 Backend는 Java JAR, Frontend는 Tauri 리소스다. [startup.rs][startup]의 기본 시작 흐름은 다음과 같다.

```text
Tauri 시작 창
 → Docker Desktop/Engine 확인·필요 시 자동 시작
 → 소유권을 확인한 기존 PostgreSQL 컨테이너 재사용/시작, health 대기
 → Ollama 확인·필요 시 serve 시작
 → 필수 모델 설치 여부 확인
 → Java 17과 Backend 상태 확인·필요 시 번들 JAR 시작
 → Dashboard
```

각 준비 단계의 시간 예산은 Docker 180초, PostgreSQL 90초, Ollama 60초, 모델 5초, Backend 90초다. 이는 RAG 질문 응답 시간 제한과 다르다. Docker 시작 명령, loopback Ollama, 컨테이너 label/소유권, 백엔드 health를 확인하며 숨김 프로세스로 실행한다. 컨테이너·volume 삭제/초기화를 자동 복구 수단으로 쓰지 않는다.

Docker 자체의 stale runtime/socket 문제와 앱의 orchestration 문제는 별개다. 과거 runtime 복구 기록이 있다고 해서 LocalRAG가 Docker 내부 파일을 항상 자동 정리하는 기능을 가진 것은 아니다.

### 환경변수·배포 조건

| 설정 | 기본 의미 |
|---|---|
| `LOCALRAG_WORKSPACE_ROOT` | `C:/workspace` |
| `DB_URL` | localhost:5432/local_ai_workspace |
| `DB_USERNAME`, `DB_PASSWORD` | 로컬 개발 기본값; 운영 보안 설정으로 간주하면 안 됨 |
| `OLLAMA_BASE_URL` | localhost:11434 |
| `OLLAMA_CHAT_MODEL` | qwen3:8b |
| `OLLAMA_EMBEDDING_MODEL` | qwen3-embedding:0.6b |
| `JAVA_HOME` / PATH | Desktop에서 Java 17 발견 |
| `OLLAMA_HOST` | Desktop 자동 시작 시 로컬 bind에 사용 |

NSIS/실행파일과 함께 Backend JAR·runtime compose 리소스가 필요하다. Docker Desktop, Java 17, Ollama, 모델, 이미지, WebView2 등의 선행 준비가 필요하며 이를 전부 번들 설치하지 않는다. Docker가 없는 다른 PC에 exe 하나만 복사하면 바로 모든 기능이 된다고 설명하면 안 된다.

### “로컬 RAG”라는 표현의 타당성

**기본 설정 기준으로 타당하다.** 파일 읽기·DB·벡터 생성·답변 생성이 로컬 프로세스/컨테이너에서 수행되며 상용 외부 LLM API 키를 요구하지 않는다. 이미지·모델·의존성을 미리 준비하면 런타임 핵심 처리는 외부 API 없이 수행할 수 있는 구조다.

단, 최초 설치/다운로드/빌드에는 네트워크가 필요할 수 있다. DB/Ollama URL을 원격으로 설정할 수도 있다. 이번 조사에서 네트워크를 실제 차단한 전체 기능 시험은 하지 않았다. 따라서 “어떤 설정에서도 100% 오프라인”, “자료가 절대로 외부로 나가지 않음”이라는 보장은 하지 않는다.

Desktop은 Backend를 `127.0.0.1:18080`으로 실행하고 Bridge 경로·메서드·redirect를 제한한다. 반면 application.yml 자체는 standalone 실행용 `server.address`를 고정하지 않고 Compose 기본 5432 노출도 loopback으로만 한정되어 있지 않다. 인증/다중 사용자 격리가 없는 로컬 도구이므로 외부 공개 운영에 적합하다는 주장은 근거가 없다.

## 11. 테스트 및 검증

### 11.1 실제 코드 개수와 기존 실행 결과

이번 조사에서 Backend 소스의 `@Test`/`@ParameterizedTest`와 기존 JUnit XML을 대조했다. **새로 실행한 결과가 아니다.**

| 구분 | 코드 기준 | 마지막 확보 결과 | 근거 |
|---|---|---|---|
| Backend | 일반 @Test 209개 + ParameterizedTest 1메서드의 6입력 = 215 실행 사례, 72 suite | 214 통과, 1 skip, 실패/오류 0 | `src/test`, `build/test-results/test/TEST-*.xml` |
| Frontend | 11 test 파일, parameterized 확장 포함 43 사례 | 43 통과 | `frontend/src/**/*.test.*`, Decision 0036 |
| Rust | startup/bridge의 10 test | 10 통과 | startup.rs/lib.rs, Decision 0036 |
| 최종 실제 질문 | 5개, 각 1회 | 정상/실패 상태와 의미 평가가 혼재 | Decision 0036, build/unified-quality/release-20260928 |

Backend skip은 실모델 Agent 평가의 opt-in 조건이다. 일반 테스트를 실행할 때마다 모델 품질 전체를 검증했다는 뜻은 아니다. “215개의 독립 테스트 메서드”가 아니라 **215개 실행 사례**라고 적는 것이 정확하다.

### 11.2 주요 Backend 테스트 분류

소스 위치: [src/test/java/com/localai/workspace][tests]. 아래 수는 parameterized 확장을 포함한 사례 수이며 합계 215다.

| 영역 | 사례 수 | 주요 테스트와 확인 대상 |
|---|---:|---|
| discovery | 13 | ProjectIdTest, ProjectDiscoveryServiceTest, ProjectTypeDetectorTest, WorkspaceControllerTest: 경로/중첩/Container/depth/marker |
| scan | 5 | ProjectFileScannerTest, WorkspaceMetadataScanServiceTest 및 Controller: Unity 제외·민감/대형 파일·Project 실패 격리 |
| document | 9 | ProjectDocumentReaderTest, BatchReaderTest 및 Controller: UTF-8·실제 내용·외부 링크/경로 차단·단일 실패 후 계속 |
| chunk | 9 | DocumentChunkingServiceTest, ProjectChunkingServiceTest/Controller: 빈/짧은/긴 텍스트·경계·중첩·위치·ID |
| embedding | 12 | EmbeddingServiceTest, ProjectChunkEmbeddingServiceTest 및 Controller: 벡터 연결·차원·일괄 실패·결과 수·부분 처리 |
| index | 8 | ProjectIndexServiceTest, RepositoryIntegrationTest, Controller: 저장 검증·upsert·stale 삭제·범위·rollback |
| search | 10 | ProjectSemanticSearchServiceTest, RepositoryIntegrationTest, Controller: query instruction/raw·topK/threshold·거리 정렬·Project 격리 |
| rag | 19 | RagContextAssemblyServiceTest, RagCitationValidatorTest, RagChatServiceTest 및 Controller: 예산·Source·no-evidence·ID 경고 |
| agent | 74 | UnifiedChatTest, AgentChatService/ToolExecutionTest, Git/Docker/DB/Ollama/Log 테스트, ErrorAnalysisEvidenceTest, BoundedLiveProcessTest 등: 허용 도구·범위·출력·실패·근거 |
| chat | 15 | ChatControllerTest, OllamaCallProfilerTest, UnifiedToolRoundTest: 호출·done/finish·도구 실행 제한·진단 |
| errors | 12 | ErrorAnalysisDraftTest, ErrorProjectScopeTest, History/SimilarityIntegrationTest: 서버 draft·검증 상태·감사·version·유사 검색 |
| workflow | 3 | DeveloperWorkflowServiceTest: 실제 근거/최근 작업·미실행 정보의 한계 |
| automation | 13 | 실행/스케줄러/Controller/영속성/지문/오류·환경 watch: 중복·변경 없음·부분 실패·설정 |
| overview | 10 | WorkspaceOverview/ProjectOverview/ProjectMetadataAndBrief/ProjectEvidencePolicyTest: 표시 정보·실제 파일 발췌·noise 정책 |
| config | 1 | DesktopCorsConfigurationTest |
| health | 1 | HealthControllerTest |
| Application | 1 | LocalAiWorkspaceApplicationTests |

Testcontainers/SpringBoot 기반 DB 통합은 Application, ProjectIndexRepository, ProjectSemanticSearchRepository, ErrorHistory, ErrorSimilarity, AutomationPersistence의 6개 suite/15사례다. 실제 pgvector SQL을 테스트한다. 나머지를 전부 순수 unit test라고 단순 분류하면 안 되며 MockMvc·파일시스템 fixture 등의 서비스/웹 계층 테스트가 섞여 있다.

Embedding/LLM 단위 테스트는 주로 mock으로 제어 흐름을 검증한다. 벡터 SQL 통합 테스트도 정해진 벡터로 정렬·필터를 확인하는 것이며 자연어 검색 정확도 검증과 다르다.

Frontend는 API 오류/bridge 설정, App/ProjectList, 오류·자동화·RAG·Agent·통합 페이지, Enter/IME, 입력 비우기·실패 복원을 검증한다. Rust는 준비 순서·기존 서비스 재사용·실패/제한 및 Bridge 입력을 검증한다. 모든 테스트가 실제 Windows UI와 Docker Desktop을 띄우는 E2E는 아니다.

### 11.3 기존 평가에서 쓸 수 있는 수치

| 시점·조건 | 기록 | 포트폴리오 해석 |
|---|---|---|
| Phase 5 Step 9, 중첩 Root 정책 수정 | 포함 파일 8,614 → 680, 대형 제외 1 → 0 | Unity Library를 실제 Project 타입 정책으로 제외한 사례. 검색 정확도 수치 아님 |
| Phase 5 종료의 별도 snapshot | 전체 107,086파일, 포함 686, 제외 106,400, Scan 3,978ms; Local_Ai_Work 73문서 읽기 82ms | 위 680과 시점이 다름. 파일시스템 cache/당시 corpus 의존, 현재 측정 아님 |
| Phase 6 Step 4, 208청크·10질문 | warmed 평균 Query Embedding 30.6ms, DB 검색 12.9ms, 전체 검색 45.2ms | LLM 생성 시간이 빠진 검색 기준값 |
| Step 4.1, 동일 질문 threshold .50→.45 | Embedding 관련 결과 수 2→5, 재인덱싱 0→2, 중첩 탐지 2→5; Kafka 0 유지 | 관련 회수 개선 관찰. 결과 수 증가를 그대로 Recall 수치로 환산하지 않음 |
| Step 4.2, instruction 비교 | 민감 파일 0→5, Workspace 경계 0→5; 일부 rank 악화도 발생 | 한 변수 비교 사례, 모든 질문에서 성능 향상이라고 할 수 없음 |
| Phase 6 종료, 259청크·14질문 | 9 PASS / 4 PARTIAL / 1 FAIL, 13 LLM 호출 평균 10,990ms | 수작업 소규모 평가. “정답률 64%” 같은 일반화 금지 |
| 최종 2026-09-28, 통합 채팅 5질문 | 1.874~14.683초, 미완료/허위 symbol 차단 재현, 의미 오류 잔존 | 안전장치 평가와 답변 정확도는 별도 |

관련 원본: [Decision 0009][d9], [0010][d10], [0014][d14], [0015][d15], [0016][d16], [0020][d20], [0036][d36]. 기존 `phase10-portfolio-metrics.md`의 이전 테스트 수·화면 수는 당시 snapshot이며 현재 수와 혼용하지 않는다.

### 11.4 미측정 항목과 측정 가능성

이번 요청에서는 추가 측정하지 않았다. 아래는 기존 증거로 무엇을 주장할 수 있는지의 판단이다.

| 항목 | 현재 증거 | 객관적 수치화 가능 여부 / 부족한 자료 |
|---|---|---|
| 검색 성공률 | Query별 결과 수·유사도 | 결과 1개 이상 비율은 계산 가능하지만 정답 검색률과 다름. 정답 Source 라벨 필요 |
| 관련 Chunk 검색률 | 10질문 rank 관찰 | Recall@K/Precision@K에는 relevant 청크 집합·판정 규칙이 부족 |
| Source 정확도 | ID validator 및 수작업 의미 평가 | ID validity와 semantic correctness를 분리해야 함. 대규모 독립 라벨 없음 |
| 답변 정확도 | PASS/PARTIAL/FAIL 소규모 기록 | 일반 정확도/통계 신뢰구간을 주장할 데이터 아님 |
| 일반 LLM vs RAG | 별도 호출 API 존재 | 동일 질문·모델·조건 통제 비교 결과 없음 |
| Top-K 비교 | 요청 override 및 기본 5 | K를 바꾼 체계적 ablation 결과 없음 |
| Threshold 비교 | .50 vs .45 동일 10질문 비교 기록 | 제한된 corpus에서의 비교는 사용 가능; 전역 최적값 증명 아님 |
| Instruction 비교 | 같은 10질문 raw vs instruction | 개선과 퇴행을 함께 제시 가능 |
| 지연/자원 | 단계별 시간·token 진단 | cold/warm, GPU/CPU, 동시성 통제 반복 통계 없음 |
| 코드 커버리지 | 테스트 존재·실행 결과 | JaCoCo 등 실제 커버리지 결과 확인되지 않음. 커버리지 % 생성 금지 |

특히 평가 질문/답변을 Decision Log에 남긴 뒤 그 문서를 다시 인덱싱하면, 원래 없던 Kafka도 평가 문서에서 검색될 수 있다. Phase 6 기록에 이런 corpus 오염 문제가 관찰되었다. “존재하지 않는 질문은 언제나 0건”이라고 보편화하면 안 된다.

## 12. 기술적 의사결정

결정의 근거는 코드가 보여 주는 구조와 당시 Decision Log의 설명을 분리한다. 기록된 선택 이유도 다른 기술보다 객관적으로 우월하다는 실험 증명은 아니다.

| 선택 | 확인 가능한 이유·근거 | 주장하면 안 되는 것 |
|---|---|---|
| Java 17 | 기존 활성 JDK 유지 요구, Boot/AI 호환 기준 고정. Decision 0001 | Java 21보다 성능이 좋다는 비교 결과 |
| Spring Boot | Java 서비스·REST·설정·영속성·테스트 통합에 사용, 3.5.16/AI 1.1.8 baseline 기록 | 타 프레임워크 대비 선택의 정량 우위는 코드만으로 확인 불가 |
| Spring AI | ChatClient/EmbeddingModel/Tool Calling을 Spring 서비스에 연결 | 직접 모델을 학습하거나 Embedding 알고리즘을 구현했다는 주장 |
| Ollama | 로컬 모델 실행과 애플리케이션 호출 경계 분리, 명시적 사전 모델 준비. Decision 0003 | 모든 외부 네트워크 차단/보안 인증을 보장 |
| qwen3:8b | 지정된 로컬 Chat baseline, 실제 평가 기록 존재 | 다른 LLM과의 동일 조건 비교로 최적임을 입증한 이유는 확인 불가 |
| qwen3-embedding:0.6b | 한국어/영어/코드 사용, 로컬 크기·운용 baseline, 실제 1024차원 확인. Decision 0004 | 다양한 임베딩 후보에 대한 엄밀한 최상위 성능 검증 |
| PostgreSQL+pgvector | 관계형 이력/Metadata와 벡터를 한 DB에 두고 SQL·트랜잭션 재사용. Decision 0002/0013 | 전용 Vector DB보다 대규모 성능이 우수하다는 미측정 비교 |
| 문자·구조 힌트 chunking | 초기 단계의 정확한 모델 tokenizer 의존성·AST 구현을 늘리지 않는 baseline. Decision 0011 | 의미 단위/메서드 단위를 완벽히 보존 |
| Top-K 5 | 작은 corpus 평가용 baseline. Decision 0014 | 모든 프로젝트에 최적인 K를 찾았다는 주장 |
| Threshold .45 | .50에서 관련 결과 누락 → 동일 10질문 비교로 일부 복구. Decision 0015 | .45는 정답 확률이거나 전 범용 모델 기준 |
| Query Instruction ON | 변경 변수를 하나로 제한한 비교에서 민감/경계 회수 개선. Decision 0016/0017 | 모든 rank가 좋아졌다는 주장; Raw 전환 구조가 남아 있음 |
| Context 8,000자 | 헤더까지 포함한 명시적 예산, 청크 통째 선택. Decision 0018 | 8,000토큰/전체 Prompt의 엄격한 tokenizer 한도 |
| ANN 미적용 | 당시 208청크의 소규모 baseline에서 exact cosine부터 검증. Decision 0014 | 현재 수백만 청크 성능을 검증 |
| Tauri | 로컬 Web UI와 Java Backend를 Windows 앱으로 연결, 시작 단계 명시. Decision 0032/0033 | Java/Ollama/Docker까지 포함한 완전 self-contained 배포 |

면접에서는 “유명해서 선택”보다 **현재 문제의 범위에서 어떤 복잡도를 보류했고 무엇을 실제 검증했는지** 설명할 근거가 충분하다. 초기 개인 선택 동기 전체나 후보 비교 과정은 문서에 없는 부분을 사용자가 직접 보완해야 한다.

## 13. 문제 해결 사례 후보

### A. 중첩 Unity Root 오탐지로 캐시 파일이 수집되던 문제 — 최우선 추천

| 항목 | 근거 기반 내용 |
|---|---|
| 문제 | `Toy_Sports_Day`를 UNKNOWN으로 처리하면서 실제 하위 Unity Project의 Library가 일반 파일 정책으로 Scan 대상에 들어감 |
| 원인 | 상위 폴더를 Project Root로 가정하여 Unity marker를 놓침. 타입 의존 제외 정책도 적용되지 않음 |
| 분석 과정 | UNKNOWN 목록·marker 위치·대형 파일 경로를 확인하고 Container와 실제 Project Root를 구분 |
| 해결 | Workspace 직계 UNKNOWN 후보에서 한 단계만 추가 탐지, 부모 Container/자식 Project 분리, 실제 타입의 정책 적용 |
| 검증 | Discovery depth/Container 테스트, Unity Library 제외 테스트, 실제 Workspace Metadata Scan 비교 |
| 결과 | 당시 포함 파일 8,614→680, 대형 제외 1→0. 줄어든 것은 수집 후보 수이지 검색 정확도나 속도 개선율이 아님 |
| 코드/기록 | ProjectDiscoveryService, ProjectTypeDetector, WorkspaceScanPolicy, ProjectFileScanner; Decision 0009/0010 |

포트폴리오에서 가치 있는 이유: 모델을 바꾸기 전에 **수집 대상과 경계의 오류**를 잡아 corpus 품질을 개선한 인과가 명확하다. 실제 파일 수의 시점 차이는 11절처럼 표시한다.

### B. 검색 누락을 Threshold와 Query Instruction으로 분리 평가 — 우선 추천

| 항목 | 근거 기반 내용 |
|---|---|
| 문제 | Threshold .50에서 관련 구현이 누락되고 민감 파일 제외/Workspace 경계 질의가 0건 |
| 원인 | 관련 질의의 score가 기준 아래에 있음. 다만 score 하나만으로 의미 품질을 판단할 수 없음 |
| 분석 과정 | 같은 10질문/동일 corpus/Top-K 5에서 .50 vs .45를 비교한 뒤, .45를 고정하고 raw vs 한 가지 instruction만 비교 |
| 해결 | .45 baseline 채택, instruction 기본 ON + Raw override 유지 |
| 검증 | Query별 result count, Top-1/rank, 관련 회수, Kafka 음성 사례 확인. 서비스 테스트로 설정/override 검증 |
| 결과 | 재인덱싱 질의 0→2 등 회수 개선; instruction 후 민감/경계 0→5. 하지만 Chunk 구현 rank 1→3, 중첩 구현은 Top-5 밖으로 밀린 사례도 있음 |
| 코드/기록 | ProjectSearchProperties, ProjectSemanticSearchService, Repository, Decision 0014~0017 |

좋은 표현의 범위: “변수를 분리해 baseline을 보정했고 퇴행도 기록했다.” 피할 표현: “검색 정확도 X% 향상”, “모든 질문에서 의미 검색 해결”. Workspace 경계 결과 복구에는 보안 설명 문서도 포함되어 실제 구현 코드의 우선순위까지 전부 해결한 것은 아니다.

### C. 인덱스 미존재·범용 질문이 모두 답변 실패로 이어지던 UX — 선택 추천

| 항목 | 근거 기반 내용 |
|---|---|
| 문제 | 사용자는 프로젝트를 선택하면 전반적인 설명을 기대하지만 pure RAG는 인덱스/관련 청크 없음을 답변 불가로 처리 |
| 원인 | 프로젝트 개요·진행 상태·런타임 조회와 특정 문서 검색을 같은 경로로 취급한 기능 진입 구조 |
| 분석 과정 | 화면의 CONTEXT_FAILED/NO_EVIDENCE와 실제 도구·Metadata·현재 파일 가용성을 구분 |
| 해결 | 메인 통합 채팅에서 기존 Tool 선택, 실제 허용 파일 Brief, Git/진행/최근 작업 근거를 조합. 기존 RAG 상세 경로는 유지 |
| 검증 | UnifiedChatTest, ProjectMetadataAndBriefTest, ProjectKnowledgeAgentToolsTest, 실제 통합 질의 기록 |
| 결과 | “벡터 검색 0건=모든 질문 종료” 결합을 완화. 모든 broad question의 답변 품질을 보장한 것은 아님 |
| 코드/기록 | UnifiedChatController, UnifiedToolRound, ProjectBriefService, ProjectKnowledgeAgentTools, Decision 0034/0035 |

이 사례는 RAG만 강조하는 포트폴리오에서는 작은 보조 사례로 충분하다. 구조를 Hybrid Search로 잘못 이름 붙이지 않는다.

### D. 중간에 잘린 응답을 SUCCESS로 표시하던 문제 — 우선 추천

| 항목 | 근거 기반 내용 |
|---|---|
| 문제 | 일부 Unity 답변이 중간에 끝나도 생성 성공으로 표시 |
| 원인 | 최종 `.content()`만 사용해 종료 Metadata를 잃고 HTTP 200을 성공으로 집계 |
| 분석 과정 | ChatResponse finish reason과 Ollama HTTP done/stop 신호를 대조 |
| 해결 | 계획 단계/일반 답변/최종 통합 답변에서 완료 확인. length/unknown/누락/done=false는 부분 본문을 숨기고 기존 LLM_FAILED로 안내 |
| 검증 | OllamaCallProfilerTest, UnifiedToolRoundTest, UnifiedChatTest의 정상/미완료/충돌 신호; 실제 Unity 질문 1회 |
| 결과 | 실제 Unity 소개가 11.034초 후 finish unknown일 때 실패로 분리됨. **생성 중단 자체를 해결한 것이 아니라 거짓 성공 표시를 해결** |
| 코드/Git | ChatService, IncompleteResponseException, OllamaCallProfiler, AgentChatService; `8d9f259`, Decision 0036 |

면접에서는 transport 성공(HTTP), 생성 완료(done/finish), 내용 정확성(semantic quality)의 세 계층을 구분해서 설명할 수 있다.

### E. 다른 프로젝트의 클래스명을 구현 사실처럼 설명하던 문제 — D와 묶어서 추천

| 항목 | 근거 기반 내용 |
|---|---|
| 문제 | Unity 질문에서 실제 근거 없는 `ReservationLockService`를 구현된 것처럼 설명 |
| 원인 | Source ID 존재 검사는 주장 속 심볼 존재를 검증하지 못함. Unicode 단어 경계는 `ReservationLockService는/가`처럼 조사 결합을 놓침 |
| 분석 과정 | 질문·답변의 식별자와 Source path/content를 대조, 한국어 조사 fixture 추가 |
| 해결 | ASCII 식별자 경계와 정확한 token 비교로 복합 PascalCase/지원 파일명 확인. 근거 없으면 INSUFFICIENT_EVIDENCE |
| 검증 | 조사/파일명/답변에서 생성된 심볼 회귀 테스트, Unity 부재 클래스·Spring 실제 클래스 질문을 각각 1회 실행 |
| 결과 | Unity 부재 심볼 설명 차단; Spring 실제 클래스 답변은 허용. Spring 답변의 락 경합/Redis 장애 fallback 의미 혼동까지 해결되지는 않음 |
| 코드/Git | AgentChatService, UnifiedChatTest, ProjectKnowledgeAgentTools; `a5b0788`, Decision 0036 |

이것을 완전한 hallucination detector나 의미 검증이라고 표현하면 안 된다. 보수적인 어휘 방어이며 정상 질문을 과도하게 보류하는 오탐 가능성도 남는다.

### F. AI 오류 추측을 검증된 해결 이력과 구분 — Backend 직무 보조 사례

| 항목 | 근거 기반 내용 |
|---|---|
| 문제 | 모델이 제안한 원인을 검증된 사실로 저장·재사용하면 다음 답변의 잘못된 근거가 될 수 있음 |
| 원인 | 생성 결과/사용자 확인/해결 완료를 같은 데이터 상태로 취급하는 위험. 실제 운영 피해를 계량한 사례는 아님 |
| 분석 과정 | 분석 시 실제 ToolEvidence와 모델 문장을 분리하고 이력의 신뢰 수준·변경 책임 정의 |
| 해결 | 서버 보관 draft만 명시적 저장, UNVERIFIED 초기화, 사용자 확인 상태 전이, 낙관적 잠금, 감사 이력, 유사 오류에서 미검증 원인 차단 |
| 검증 | ErrorAnalysisDraftTest, ErrorAnalysisEvidenceTest, ErrorHistoryIntegrationTest, ErrorSimilarityIntegrationTest |
| 결과 | 정책·트랜잭션·경쟁 업데이트 회귀 검증. 실제 진단 정확도/재발률 감소 수치는 미측정 |
| 코드/기록 | ErrorAnalysisService, ErrorHistoryService, ErrorSimilarityService, V3~V5; Decision 0026~0028 |

### G. 청크 재저장 중복과 stale 데이터 관리 — 면접용 보조 사례

내용 fingerprint·안정적 ID·Project 단위 upsert/stale 삭제·트랜잭션을 구현하고 Repository 통합 테스트로 확인했다. 이는 의미 있는 데이터 정합성 설계다. 다만 실제 운영 장애에서 중복 건수가 얼마나 줄었는지 기록은 없으므로 “장애 해결 성과”보다 **정합성 설계·검증 사례**로 쓰는 것이 정확하다. 증분 Embedding을 구현했다고 확대하지 않는다.

## 14. AI 활용 범위와 개발자 기여

### 확인 가능한 객관적 근거

현재 Git HEAD까지 45개 커밋이며 author 집계는 `PJS-1012`다. 이것은 작성 계정 기록이지 각 코드 줄을 사람이 직접 입력했다는 증거가 아니다. 저장소만으로 AI 기여율·개발자 직접 작성 비율을 수치화할 수 없다.

이 대화에는 사용자가 Java 17 유지, 단계별 범위, Project 경계, baseline 값, 평가 기준, 승인·push 정책, 개발 종료 범위를 명시한 기록이 있다. 이는 요구사항 정의·검토·승인 참여의 근거다. 반면 모든 상세 구현을 개발자가 직접 설계·코딩·디버깅했다는 결론은 나오지 않는다. AI는 구현·테스트 실행·분석·문서화 작업을 수행한 이력이 있다.

### 사용자가 수정할 수 있는 구분표

| 작업 | 확인 수준 | 포트폴리오 작성 전에 직접 채울 내용 |
|---|---|---|
| 요구사항·안전 범위 정의 | 사용자 대화에서 확인 | 본인이 가장 중요하게 정한 제약 2~3개 |
| 단계별 승인·baseline 결정 | 사용자 대화 및 Decision Log | 제안을 그대로 수용한 부분과 직접 바꾼 부분 |
| Java 17/로컬 운영 선택 | 명시적 사용자 요구 확인 | 본인 환경/학습 목표와 선택 이유 |
| RAG 세부 클래스·SQL 설계 | 코드/결정 기록은 확인, 사람/AI별 최초 작성 분담은 확인 불가 | 본인이 설명·수정 가능한 설계와 직접 변경한 근거 |
| 구현 초안·반복 코드 | 대화상 AI 구현 지원 확인 | 대표 파일/커밋과 검토 방식 |
| 테스트 제안·작성·실행 | 코드·실행 기록 및 AI 작업 이력 확인 | 본인이 직접 수행한 테스트와 AI에게 실행시킨 테스트 구분 |
| 오류 분석·리팩터링·문서화 | AI 보조 이력 확인 | 본인이 재현/확인한 문제와 최종 판단 근거 |
| UI 실제 사용·문제 제기 | 사용자 스크린샷·응답 품질 피드백 확인 | 직접 발견한 증상과 기대 동작 |
| 검색 결과 수작업 검증 | 평가 기록 존재, 모든 판정자의 신원/분담은 확인 불가 | 직접 읽고 판단한 Query·Source 예시 |
| AI 생성 코드 리뷰·수정 | 승인만으로 모든 코드 리뷰 수행을 증명할 수 없음 | 직접 수정한 diff 또는 설명 가능한 수정 사례 |
| 전체 실행/배포 검증 | 산출물·테스트 로그 존재, 수행 주체 혼합 | 직접 실행한 절차/환경과 AI 자동 수행 구분 |

기여를 숨길 필요는 없지만 “AI는 반복 코드만 도왔다”거나 “설계·디버깅·테스트는 전부 직접 수행했다”는 문구는 현재 기록만으로 확정할 수 없다. 다음 체크리스트를 채운 뒤 최종 문구를 작성하는 것이 적절하다.

- [ ] 내가 직접 정의한 요구사항: ______
- [ ] AI 제안을 검토하고 바꾼 설계: ______
- [ ] 내가 직접 재현·분석한 오류: ______
- [ ] 내가 직접 변경한 파일/커밋: ______
- [ ] 내가 직접 읽고 검증한 Query와 Source: ______
- [ ] AI가 주로 수행한 구현·실행·문서 작업: ______
- [ ] 면접에서 코드 없이도 설명할 수 있는 범위: ______

## 15. 현재 한계

아래는 남아 있는 한계의 기록이다. 기능 개발 재개나 새 튜닝을 제안하는 작업 목록이 아니다.

| 영역 | 현재 한계와 근거 |
|---|---|
| Retrieval | 작은 corpus에 맞춘 .45/Top-K 5. broad question, 구현 vs 문서/테스트 rank, 외부 패키지 noise 문제 잔존 |
| 후처리 필터 | SQL Top-K 이후 Source 정책으로 제거한 결과를 추가 검색해 보충하지 않으므로 최종 결과가 적어질 수 있음 |
| 평가 오염 | Decision Log/평가 문서가 corpus에 들어가면 음성 질문에도 관련 단어가 검색될 수 있음 |
| Chunking | 문자·경계 휴리스틱. AST/정확한 token budget/의미 단위 보장 없음 |
| Context | formatted context 문자 예산과 모델 token window는 다른 단위. 최신 통합 경로와 legacy RAG 제한도 다름 |
| 인덱싱 비용 | Project 전체 재읽기·재임베딩. 변경 파일만 처리하는 증분 pipeline 아님 |
| 인덱스 정합성 | 앞선 read/chunk 일부 실패 후 stale 삭제 위험, splitter 변경 버전 미포함, model filter 부재. 이번에 재현한 장애는 아님 |
| 파일 안전성 | 이름/경로 패턴과 외부 경로 방어 중심. 내부 Symlink 정책·TOCTOU·본문 비밀값까지 완전 보장하지 않음 |
| 확장성 | 순차 처리, 전체 파일/문서/청크 목록 메모리 유지, 제외 디렉터리도 집계 순회. 대규모 부하 시험 없음 |
| Vector Search | ANN 없이 exact cosine. 대규모 corpus 지연·동시 사용자 수 검증 없음 |
| 파일 형식 | UTF-8 텍스트 위주. PDF/DOCX/OCR·전문 언어 파서 미구현 |
| 모델 출력 | 정상 종료해도 의미 오류 가능. Unity finish unknown 미해결, 일반 HashMap 설명에서도 오류 관찰 |
| Grounding | Citation ID/어휘 검사와 의미적 entailment는 다름. 자연어 주장/가짜 관계는 완전히 차단하지 못함 |
| Guard 적용 범위 | 최근 완료·symbol guard는 통합 채팅 중심. plain Chat/RAG/Agent 모든 API에 같은 보장 없음 |
| Source 최신성 | 인덱스 당시 줄과 UI에서 다시 읽는 현재 파일이 달라질 수 있음 |
| 상태 분석 | Git/문서/로그 기반 추론. 테스트 실제 실행/정확한 작업 완료율 판정 아님 |
| 자동화 | 프로세스 내 중복 방지이며 분산 실행 lock 아님. 후보 저장/목록이지 외부 알림 전달 아님 |
| Workspace | 기본 Path 하나. 동적 Workspace CRUD, DB Project identity, 다중 사용자 권한 없음 |
| 운영 보안 | 로컬 사용 전제. 개발 DB 기본값·standalone bind·인증 부재를 고려해야 함 |
| 평가 | 소규모 수작업·당시 corpus. 독립 test set, 대조군, MRR/NDCG, 성능 분포/부하 통계 부족 |

현재 미구현임을 명확히 할 항목: GraphRAG, BM25/Hybrid Search, Reranker, HNSW/IVFFlat, 모델 학습/Fine-tuning, Conversation Memory, Streaming, 자동 코드 수정 Agent. 경로 기반 Source 선택 정책은 있지만 학습 기반 weighting/reranker를 구현한 것은 아니다.

### 포트폴리오에서 금지할 과장과 대체 가능한 사실

| 피할 표현 | 현재 증거로 말할 수 있는 사실 |
|---|---|
| “모든 파일을 안전하게 완벽 분석” | 지정 Workspace의 지원 UTF-8 파일에 경로/민감/크기 정책 적용 |
| “증분 인덱싱으로 변경분만 Embedding” | 안정적 ID와 트랜잭션 기반 upsert/stale 삭제; 전체 재임베딩 |
| “환각 해결/정확한 답변 보장” | no-evidence/완료 여부/일부 unsupported symbol 방어, 의미 오류 잔존 |
| “검색 정확도 크게 향상” | 동일 10질문에서 threshold와 instruction을 분리 비교하고 회수·rank 변화 기록 |
| “응답 속도 N% 개선” | 서로 다른 시점 측정값은 있으나 통제된 반복 실험에 의한 일반 개선율 없음 |
| “완전 오프라인 단독 exe” | 로컬 실행 구조지만 모델·Java·Docker·이미지 등의 선행 준비 필요 |
| “직접 구현한 AI 모델” | Spring AI/Ollama로 기존 모델을 연동하고 데이터·검색·근거 흐름을 구현 |
| “Read-only 앱이므로 데이터 변경 없음” | Agent 관찰 도구는 읽기 전용, 앱은 인덱스/오류/자동화 저장 및 서비스 시작 수행 |

## 16. 3~4페이지 포트폴리오 내용 선별

### 권장: 4페이지, Backend 흐름과 검증을 중심으로

| 페이지 | 포함할 내용 | 근거 자료 | 제외/축소할 내용 |
|---|---|---|---|
| 1P — 문제·경계·구조 | 어떤 개발 정보를 연결하는가, Project 범위, 3개 대표 시나리오, 시스템 구조, 계층별 스택 | 1절·10절, 실제 앱 화면 1개 | 10개 화면 전부 소개, 모든 라이브러리 나열 |
| 2P — 데이터와 RAG | Scan→Read→Chunk→Embedding→pgvector→검색→Context→Source. DB 핵심행·Project SQL·안전 정책 | 2~6절·9절, 클래스/Metadata 몇 개 | 모든 API/DTO·오류 이력 테이블 전체 |
| 3P — 문제 해결 | A(중첩 Unity/정책), B(변수 분리 Retrieval 평가)를 주 사례로 선택. D/E를 짧은 안전성 보완 사례로 묶기 | 13절, 당시 수치·검증 테스트 | 성공 사례만 나열, 퇴행/한계 숨기기 |
| 4P — 검증·기여·한계 | 테스트 계층과 실제 수, 종료 guard 결과, AI/본인 역할 구분, 남은 한계 3~4개 | 11·14·15절 | 미측정 정확도/커버리지, 장황한 향후 기능 roadmap |

3페이지가 필요하면 4P의 핵심을 3P 하단으로 합치고 사례는 A/B 두 개만 상세히 쓴다. 오류 이력·검증 감사는 Spring Backend 역량을 특별히 강조할 때 B 대신 보조 사례 F로 대체할 수 있다.

System Architecture에는 다음 관계만 우선 표시한다: React/Tauri → Spring Boot → 파일시스템·Git/로컬 도구 / Ollama / PostgreSQL+pgvector. “도구 관찰”과 “데이터 저장” 화살표를 구분하면 읽기 전용 Agent의 범위도 오해 없이 설명된다.

### 우선순위

| 용도 | 선별 |
|---|---|
| 본문 필수 | 안전 수집 경계, 실제 인덱싱·검색 흐름, Project 필터·차원·cosine, Source 추적, 비교 평가 사례 |
| 본문 선택 | 오류 상태 전이/감사/낙관적 잠금, 통합 채팅 fallback, Desktop 준비 단계 |
| 부록·면접용 | 전체 API 표, 8-table 상세, scan 예외 경로, Source DTO 차이, timeout 세부, source failure와 stale 삭제 위험 |
| 현재 넣지 않기 | 독립 검증 없는 정확도/속도 개선율, 구현하지 않은 검색 기술, 증명할 수 없는 개인 코드 기여율 |

포트폴리오의 강점은 기술 종류의 개수가 아니라 **입력 경계→처리→저장→근거→실패 상태를 끝까지 연결하고, 검증으로 주장 범위를 제한한 점**이다. 이는 편집상 추천이며 채용 성과를 보장하는 평가는 아니다.

## 17. 면접 대비

아래는 예상 질문과 확인할 코드·필수 개념이다. 완성 답변 대본이 아니라 본인이 설명할 수 있는지 점검할 목록이다.

| 질문 | 확인할 코드/기록 | 반드시 알아야 할 개념 |
|---|---|---|
| 일반 LLM 호출과 RAG는 무엇이 다른가? | ChatController/ChatService, RagChatService | 외부 근거 회수·Context 구성과 모델 자체 지식의 구분, 학습과 추론 |
| 메인 채팅은 항상 벡터 검색을 하는가? | UnifiedChatController, UnifiedToolRound, ProjectKnowledgeAgentTools | Tool 선택, 실제 파일 fallback, pure RAG/일반 지식 경로 차이 |
| 프로젝트 범위는 어디서 강제하는가? | ProjectId, ProjectDiscoveryService, SearchRepository, ErrorProjectScope | 파일시스템 경계와 SQL tenant-like 범위의 차이, DTO 검증만으로 부족한 이유 |
| 중첩 Unity Project 문제를 어떻게 찾았나? | ProjectTypeDetector, DiscoveryService, Decision 0009 | Root/Container, marker, depth 제한, 타입별 제외 정책 |
| 파일 접근을 안전하게 만들었다는 범위는? | ProjectDocumentReader, WorkspaceScanPolicy, ReaderTest | normalize/realPath, traversal, Symlink, TOCTOU, deny pattern 한계 |
| Document와 Chunk의 차이는? | WorkspaceDocument, DocumentChunk, DocumentChunkingService | 원본 단위와 검색 단위, offset/line, Metadata 추적 |
| 2,000/2,400자와 overlap 200을 왜 썼나? | ChunkingProperties, CharacterChunkSplitter, Decision 0011 | char vs token, 경계·문맥 보존과 중복 비용, baseline의 한계 |
| 이 청크 분할은 코드 의미를 이해하는가? | SourceCodeDocumentChunker, CharacterChunkSplitter | 구조 힌트와 AST의 차이, 함수가 잘릴 수 있는 조건 |
| Embedding은 무엇이고 1024차원은 어디서 오는가? | EmbeddingService, application.yml, V2 | 의미를 수치 공간으로 표현, 모델 출력 차원, SQL/애플리케이션 차원 일치 |
| 문서와 질의에 같은 모델을 쓰는 이유는? | ProjectChunkEmbeddingService, ProjectSemanticSearchService | 같은 벡터 공간, 모델 버전 변경·재인덱싱, 차원 같아도 공간이 다른 문제 |
| pgvector를 어떻게 사용했나? | ProjectIndexRepository, SearchRepository, V1/V2 | PostgreSQL 확장/타입/연산자, JDBC와 VectorStore 추상화의 차이 |
| cosine similarity를 어떻게 계산하는가? | SearchRepository.SEARCH_SQL | 내적과 벡터 norm, 거리=1−similarity, 값이 확률이 아닌 이유 |
| Top-K와 threshold는 무엇이 다른가? | SearchProperties/Service/Repository | 개수 상한 vs 최소 유사도, 5보다 적거나 0개가 될 수 있음 |
| .45를 선택한 근거는? | Decision 0014/0015 | 동일 corpus/질문으로 변수 통제, precision/recall trade-off, 일반화 한계 |
| Query Instruction이 문서에도 붙는가? | QueryInstruction.apply, EmbeddingService, Decision 0016 | 질의 입력만 바꾼 실험, score 상승과 rank/정확도 구분 |
| HNSW 없이 성능이 괜찮은 이유는? | SearchRepository, V2, Decision 0014 | 당시 corpus 크기, exact vs approximate, 실행계획·미측정 대규모 한계 |
| 재인덱싱은 정말 증분인가? | DocumentChunkingService, IndexService/Repository | fingerprint, ID 안정성, DB idempotency와 재임베딩 비용의 차이 |
| 저장 도중 실패하면 어떻게 되는가? | IndexRepository @Transactional, IntegrationTest | upsert+delete 원자성, rollback, 앞 단계 부분 읽기 실패와 별개의 문제 |
| Context 8,000자는 어떻게 계산하는가? | RagContextAssemblyService/Properties | 헤더/구분자 포함, 청크 통째 제외, 전체 Prompt/token window와 차이 |
| Prompt Injection에 어떻게 대응하는가? | RagChatService, ChatService, UnifiedChatPolicy | Source를 신뢰하지 않는 지침, tool allowlist, prompt만으로 완전 방어 불가 |
| Citation ID가 유효하면 답변이 맞는가? | RagCitationValidator, AgentChatService, Decision 0020/0036 | ID validity, Source relevance, claim entailment의 차이 |
| Source 파일이 수정되면 어떻게 되는가? | RagPage/ragApi, ProjectDocumentReader | 인덱스 스냅샷과 현재 파일, 줄 번호 drift |
| 응답이 잘렸는지 어떻게 아는가? | ChatService.requireComplete, OllamaCallProfiler | HTTP 200 vs done/finish, length/unknown, 정상 stop의 의미적 한계 |
| 한국어 조사 때문에 왜 symbol 검사가 실패했나? | AgentChatService, UnifiedChatTest | Unicode word boundary와 ASCII identifier boundary, 정확 token·오탐/미탐 |
| Agent가 임의 명령을 실행할 수 있는가? | GitProcessRunner, DockerProcessRunner, AgentToolExecution | 허용 명령·인자, timeout·출력 상한, 읽기 전용 도구와 startup의 차이 |
| 오류 분석을 곧바로 검증된 이력으로 저장하는가? | ErrorAnalysisService, ErrorHistoryService, V3/V4 | 서버 snapshot, 상태 전이, 사용자 확인, 낙관적 잠금·감사 |
| 유사 오류 검색과 코드 검색은 같은 설정인가? | ErrorSimilarityService/Properties, V5 | 같은 cosine라도 corpus/목적·threshold .65 vs .45가 다름 |
| 자동화가 LLM을 계속 호출하지 않는가? | AutomationExecutionService, ProjectStateFingerprintService | 변경 지문, no-change skip, 프로세스 내 중복 방지와 분산 lock 차이 |
| 왜 답변이 느리고 어디서 시간을 재나? | UnifiedRequestTrace, OllamaCallProfiler | 도구/검색/LLM 시간, token 생성, warm/cold, 겹치는 timing을 단순 합산하면 안 됨 |
| 로컬 앱은 인터넷 없이 실행되는가? | application.yml, compose.yml, startup.rs, lib.rs | 로컬 추론과 초기 의존성 설치, URL 설정, loopback·인증 전제 |
| 테스트 215개가 품질을 보장하는가? | Test source/XML, Decision 0036 | unit/integration/live quality 차이, mock 한계, parameterized count, skipped case |
| 본인이 직접 한 일과 AI가 한 일은? | Git diff, Decision Log, 사용자 본인 기록 | 요구사항·판단·구현·검증 분담을 구체적 사례로 구분, 모르는 부분 인정 |

### 최종 작성 전 확인 사항

1. 이 문서의 “코드 확인”과 “기록 확인”을 섞지 않는다.
2. 표의 측정값에는 당시 단계·corpus·질문 수를 함께 쓴다.
3. AI 기여 표의 빈칸은 본인이 실제 수행한 증거로만 채운다.
4. 문제 해결 A/B/D/E 중 설명 가능한 2~3개만 본문에 넣는다.
5. 한계는 축소하지 않되 모든 내부 위험을 4쪽 본문에 나열하지 않고 면접 자료로 구분한다.

---

## 근거 파일 바로가기

아래 링크는 조사 당시 로컬 저장소 절대 경로다. 저장소를 옮겨 공개할 때에는 링크를 해당 Git commit의 GitHub 경로로 바꾸면 된다. 원본 소스·평가 JSON을 외부에 옮길 때 다른 프로젝트 코드/로그가 섞이지 않도록 별도 확인해야 한다.

[build]: C:/workspace/Local_Ai_Work/build.gradle
[config]: C:/workspace/Local_Ai_Work/src/main/resources/application.yml
[package]: C:/workspace/Local_Ai_Work/frontend/package.json
[cargo]: C:/workspace/Local_Ai_Work/frontend/src-tauri/Cargo.toml
[java]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace
[discovery]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/discovery/ProjectDiscoveryService.java
[detector]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/discovery/ProjectTypeDetector.java
[projectid]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/discovery/ProjectId.java
[scanner]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/scan/ProjectFileScanner.java
[scanpolicy]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/scan/WorkspaceScanPolicy.java
[reader]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/document/ProjectDocumentReader.java
[batch]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/document/ProjectDocumentBatchReader.java
[projectchunk]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/chunk/ProjectChunkingService.java
[chunk]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/chunk/DocumentChunkingService.java
[splitter]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/chunk/CharacterChunkSplitter.java
[embedbatch]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/embedding/ProjectChunkEmbeddingService.java
[embed]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/embedding/EmbeddingService.java
[indexservice]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/index/ProjectIndexService.java
[indexrepo]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/index/ProjectIndexRepository.java
[searchservice]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/search/ProjectSemanticSearchService.java
[searchrepo]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/search/ProjectSemanticSearchRepository.java
[searchprops]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/search/ProjectSearchProperties.java
[context]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/rag/RagContextAssemblyService.java
[ragchat]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/rag/RagChatService.java
[citation]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/rag/RagCitationValidator.java
[chat]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/chat/ChatService.java
[unifiedcontroller]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/agent/UnifiedChatController.java
[agentservice]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/agent/AgentChatService.java
[toolround]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/chat/UnifiedToolRound.java
[knowledge]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/agent/ProjectKnowledgeAgentTools.java
[brief]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/overview/ProjectBriefService.java
[evidencepolicy]: C:/workspace/Local_Ai_Work/src/main/java/com/localai/workspace/overview/ProjectEvidencePolicy.java
[app]: C:/workspace/Local_Ai_Work/frontend/src/App.tsx
[ragpage]: C:/workspace/Local_Ai_Work/frontend/src/pages/RagPage.tsx
[migrations]: C:/workspace/Local_Ai_Work/src/main/resources/db/migration
[compose]: C:/workspace/Local_Ai_Work/compose.yml
[startup]: C:/workspace/Local_Ai_Work/frontend/src-tauri/src/startup.rs
[tests]: C:/workspace/Local_Ai_Work/src/test/java/com/localai/workspace
[d9]: C:/workspace/Local_Ai_Work/docs/decisions/0009-depth-one-project-root-discovery.md
[d10]: C:/workspace/Local_Ai_Work/docs/decisions/0010-phase-5-workspace-ingestion-foundation.md
[d14]: C:/workspace/Local_Ai_Work/docs/decisions/0014-project-cosine-similarity-search.md
[d15]: C:/workspace/Local_Ai_Work/docs/decisions/0015-similarity-threshold-045.md
[d16]: C:/workspace/Local_Ai_Work/docs/decisions/0016-query-instruction-evaluation.md
[d20]: C:/workspace/Local_Ai_Work/docs/decisions/0020-phase-6-rag-quality-baseline.md
[d36]: C:/workspace/Local_Ai_Work/docs/decisions/0036-final-quality-guards-and-release.md
