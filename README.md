# ADP-BE

Java 21 / Spring Boot 3 / Gradle 기반 ADP Gateway Runtime입니다.

## 역할

- Runtime Policy Enforcement
- Decision / Transform / Egress orchestration
- Vault / Connector integration boundary
- Audit Event 생성
- Runtime API 제공
- Production Reference Architecture와 검증 Claim 경계 관리

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
│   ├── evidence/       # 관리자 추적용 Reference Evidence 조회 경계
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
make integration-check INTEGRATION_PROFILE=demo
make architecture-validate
```

`integration-check`는 Repository Lock과 Flyway 기준을 먼저 검증하고 BE·FE·DA·Docs·PostgreSQL·Mock Provider를
모두 build/start한 뒤 health와 실행 profile을 확인합니다. 일반 개발은 `local`, 합성 데이터 데모는 `demo`, 운영 기본값에
가까운 fail-closed 설정 확인은 `production-like` profile을 사용합니다.

로컬 통합 환경의 관리자 콘솔은 Session 기반 로그인을 사용합니다. `auditor-local`, `privileged-operator-local`,
`operator-local` 데모 계정은 local fixture가 활성화된 경우에만 제공되며, Runtime API는 별도의 `X-ADP-API-Key`
stateless 인증 경계를 유지합니다.

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
make integration-check INTEGRATION_PROFILE=demo
make integration-down INTEGRATION_PROFILE=demo
make ai-eval-e2e
make docker-down
make check
```

`make check`는 개발 DB를 오염시키지 않도록 별도 `postgres-test` 컨테이너를 사용합니다.

운영 목표 배치와 현재 검증 범위는 [Production Reference Architecture](docs/production-reference-architecture.md)에서 관리합니다.

## API 문서

BE 실행 후 Swagger UI에서 전체 API 계약을 확인하고 요청을 실행할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

보호된 API는 Swagger UI의 `Authorize`에서 로컬 개발용 `X-ADP-API-Key`를 설정한 뒤 호출합니다.

## 문서

- [구현 진행 현황](docs/implementation-progress.md)
- [SEC-0 Security Contract](docs/sec-0-security-contract.md)
- [SEC-1/2 Security Hardening](docs/sec-1-2-security-hardening.md)
- [AI-EVAL-0 NVIDIA Model Connector And Profiles](docs/ai-eval-0-nvidia-model-profiles.md)
- [AI-EVAL-1 Evaluation Run Contract](docs/ai-eval-1-evaluation-run-contract.md)
- [AI-EVAL-2 Runtime Evidence Capture](docs/ai-eval-2-runtime-evidence.md)
- [AI-EVAL-3 DA Evaluation Bundle Export](docs/ai-eval-3-da-evaluation-bundle.md)
- [AI Evaluation Real E2E Handoff](docs/ai-eval-real-e2e-handoff.md)
- [AI Experiment 02 Calibration Evidence](docs/ai-experiment-02-calibration-evidence.md)
- [BE-4 Policy & Decision Core](docs/be-4-policy-decision-core.md)
- [BE-5 Transform Engine & Vault](docs/be-5-transform-vault.md)
- [BE-6 Common Egress Boundary](docs/be-6-common-egress-boundary.md)
- [BE-7 AI Full E2E](docs/be-7-ai-full-e2e.md)
- [BE-8 Digital Asset Thin E2E](docs/be-8-digital-asset-thin-e2e.md)
- [BE-8 Digital Asset Recovery](docs/be-8-digital-asset-recovery.md)
- [BE-8 Digital Asset Policy Gate](docs/be-8-digital-asset-policy-gate.md)
- [BE-8 Digital Asset Mismatch Recovery](docs/be-8-digital-asset-mismatch-recovery.md)
- [DA-P0-1 Digital Asset Runtime Realignment Impact](docs/digital-asset-runtime-realignment-impact.md)
- [DA-P0-2 Approved Transaction Trust Boundary](docs/da-p0-2-approved-transaction-boundary.md)
- [DA-P0-3 Digital Asset Domain Contracts](docs/da-p0-3-domain-contracts.md)
- [DA-P0-4 Canonical Contract Freeze](docs/da-p0-4-canonical-contract.md)
- [DA-P0-5 Artifact Loader](docs/da-p0-5-artifact-loader.md)
- [DA-P0-6 Versioned Runtime Snapshot](docs/da-p0-6-versioned-runtime-snapshot.md)
- [DA Analysis to BE Runtime Contract Gap Review](docs/da-analysis-runtime-contract-gap-review.md)
- [NCP-5 BE NCP ContentStore](docs/ncp-5-be-content-store.md)
- [Pack Runtime Resolver](docs/pack-runtime-resolvers.md)
- [BE-9A Idempotency Core](docs/be-9a-idempotency-core.md)
- [BE-9B External Interaction Recovery Core](docs/be-9b-external-interaction-recovery.md)
- [BE-9 Recovery Operations](docs/be-9-recovery-operations.md)
- [BE-10 Policy Lifecycle Skeleton](docs/be-10-policy-lifecycle-skeleton.md)
- [BE-11A Observability Foundation](docs/be-11a-observability-foundation.md)
- [BE-11B Audit Read Model](docs/be-11b-audit-read-model.md)
- [BE-11 Observability Operations](docs/be-11-observability-operations.md)
- [Policy · Regulation · Evidence Plane](docs/policy-regulation-evidence-plane.md)
- [Slice 25 Policy Operations Read Model](docs/slice-25-policy-operations-read-model.md)
- [Slice 25 Security Finding Read Model](docs/slice-25-security-finding-read-model.md)
- [My Work / Approval Inbox](docs/my-work-approval-inbox.md)
- [Troubleshooting Index](docs/troubleshooting/index.md)
- [Slice 30 Local Integration Reproducibility](docs/slice-30-local-integration-reproducibility.md)
