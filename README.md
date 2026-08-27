# ADP-BE

Java 21 / Spring Boot 3 / Gradle 기반 ADP Gateway Runtime입니다.

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
│   ├── common/         # 공통 Contract, Error, Trace
│   ├── auth/           # 인증·인가 경계
│   ├── workload/       # Workload 경계
│   ├── dataaccess/     # Data Access 경계
│   ├── retrieval/      # Retrieval 경계
│   ├── context/        # Context 경계
│   ├── detection/      # Detection 경계
│   ├── policy/         # Policy Artifact와 Fixture 경계
│   ├── decision/       # Runtime Decision 경계
│   ├── transform/      # Transform 경계
│   ├── vault/          # Vault Mapping 경계
│   ├── connector/      # Connector 경계
│   ├── guard/          # Guard 경계
│   ├── audit/          # Audit Event 경계
│   ├── operations/     # 내부 운영·Mock Runtime API
│   └── config/         # 공통 애플리케이션 설정
├── src/main/resources  # Spring Boot 설정
│   └── db/migration    # Flyway migration
├── src/test/java       # 부트스트랩 검증 테스트
├── Dockerfile
├── docker-compose.yml
├── Makefile
├── settings.gradle
└── build.gradle
```

## 빠른 시작

현재 로컬 환경에 Gradle이 없어도 Docker 기반 Gradle 이미지로 검증할 수 있습니다.

```bash
make setup
make check
```

## 로컬 실행

```bash
make docker-up
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/internal/info
curl -X POST http://localhost:8080/api/runtime/mock \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req_local_001' \
  -H 'X-Trace-Id: trace_local_001' \
  -H 'Idempotency-Key: idem_local_001' \
  -d '{"workloadId":"workload_local","purpose":"local-smoke","subject":"mock-subject"}'
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

- [x] Java 21
- [x] Spring Boot 3
- [x] Gradle
- [x] Modular Monolith 최상위 모듈 경계 고정
- [x] PostgreSQL + Flyway baseline migration
- [x] `request_id`, `trace_id`, `idempotency_key` Runtime Contract
- [x] Error Response Contract
- [x] Reason Code
- [x] Actuator liveness/readiness health
- [x] Docker Compose Local E2E 환경
- [x] Fake Connector
- [x] `PROJECT_PROVISIONAL` Policy Fixture
- [x] DA Artifact 수신 Port
- [x] Application Context Test
- [x] Flyway Migration Test
- [x] Trace propagation / Mock Runtime Flow Test
- [x] Mock Request -> Fake Decision -> Fake Connector -> Audit Record
- [x] Redis/Kafka 미도입
