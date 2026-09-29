# 0009. 한 단계 중첩 프로젝트 루트 탐지

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태

승인됨. 당시 탐색 비교 수치를 보존한 기록이다.

## 문제

작업공간의 직계 폴더를 모두 프로젝트로 취급하면 `Room_Reservation`, `Toy_Sports_Day`처럼
실제 루트가 한 단계 아래인 폴더를 잘못 분류한다. 타입별 정책도 잘못된 루트에 적용된다.

## 결정

기존 타입 판별기를 직계 폴더에 먼저 적용한다. 알려진 marker가 있으면 루트로 확정하고 더 찾지 않는다.
UNKNOWN일 때만 바로 아래 디렉터리를 한 번 검사하고 명확한 marker가 있는 자식만 채택한다.

자식 프로젝트가 하나 이상 있으면 부모는 `DetectedContainer`, 자식은 독립 `DetectedProject`로 반환한다.
Container는 구조 메타데이터이며 Scan/RAG 대상이 아니다. 자식 marker도 없으면 부모를 UNKNOWN으로 남긴다.
추가 한 단계보다 깊은 탐지는 하지 않는다.

Container를 `ProjectType` enum에 넣지 않는다. 탐색 가능한 기술 타입과 비프로젝트 구조를 섞지 않기 위해서다.
Scanner는 `DetectedProject.rootPath`를 사용하고 직계 폴더라는 가정을 다시 만들지 않는다.

## 공통 정책과 Unity 정책

`.git`, `.gradle`, `.idea`, `.vscode`, `node_modules`, `target`, `build`, `obj`, `bin` 등 공통 제외는
UNKNOWN을 포함한 모든 타입에 적용한다. `Library`는 다른 기술에서 정상 디렉터리일 수 있으므로
Unity 전용으로 남긴다. 올바른 루트 탐지로 `toy_sports_day`에만 Unity 정책을 적용한다.

## 실제 확인

Container 2개, 탐색 가능한 프로젝트 루트 12개를 찾았다.

- `Room_Reservation`: `room-reservation-front` (NODE), `RoomReservation` (SPRING_BOOT), `RoomReservationBackendPractice` (SPRING_BOOT).
- `Toy_Sports_Day`: `toy_sports_day` (UNITY).
- 직계 `Local_Ai_Work` (SPRING_BOOT), `DungeonMerchant` (UNITY)는 그대로 유지하고 내부를 추가 탐지하지 않았다.

| 항목 | 변경 전 | 변경 후 |
|---|---:|---:|
| 프로젝트 | 10 | 12 |
| 전체 파일 | 107,009 | 106,990 |
| 포함 | 8,614 | 680 |
| 제외 | 98,394 | 106,310 |
| 대형 파일 | 1 | 0 |

Unity의 `Library`, `Logs`, `Temp`, `UserSettings`를 제외했다.
`Library/Bee/1900b0aE.dag.json`은 지원 파일 크기 검사 후보에서 빠졌다.

## 남은 UNKNOWN

`awsd`, `dragonball`, `Dungeon_Shop`, `untitled`, `ValueSwap`은 한 단계 확인 뒤에도 UNKNOWN이었다.
Git만으로 프레임워크를 추정하지 않는다.
