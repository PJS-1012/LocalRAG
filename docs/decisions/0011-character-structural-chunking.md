# 0011. 문자 수와 구조 경계를 이용한 청크 분할

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태와 배경

승인됨. Phase 6의 청크 baseline 기록이다.
안전한 `WorkspaceDocument`를 검색 단위로 바꾸되 당시 임베딩·벡터 저장·RAG는 추가하지 않았다.
현재 의존성 집합에 Qwen3 전용의 정확한 토큰 계수기가 없어, 품질 측정 전에 모델별 tokenizer
의존성과 결합을 늘리지 않기로 했다.

## 결정

- 별도 `DocumentChunker` 추상화 뒤에서 문자 수를 사용한다.
- 일반 텍스트/Markdown은 최대 2,000자, 코드는 최대 2,400자, 중첩은 200자다.
- 일반 텍스트는 Markdown 제목·문단·줄·공백 경계를, 코드는 완료된 블록·빈 줄·줄·공백을 우선한다.
- 언어별 AST 파서는 추가하지 않는다.
- 알려진 코드 확장자는 `SourceCodeDocumentChunker`, 나머지 안전한 텍스트는 `TextDocumentChunker`로 처리한다.
- 빈 문서는 가짜 청크를 만들지 않고 `EMPTY`로 처리한다.
- 분할 실패는 해당 문서에 격리하고 나머지 프로젝트를 계속 순차 처리한다.

## 청크 메타데이터

`DocumentChunk`는 결정적 `chunkId`, `projectId`, 상대 소스 경로/이름, 확장자,
0부터 시작하는 순번, 내용, 문자 시작/끝 offset(끝 제외), 1부터 시작하는 줄 번호,
원문 fingerprint와 수정 시간을 유지한다.

경로는 **Project 기준 상대 경로**다. 원래 문서의 Workspace-relative 표현을 실제 생성 코드에 맞게 정정했다.
경로·줄·offset은 출처/순서를 추적하고 fingerprint는 정확한 원문 버전과 청크 집합을 연결한다.

## 결정적 ID

다음 값을 줄바꿈으로 결합한 문자열의 SHA-256이 `chunkId`다.

```text
projectId
relative source path
source content SHA-256
chunk index
```

같은 소스 버전은 같은 ID, 변경된 소스는 새 ID 집합을 만든다.
후속 저장에서 프로젝트/소스 범위의 오래된 ID를 안전하게 제거해야 한다.

## 당시 Local_Ai_Work 측정

| 항목 | 값 |
|---|---:|
| 원문 / 성공 / 실패 문서 | 90 / 90 / 0 |
| 청크 | 131 |
| 평균 / 최소 / 최대 길이 | 1,318.95 / 30 / 2,396자 |
| 기존 읽기 포함 전체 처리 | 114 ms |

`README.md`, Java, `src/main/resources/application.yml`, `gradle/wrapper/gradle-wrapper.properties`를 확인했다.
짧은 파일은 한 청크, 긴 Markdown/Java/YAML은 여러 청크였다. 작업 트리·새 소스/테스트·캐시 때문에
수와 시간은 달라질 수 있다.

## 한계

Java 문자열 문자 수는 Qwen3 토큰 수와 다르며 언어마다 비율이 달라진다.
추상화 덕분에 향후 토큰 기반 구현으로 바꿀 여지는 있지만 현재 구현은 휴리스틱이다.
목표 길이 근처의 경계를 우선할 뿐 클래스/메서드를 완전히 보존하지 않는다.
