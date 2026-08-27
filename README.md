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

## BE-1 완료 기준

- [x] Spring Security 기반 stateless API 인증
- [x] `X-ADP-API-Key` 기반 Service Principal 인증
- [x] API Key 원문 미저장, SHA-256 hash 기반 lookup
- [x] Principal / Role / Workload / API Key PostgreSQL schema baseline
- [x] Local Test Harness credential은 opt-in fixture로 분리
- [x] RBAC role model
- [x] Context 권한 모델
  - workload mapping
  - action
  - purpose
  - subject grant
- [x] `RUNTIME_EXECUTOR` 권한 기반 Mock Runtime 실행 인가
- [x] Purpose 검증을 Subject 검증 여부와 독립적으로 적용
- [x] Privileged Action은 `PRIVILEGED_OPERATOR` 권한으로 분리
- [x] 인증 실패 / 인가 실패 공통 `ErrorResponse` 및 reason code 분리
- [x] `/api/internal/auth/context` 인증 컨텍스트 확인 API
- [x] Runtime Service credential / Admin User credential 인증 경계 분리
- [x] Local User Header Stub은 `adp.local-user-auth.enabled=true`에서만 활성화
- [x] Actuator health와 internal info는 인증 없이 조회 허용
- [x] Local Test Harness API key fixture 제공

## BE-2 완료 기준

- [x] Workload Registry baseline
- [x] Retrieval Profile baseline
- [x] Dataset/Field allowlist와 Data Class metadata
- [x] Subject Scope, Dataset별 Time Window, Dataset별 Row Limit 기반 Data Access Guard
- [x] 자유 SQL 없이 Workload별 Predefined Retrieval Adapter 사용
- [x] Demo Synthetic Financial Schema
- [x] Synthetic Seed Data는 opt-in local fixture로 분리
- [x] Query/Data Access Audit Metadata 저장
- [x] Audit에는 subject 원문 대신 subject digest 저장
- [x] 조회 결과 원문은 Audit에 저장하지 않고 field/data class/row count만 기록
- [x] 허용 Field만 DB SELECT list에 포함
- [x] 허용 Field가 없는 Dataset은 조회 자체를 생략
- [x] Dataset별 Row Limit와 기간 제한 검증
- [x] 다른 Subject와 다른 Purpose 조회 차단
- [x] Retrieval Profile 없음 또는 Workload 미등록 시 임의 조회 금지

## BE-3 완료 기준

- [x] Canonical Context Schema 추가
- [x] Retrieval 결과를 Canonical Context로 조립하는 Context Builder 추가
- [x] Context Field에 Data Class Metadata 부여
- [x] 선택되지 않은 원문 Field는 Context 조립 단계에서 제거
- [x] Context/API 응답에는 raw value 대신 value digest 노출
- [x] Subject 원문 대신 subject digest 유지
- [x] `SensitiveDataDetector` Port 추가
- [x] 초기 Rule/Regex Detector Adapter 추가
- [x] 이름, 전화, 계좌, 이메일, 주민번호 형식 탐지 규칙 추가
- [x] Detector Version Metadata 포함
- [x] Detector Finding에 Type, Location, Offset, Evidence Digest 포함
- [x] Unknown Data Class 처리 테스트 추가
- [x] Local 검증용 Context Preview API 추가
