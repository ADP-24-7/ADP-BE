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
├── docs                # 구현 단계 추적과 개발 기준
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
curl http://localhost:8080/api/internal/auth/context \
  -H 'X-ADP-API-Key: local-dev-api-key'
curl -X POST http://localhost:8080/api/runtime/mock \
  -H 'Content-Type: application/json' \
  -H 'X-ADP-API-Key: local-dev-api-key' \
  -H 'X-Request-Id: req_local_001' \
  -H 'X-Trace-Id: trace_local_001' \
  -H 'Idempotency-Key: idem_local_001' \
  -d '{"workloadId":"workload_local","purpose":"local-smoke","subject":"customer:mock-subject"}'
curl -X POST http://localhost:8080/api/runtime/data-access/preview \
  -H 'Content-Type: application/json' \
  -H 'X-ADP-API-Key: local-dev-api-key' \
  -H 'X-Request-Id: req_data_access_local' \
  -H 'X-Trace-Id: trace_data_access_local' \
  -d '{"workloadId":"customer_summary","purpose":"CUSTOMER_SUPPORT","subject":"customer:customer-100"}'
curl -X POST http://localhost:8080/api/runtime/context/preview \
  -H 'Content-Type: application/json' \
  -H 'X-ADP-API-Key: local-dev-api-key' \
  -H 'X-Request-Id: req_context_local' \
  -H 'X-Trace-Id: trace_context_local' \
  -d '{"workloadId":"customer_summary","purpose":"CUSTOMER_SUPPORT","subject":"customer:customer-100"}'
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

## 구현 단계 추적

README는 프로젝트 개요와 실행 방법만 유지합니다.

- [구현 진행 현황](docs/implementation-progress.md)
- [BE-4 개발 기준](docs/be-4-policy-decision-core.md)
