# ADP-BE

Java 21 / Spring Boot 기반 ADP Gateway Runtime입니다.

## 역할 범위

- Runtime Policy Enforcement
- Decision Source of Truth
- Transform orchestration
- Vault / Connector integration boundary
- Audit Event 생성
- Runtime API 제공

## 기본 구조

```text
ADP-BE/
├── src/main/java/com/adp/gateway
│   ├── config/         # 공통 애플리케이션 설정
│   └── health/         # 로컬·컨테이너 헬스체크
├── src/main/resources  # Spring Boot 설정
├── src/test/java       # 부트스트랩 검증 테스트
├── Dockerfile
├── docker-compose.yml
├── Makefile
└── pom.xml
```

## 빠른 시작

현재 로컬 환경에 Maven/Gradle이 없어도 Docker 기반 Maven 이미지로 검증할 수 있습니다.

```bash
make setup
make check
```

## 로컬 실행

```bash
make docker-up
curl http://localhost:8080/health
curl http://localhost:8080/actuator/health
```

## Make 명령

```bash
make help
make setup
make test
make package
make check
make run
make docker-up
make docker-down
```

## BE-0 완료 기준

- Java 21 / Spring Boot 프로젝트 골격 생성
- `/health`, `/actuator/health` 헬스체크 제공
- 테스트와 패키징 명령 제공
- Docker 기반 로컬 실행 경로 제공
- ADP 공통 `adp-local` Docker 네트워크 연결
