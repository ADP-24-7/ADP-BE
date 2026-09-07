# SEC-0 Security Contract

이 문서는 ADP-BE가 AI 및 Digital Asset 외부 실행을 중계할 때 반드시 유지해야 하는 보안 경계와 검증 증적을 정의한다. SEC-0은 새로운 공식 BE Phase가 아니라 BE-1~12를 가로지르는 보안 기준선이다.

## Responsibility Boundary

ADP-BE는 WAF, API Gateway, IAM, TLS/mTLS, 네트워크 방화벽, KYC/AML 원장, Private Key/Custody를 대체하지 않는다. 기존 보안 계층을 통과한 요청에 대해 다음 semantic authorization과 execution control을 담당한다.

```text
Caller
  -> Authentication
  -> Institution + Role + Workload + Purpose + Subject + Action Authorization
  -> Minimum Retrieval + Field Allowlist
  -> Policy Decision + Transform
  -> Destination/Profile/Schema/Secret Guard
  -> Connector
  -> Response Guard + Controlled Delivery
  -> Audit/Trace/Recovery
```

## Protected Assets

| Asset | 보안 목표 |
| --- | --- |
| 고객·계좌·Wallet 원문 | 승인된 최소 범위 외 조회·응답·로그·외부 전송 금지 |
| Prompt 및 Provider Payload | Policy/Transform/Destination/Provider Payload의 version·digest를 동일 Execution에 고정해 상호 추적 |
| API Key·Secret·Token Mapping·Private Key | 애플리케이션 응답, Audit, Log, Metric 비노출 |
| Policy·Profile·Approval | Version/Digest를 실행 시작 시 고정하고 실행 중 교체 방지 |
| Digital Asset Intent | Amount, Asset, Network, Counterparty 변조 및 중복 실행 방지 |
| Audit Evidence | 실행 결과를 원문 없이 재현하고 tenant 간 증적 혼합 방지 |

## Trust Boundaries

| Boundary | 신뢰하지 않는 입력 | 필수 통제 |
| --- | --- | --- |
| Caller -> HTTP API | Header, body, idempotency key, subject | AuthN, context-bound AuthZ, schema validation, canonical hash |
| Runtime -> Database | 동적 조회 조건과 tenant scope | predefined query, field allowlist, institution/workload SQL scope |
| Runtime -> Policy/Profile | version, digest, effective period | immutable snapshot, pinning, fail closed |
| Runtime -> Connector | destination, mapped payload | server-owned profile, outbound guard, correlation key |
| Provider -> Runtime | response body, status, correlation | response guard, request correlation, typed status mapping |
| Worker -> Recovery Store | lease, retry result | claim/lease ownership, stale worker rejection, reconciliation-first |
| Admin -> Governance | transition, approval, export | RBAC, maker-checker, optimistic revision, scoped read model |

## Security Invariants

1. 공개 endpoint는 명시된 health/info/docs allowlist로 제한하고 matcher에 포함되지 않은 나머지 endpoint는 인증 여부와 관계없이 거부한다.
2. Runtime Service credential과 Admin User credential은 상호 대체할 수 없다.
3. Authorization 성공 전 Retrieval, Destination load, Connector 호출을 수행하지 않는다.
4. Institution, Workload, Purpose, Subject, Action scope 중 하나라도 불일치하면 fail closed한다.
5. 허용 Field가 없는 Dataset은 조회하지 않으며 외부 Payload는 승인된 Field만 포함한다.
6. Caller가 전달한 URL을 Connector destination으로 사용하지 않고 서버 등록 Profile만 사용한다.
7. Policy/Profile이 누락·만료·충돌하면 ALLOW로 완화하지 않는다.
8. Canonical request hash가 다른 동일 idempotency key 요청은 충돌로 처리한다.
9. `SENT_UNKNOWN`은 즉시 재전송하지 않고 provider status reconciliation을 먼저 수행한다.
10. 로그·Metric·Audit·Admin API에는 업무 Payload의 Raw PII, Prompt, Secret, Token Mapping을 저장하지 않는다. Caller가 제공하는 request/trace ID는 PII를 포함하지 않는 opaque technical identifier라는 upstream 계약을 전제로 한다.
11. Policy 승인·활성화와 Privileged Evidence Export는 일반 Operator 권한으로 수행할 수 없다.

