# ADP-BE

Java 21 / Spring Boot 3 / Gradle 기반 ADP Gateway Runtime입니다.

## 역할

- Runtime Policy Enforcement
- Decision / Transform / Egress orchestration
- Vault / Connector integration boundary
- Audit Event 생성
- Runtime API 제공

## Docker 개발 환경

ADP-BE의 `docker-compose.yml`은 로컬 통합 개발 스택의 진입점입니다. BE 레포에서 실행하면 같은 상위 폴더에 있는 `ADP-BE`, `ADP-FE`, `ADP-DA`, `ADP-Docs` 네 레포와 PostgreSQL이 함께 실행됩니다.

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
│   ├── egress/         # Outbound Guard / Connector / Response Guard 경계
│   ├── audit/          # Audit Event 경계
│   ├── operations/     # 내부 운영·Mock Runtime API
│   └── config/         # 공통 애플리케이션 설정
├── src/main/resources
│   └── db/migration    # Flyway migration
├── src/test/java       # 검증 테스트
├── docs                # 구현 단계 추적과 개발 기준
├── Dockerfile          # CI/NCP 배포용 이미지
├── Dockerfile.dev      # 로컬 개발용 이미지
├── docker-compose.yml  # 로컬 통합 개발 스택
├── Makefile
├── settings.gradle
└── build.gradle
```

## 실행

각 레포의 최신 `main`을 받은 뒤 BE 레포에서 실행합니다.

```bash
make setup
make docker-up
```

## Docker 파일 기준

- `Dockerfile`: CI/NCP 배포용 jar image build
- `Dockerfile.dev`: 로컬 개발용 Gradle `bootRun`
- `docker-compose.yml`: BE/FE/DA/Docs/PostgreSQL 통합 개발 스택
- `.env.example`: 팀 공통 로컬 환경변수 샘플

## Make 명령

```bash
make setup
make docker-up
make docker-logs
make docker-ps
make docker-down
make check
```

`make check`는 개발 DB를 오염시키지 않도록 별도 `postgres-test` 컨테이너를 사용합니다.

## API 문서

BE 실행 후 Swagger UI에서 전체 API 계약을 확인하고 요청을 실행할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

보호된 API는 Swagger UI의 `Authorize`에서 로컬 개발용 `X-ADP-API-Key`를 설정한 뒤 호출합니다.

## 문서

- [구현 진행 현황](docs/implementation-progress.md)
- [BE-4 Policy & Decision Core](docs/be-4-policy-decision-core.md)
- [BE-5 Transform Engine & Vault](docs/be-5-transform-vault.md)
- [BE-6 Common Egress Boundary](docs/be-6-common-egress-boundary.md)
- [Pack Runtime Resolver](docs/pack-runtime-resolvers.md)
