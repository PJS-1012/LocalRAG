# 0008. 작업공간 메타데이터 순차 탐색

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태

승인됨. Phase 5 Step 8 당시 기록이다. 아래 탐지 문제는 다음 기록 0009에서 다룬다.

## 문제

여러 프로젝트의 메타데이터를 합치되 모든 파일 본문을 읽지 않는다.
한 프로젝트의 탐색 실패가 다른 프로젝트를 중단시키지 않아야 한다.

## 결정

명시적 Workspace `Path`에서 프로젝트를 탐지하고 기존 `ProjectFileScanner`를 순차 호출한다.
프로젝트 경계에서 탐색 예외를 처리해 식별자·탐지 힌트·시간·실패 이유가 있는 결과를 남긴다.

성공 결과에는 타입·프레임워크·Git 여부·힌트·파일 집계·대형 파일 상세·시간을 둔다.
`WorkspaceScanSummary`는 성공 프로젝트의 메타데이터와 성공/실패 프로젝트 수를 따로 집계한다.
`ProjectDocumentReader`는 호출하지 않는다. 5 MB 상한을 유지하고 캐시·병렬 스트림·Virtual Thread는 추가하지 않는다.

## 당시 기준값

`C:/workspace`: 프로젝트 10개, 파일 107,009개, 포함 8,614개, 제외 98,394개,
메타데이터 실패 0개, 대형 파일 1개. 프로젝트 10개 모두 성공했으며 전체 2,472 ms였다.
대형 파일은 `Toy_Sports_Day/toy_sports_day/Library/Bee/1900b0aE.dag.json`, 16,280,177 bytes였다.

## 발견한 탐지 문제

직계 폴더 7개가 UNKNOWN이었다. `dragonball`, `Room_Reservation`, `Toy_Sports_Day`, `ValueSwap`은 Git 저장소였다.
`Room_Reservation`에는 두 Spring Boot 프로젝트 `RoomReservation`, `RoomReservationBackendPractice`와
프런트엔드가 있었고 `Toy_Sports_Day`에는 `toy_sports_day` Unity 프로젝트가 있었다.
상위 폴더가 UNKNOWN이라 Unity `Library` 제외가 적용되지 않아 생성물이 집계를 지배했다.

Step 8에서 타입을 임의 변경하지 않고 Phase 5 종료 전에 한 단계 감싼 폴더/혼합 Container의
표현을 결정하기로 했다. 혼합 폴더에 자식 타입을 그대로 상속하는 방식은 적절하지 않았다.
