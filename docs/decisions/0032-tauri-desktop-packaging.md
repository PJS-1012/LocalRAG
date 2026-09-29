# 결정 0032: Tauri 데스크톱 패키징

> 이 문서는 해당 단계의 결정과 당시 검증 결과를 보존합니다. 현재 구현은 [시스템 구조](../architecture.md), 최종 검증은 [배포 점검표](../release-checklist.md)를 기준으로 확인하세요.

일자: 2026-09-16. 아래 제한 시간과 검증값은 당시 기준이며 원클릭 시작 정책은 0033에서 확장했다.

## 문제와 결정

Phase 11의 브라우저/PowerShell 실행을 Windows 창 하나로 제공하되 RAG·Agent·오류·개발 흐름·자동화 의미는 유지한다. Tauri 2는 기존 React/Vite 화면과 고정된 `localrag-backend.jar`를 묶고 외부 Java 17로 실행한다. jlink 실행 환경은 동봉하지 않는다.

백엔드 시작은 제한된 후보 B 방식이다.

1. `127.0.0.1:18080`의 LocalRAG 상태와 작업공간 탐지를 함께 확인한다.
2. 식별된 LocalRAG만 재사용한다.
3. 빈 포트에서는 고정 주소/포트 인자로 `java -jar <bundled-resource>`만 실행한다.
4. 다른 프로그램이 사용하면 PORT_IN_USE로 알리고 종료시키지 않는다.
5. 자식 PID와 앱 로그를 기록하고 당시 최대 60초 동안 확인한다.

프런트엔드는 STARTING/READY/UNAVAILABLE/PORT_IN_USE를 표시하고 당시 45초 후 재시도를 멈췄다. 배포 API는 루프백 백엔드로 고정된 Rust 연결 계층을 사용한다. `/api/**`, GET/POST/PUT/PATCH, UTF-8 응답, 120초 요청 제한을 허용하고 외부 URL·경로 이탈·역슬래시·DELETE를 거부한다. 응답 10 MB 제한은 현재 구현상 전체 본문 수신 후 검사하므로 네트워크 수신 자체의 메모리 상한으로 표현하지 않는다.

## 보안과 수명주기

Tauri 권한은 `core:default`만 사용하며 shell/filesystem 플러그인이나 권한은 없다. 임의 외부 URL 탐색을 허용하지 않는다. CSP는 번들 자원·Tauri IPC·고정 루프백 백엔드만 허용하고 Spring CORS에 `http://tauri.localhost`를 명시하며 와일드카드를 사용하지 않는다. Agent에는 데스크톱 변경 도구를 추가하지 않는다.

앱은 기존 서비스를 강제 종료하지 않는다. 인증된 정상 종료 계약이 없으므로 앱이 시작한 백엔드도 창 종료 후 남아 다음 실행에서 재사용된다. Actuator 종료 노출은 로컬 API 경계를 약화시켜 채택하지 않았다. 이후 시작 작업의 짧은 자체 명령 정리와 공용 서비스 종료는 구분한다.

## 당시 검증

- Rust 1.98.1/MSVC, C++ Build Tools, WebView2 환경에서 cargo check와 Rust 테스트 2개 통과.
- 백엔드 보고: 테스트 174개, 선택 실행 실제 모델 테스트 1개 제외, 실패 0.
- 프런트엔드 11개 통과, Vite 빌드 성공.
- 실행 파일 11.49 MB, 서명 없는 NSIS 설치 파일 62.46 MB 생성.
- 실제 LocalRAG 창에 프로젝트 13개 표시, Local_Ai_Work 선택.
- RAG SUCCESS/출처 5개, Agent getGitStatus, 오류 이력/진행 상태 화면, LLM 생략 자동화 SUCCESS 확인.

Windows 화면 제어 도구 초기화 오류로 실제 WebView2에 로컬 DevTools 연결을 사용했다. 이 접속 지점은 배포 기본값으로 활성화하지 않는다.

## 한계와 결과

외부 Java 17이 필요하고 Docker/PostgreSQL/Ollama/모델은 동봉하지 않는다. 서명 없는 설치 파일은 SmartScreen 경고가 발생할 수 있다. 최종 점검은 기존 백엔드를 재사용했으므로 새 JAR 자동 시작은 이 단계에서 컴파일/단위 테스트까지만 확인했다. 정상 서비스 강제 중단 없이 장애 분기를 고정 테스트로 검증했다. desktop:dev의 5173은 기존 Vite가 사용해 동시 실행하지 못했다.

Windows 배포 산출물을 만들었으나 코드 서명, Java 동봉, 정상 자식 종료, 네이티브 알림, 자동 업데이트는 구현하지 않았다. 서비스 준비 자동화는 후속 0033을 참고한다.
