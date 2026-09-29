# 0005. 작업공간 프로젝트 탐지와 파일 탐색 경계

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태

승인됨. 초기 탐지 정책 기록이다. 직계 폴더만 탐지하던 제약은
[0009](0009-depth-one-project-root-discovery.md)에서 보정했다.

## 문제

PC 전체를 검색하거나 Git만으로 프레임워크를 추정하지 않고, 서로 다른 종류의 프로젝트를
찾아야 한다. 생성물·민감 파일·바이너리·대형 파일을 수집 대상에서 제외해야 한다.

## 결정

설정 가능한 기본 작업공간을 `C:/workspace`로 두고 당시에는 직계 디렉터리만 프로젝트 후보로 삼았다.

- Unity: `Assets`, `ProjectSettings`, `Packages/manifest.json`을 모두 확인한다.
- Java: Gradle/Maven 빌드 파일과 `src/main/java`를 확인하고 빌드 파일 내용으로 Spring Boot를 구분한다.
- Node.js, .NET, Python: 각 생태계의 manifest/build marker를 사용한다. 세부 기준은 `ProjectTypeDetector`에 있다.
- Git 디렉터리는 메타데이터일 뿐 프레임워크 판별 근거가 아니다.

본문을 읽기 전에 일반 파일의 메타데이터를 분류한다. 공통 디렉터리·경로·바이너리·민감 이름·확장자
정책을 적용하고 Unity 생성 폴더는 Unity 프로젝트에만 제외한다. 초기 크기 상한은 설정 가능한 5 MB다.

상대 제외 경로 `logs/archive`는 프로젝트 루트 기준 그 경로만 일치한다.
다른 위치의 `archive`까지 이름만 보고 제외하지 않는다.

## 결과

당시 탐지는 지정 작업공간 밖으로 나가지 않고 중첩 저장소를 별도 프로젝트로 탐지하지 않았다.
탐색 결과는 지원·제외·대형·메타데이터 실패 수와 대형 파일 상세를 제공한다.
본문 읽기·청크·임베딩·영속화는 이 단계에서 하지 않았다.

실제 Phase 5 확인 결과:

- `Local_Ai_Work`: Java / Spring Boot.
- `DungeonMerchant`: Unity.
- Unity의 `Library`, `Logs`, `UserSettings` 등 생성 폴더 제외 확인.
