# 결정 0036: 품질 개발 종료 — 응답 완료 및 근거 검사

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

일자: 2026-09-28. 범위: 두 방어 수정, 대표 질문 5개, 전체 회귀 테스트, 배포본 일치.
새 기능/검색 기술/모델/스트리밍/추가 품질 튜닝 없음. 0035의 남은 미검증 변경도 이번 전체 테스트에 포함했다.

## 잘린 응답

기존 Unified Chat은 최종 `.content()`만 반환하여 종료 메타데이터를 버렸고,
HTTP 200을 생성 성공으로 집계했다. 이제 ChatResponse의 finish reason과 실제 Ollama HTTP
done/stop 정보를 확인한다. length, unknown, 누락된 완료 상태 및 done=false는 성공이 아니다.
완료를 확인할 수 없으면 부분 본문을 표시하지 않고 기존 LLM_FAILED 상태로
“응답이 중간에 종료되었거나 정상 종료를 확인할 수 없습니다. 다시 시도해주세요.”를 반환한다.
Tool 계획 단계와 일반 답변에도 적용한다. 자동 재시도는 추가하지 않았다.
정상 stop은 문장의 의미적 완전성을 보증하지 않으며, 문장부호만으로 실패를 추정하지 않는다.

## 근거 없는 구현 주장

기존 정규식의 Unicode 단어 경계는 ReservationLockService는/가를 놓쳤다.
ASCII 식별자 경계로 변경하고 요청뿐 아니라 최종 답변의 복합 PascalCase 식별자와
지원하는 코드/문서 파일명을 실제 수집 Source path/content에서 정확한 토큰으로 대조한다.
부분 문자열이나 비슷한 클래스명은 증거가 아니다. 근거가 없으면 기존 INSUFFICIENT_EVIDENCE로
“현재 확보된 근거에서는 확인되지 않습니다.”를 반환한다. 인용 ID가 맞아도 이 검사를 통과해야 한다.
LLM 실패 상태는 이 검사로 덮어쓰지 않는다. 일반 지식 및 지식 Tool을 쓰지 않은 상태 조회에는 적용하지 않는다.
이것은 보수적인 어휘 방어이며, 자연어로만 설명한 기능이나 Source 안에 단순 언급된 이름의 실제 구현을
완전히 검증하지는 못한다. 새로운 의미 검증기나 Citation 시스템은 만들지 않았다.

## 대표 질문 (한 번씩, 총 5개)

원본: build/unified-quality/release-20260928/. 다른 프로젝트 원문을 포함하므로 Git에 넣지 않는다.

| 질문 | 상태 / 시간 | 판단 |
|---|---|---|
| Unity 전체 소개 | LLM_FAILED / 11.034초 | provider finish unknown 재현. 부분 답변 노출 차단 PASS; 답변 생성 문제 자체는 남음 |
| Unity ReservationLockService는 무슨 역할 | INSUFFICIENT_EVIDENCE / 6.232초 | 정상 stop으로 생성된 허위 구현 설명을 근거 검사로 차단 PASS |
| Spring ReservationLockService는 무슨 역할 | SUCCESS / 14.683초 | 실제 클래스와 Source 보존 PASS. 락 경합 실패와 Redis 장애 fallback 설명을 혼동하는 의미 오류는 PARTIAL |
| Spring 전체 소개 | SUCCESS / 9.728초 | README 기반 구조와 한계 설명 PASS |
| Java HashMap | SUCCESS / 1.874초 | 정상 종료/일반 경로 PASS. LinkedHashMap으로 동시성을 충족한다는 잘못된 설명은 품질 FAIL |

API 성공을 품질 PASS로 간주하지 않는다. 위 5개 외 모델 재평가/반복은 하지 않았다.

## 테스트

- 백엔드: 215개, 214 통과 / 1개 선택 실행 제외 / 실패 0. 기존 통합 테스트 포함.
- 프런트엔드: 43/43 통과. 실패 안내 표시와 기존 입력 처리 회귀 포함.
- Rust: 10/10 통과.
- 새 회귀: length/unknown/빈 finish 차단, 일반/Tool 후 답변 모두 차단, SDK stop보다 HTTP 실패 우선,
  done=false/완료 누락 HTTP 200 실패 집계, 한국어 조사 및 파일명 검사, 답변에서 만들어낸 심볼 차단.

## 배포

Tauri 배포 실행 파일과 NSIS 설치 파일을 당시 최신 백엔드 JAR 및 프런트엔드 배포 빌드와 함께 재빌드했다.
바탕화면 LocalRAG.lnk는 저장소의 frontend/src-tauri/target/release/localrag-desktop.exe를 가리킨다.
해당 실행파일과 함께 backend/localrag-backend.jar, runtime/compose.yml이 필요하므로 exe만 다른 곳으로 복사하지 않는다.
배포 해시는 build/release-20260928-manifest.json에 기록했다. 빌드 산출물은 기존 Git 제외 정책을 유지한다.

## 종료 판단 / 남은 한계

이번 방어 수정으로 명백히 미완료인 답변과 근거 밖 심볼을 성공 답변으로 내보내는 사례를 차단했다.
Unity 생성 중단 자체, 인용과 주장 사이의 의미 정확성, 일반 모델 지식 오류는 해결하지 않았다.
모델이 정상 stop을 반환한 채 부정확하게 답하는 경우가 있으며, 어휘 검사는 오탐으로 답변을 보류할 수 있다.
종료 정보가 없는 공급자 응답도 보수적으로 실패 처리한다. 이 상태를 기록하고 기능/품질 개발을 종료한다.
추가 튜닝이나 기능 제안은 하지 않는다. 당시에는 로컬 커밋만 허용하고 push하지 않는 조건이었다. 이후 push 승인 및 현재 Git 상태와는 구분한다.
