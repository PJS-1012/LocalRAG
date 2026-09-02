# Decision Log 0001: Java 및 Framework 기준 버전

## 문제

프로젝트의 Java, Spring Boot, Spring AI 기준 버전을 정해야 한다.

## 원인

개발 PC에서 Java 17이 활성화되어 있고, 프로젝트 소유자가 Java 17을 그대로 사용하기로 결정했다.

## 후보

- Java 21 + 최신 Framework 조합
- Java 17 + Spring Boot 4.x + Spring AI 2.x
- Java 17 + Spring Boot 3.5.x + Spring AI 1.1.x

## 선택

- Java 17
- Spring Boot 3.5.16
- Spring AI 1.1.8 (Phase 3부터 필요한 모듈만 도입 예정)
- Gradle Wrapper 8.14.3

## 이유

Spring Boot 3.5.16은 Java 17과 Gradle 8.x를 공식 지원한다. Spring AI 1.1.x는 Spring Boot 3.4.x와 3.5.x를 지원한다. 검증된 Spring Boot 3.x 생태계를 유지하면서 현재 활성 JDK를 그대로 사용할 수 있다.

## 결과

Gradle이 Java 17.0.17을 사용해 전체 테스트와 빌드를 완료했다. Spring Boot 3.5.16 애플리케이션의 커스텀 Health API와 Actuator Health가 모두 `UP`을 반환했다.