## Threat And Negative Test Matrix

| Threat | Expected Result | Existing Evidence | Status |
| --- | --- | --- | --- |
| 인증 없는 Runtime/Admin/Privileged/Prometheus 접근 | 401 | `SecurityDefaultDenyTests` | Covered |
| 인증된 Principal의 security matcher 누락 endpoint 접근 | 403 | `SecurityDefaultDenyTests` | Covered |
| Service API Key로 Admin 접근 | 401 | `SecurityBoundaryTests` | Covered |
| Local User Header로 Runtime 접근 | 401 | `SecurityBoundaryTests` | Covered |
| 다른 Institution/Workload의 객체 조회 | 403 또는 scoped 404 | Audit/Lifecycle controller tests | Covered |
| 다른 Subject 또는 Purpose로 최소조회 | 차단, Connector 0회 | Data access/runtime tests | Covered |
| 미등록 Workload/Profile/Pack adapter | fail closed | Runtime/resolver tests | Covered |
| 승인되지 않은 Field 또는 Secret 외부 전송 | Guard 차단, Connector 0회 | Outbound guard tests | Covered |
| Provider 응답의 PII/Secret 재노출 | Controlled Delivery 차단 | AI/Digital Asset response guard tests | Covered |
| 동일 key와 다른 body | 409, 추가 egress 0회 | Idempotency tests | Covered |
| `SENT_UNKNOWN` 즉시 재전송 | reconciliation 우선 | Recovery tests | Covered |
| stale recovery worker의 상태 갱신 | 충돌, terminal overwrite 금지 | Recovery persistence tests | Covered |
| Digital Asset expected/actual mismatch | `REVIEW_REQUIRED`, delivery withheld | Digital Asset mismatch tests | Covered |
| Caller-controlled request/trace ID에 PII 포함 | internal ID 생성 또는 digest 기반 log correlation | SEC-1 | Deferred |
| Caller 입력 URL/내부망 주소로 destination 변경 | 요청 계약에서 URL 미수용 + adapter negative test | SEC-1 | Deferred |
| 재사용 nonce 또는 허용 시간을 벗어난 timestamp | 실행 전 차단 | SEC-1 | Deferred |
| Authorization deny attempt 증적 누락 | reservation과 독립된 raw-free evidence | SEC-1 | Deferred |
| 취약 dependency, image, secret commit | CI 차단 및 SBOM 생성 | SEC-2 | Deferred |

## Public Endpoint Allowlist

- `/actuator/health/**`
- `/actuator/info`
- `/api/internal/info`
- `/`, `/docs`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`

`/actuator/prometheus`는 기본적으로 인증이 필요하다. 로컬 또는 격리된 scrape 환경에서만 `adp.observability.prometheus-public=true`로 명시적으로 공개할 수 있다.

## Evidence Rules

- 보안 거부 응답은 공통 `ErrorResponse`와 서버 정의 Reason Code를 사용한다.
- 구조화 로그의 `request_id`, `trace_id`는 현재 문자·길이를 제한하지만 caller-controlled 값이다. Upstream은 PII를 넣지 않는 opaque ID만 전달해야 하며, 이 전제 제거를 위한 server-owned internal ID 또는 digest correlation은 SEC-1에서 결정한다.
- Audit에는 subject 원문 대신 digest를, Payload 원문 대신 schema/digest/count를 저장한다.
- 테스트 fixture는 합성 데이터만 사용하며 실제 Credential을 저장하지 않는다.

## Deferred Hardening

- **SEC-1**: Destination SSRF negative test, request nonce/timestamp freshness, denied-attempt evidence, caller trace ID의 internal/digest correlation
- **SEC-2**: secret scan, SAST, dependency/container scan, SBOM CI gate
- **SEC-3 / BE-10**: 실제 DA Artifact activation, 단일 ACTIVE, rollback propagation
- **SEC-4 / BE-12**: management port와 network policy, DB 최소권한, KMS/Secret adapter, NCP 보안 QA
