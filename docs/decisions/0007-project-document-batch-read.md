# 0007. 프로젝트 문서 순차 읽기

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

## 상태

승인됨. Phase 5 Step 7 당시 기록이다.

## 문제

한 프로젝트의 허용 텍스트 파일을 모두 읽고 성공·실패·제외 결과를 유지해야 한다.
파일 하나의 오류가 다음 읽기를 막지 않아야 하며, 향후 작업공간 경로의 공급원이 DB로 바뀌어도
파일시스템 로직을 교체하지 않아야 한다.

## 결정

`ProjectFileScanner`가 기존 요약과 파일별 `FileScanEntry`를 담은 `ProjectScanPlan`을 만든다.
`SUPPORTED`만 기존 `ProjectDocumentReader`에 전달하고 나머지는 본문을 열지 않고 읽기 결과로 변환한다.

`ProjectDocumentReadResult`에는 프로젝트 집계, 성공 문서, 파일별 경로·상태·이유, 총 UTF-8 바이트와
처리 시간을 둔다. 본문은 `documents`에만 저장하고 `fileResults`에 중복하지 않는다.

파일은 순차 처리한다. 시간은 탐색 시작부터 읽기 완료까지 측정한다. 병렬 스트림·Virtual Thread,
청크·임베딩·저장은 추가하지 않는다.

탐지·탐색·단일/전체 읽기 서비스에 Workspace `Path`를 받는 메서드를 제공한다.
기존 메서드는 `WorkspaceProperties`의 기본 경로로 위임한다. 향후 `WorkspaceService`가 DB 경로를
전달할 수 있지만 이번 단계에서 DB 기반 작업공간 관리는 구현하지 않는다.

## 결과

Include/Exclude 판단은 기존 탐색 정책을 재사용하고 실제 읽기 직전 다시 확인한다.
제외 파일 메타데이터는 남기며 민감 파일로 분류한 파일의 본문은 읽지 않는다.

당시 `Local_Ai_Work` 기준: 탐지 416개, 문서 57개, 텍스트 90,026 bytes,
제외 359개, 실패 0개, 개발 PC 처리 시간 69 ms.
