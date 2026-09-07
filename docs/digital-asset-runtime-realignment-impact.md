# DA-P0-1 Digital Asset Runtime Realignment Impact

## 목적

PR #14~#17의 Digital Asset 구현을 삭제하지 않고 최신 DA Handoff의 책임 경계에 맞춰 분류한다.
이 문서는 P0-2~P0-4 구현 전 영향도 Source of Truth이며, 기존 BE-8 문서는 당시 구현의 역사적
baseline으로만 사용한다.

최신 Runtime 시작점은 `ApprovedTransaction + OutboundRequest`다. FPG는 거래 적격성을 다시 판단하지 않고,
승인값과 요청값의 일치, 외부전달 필수정보, exact 보존, 허용 Transform, Destination Payload와 Trace를 집행한다.

## 현재 Active Path

```text
DigitalAssetPurchaseInput
-> DigitalAssetCanonicalContextBuilder
-> DigitalAssetComplianceContextResolver
-> KYC / AML / Wallet assertion을 Canonical Context에 병합
-> DigitalAssetPolicyGate
-> KYC / AML / Wallet / Amount Limit으로 REVIEW 또는 BLOCK
-> Transform / Outbound Candidate
-> DigitalAssetExternalSchemaMapper
-> FakeDigitalAssetConnector
-> DigitalAssetResponseGuard
-> DigitalAssetSettlementOutcomeHandler
-> Reconciliation / Mismatch / Recovery / Trace
```

최신 Handoff와 충돌하는 구간은 Compliance Context 병합부터 Policy Gate의 eligibility 판정까지다.
Common Runtime, Egress 이후 외부 상태 추적, Idempotency와 Recovery는 유지한다.

## Eligibility Field 영향도

| Field | Current Path | Current Effect | Classification | P0-2 Action |
| --- | --- | --- | --- | --- |
| `kyc_status` | Compliance Adapter -> Context Builder -> Policy Gate -> Provider Payload -> Mismatch | 미허용 상태를 `REVIEW`로 변경 | `REMOVE_FROM_ACTIVE` + `LEGACY_ONLY` | Decision/Provider Payload/Mismatch 기준에서 제거하고 Upstream assertion metadata로만 보존 가능 |
| `aml_status` | Compliance Adapter -> Context Builder -> Policy Gate -> Provider Payload -> Mismatch | 미허용 상태를 `REVIEW`로 변경 | `REMOVE_FROM_ACTIVE` + `LEGACY_ONLY` | KYC와 동일 |
| `wallet_verified` | Compliance Adapter -> Context Builder -> Policy Gate -> Provider Payload -> Mismatch | false를 `REVIEW`로 변경 | `REMOVE_FROM_ACTIVE` + `LEGACY_ONLY` | Wallet 주소 자체의 approved-vs-requested 비교와 분리 |
| `vasp_eligibility` | 현재 코드/Schema/Test에 없음 | 없음 | `NOT_IMPLEMENTED` | 새 Active Gate로 추가하지 않음 |
| `sanctions_status` | 현재 코드/Schema/Test에 없음 | 없음 | `NOT_IMPLEMENTED` | 새 Active Gate로 추가하지 않음 |
| `customer_risk_score` | 현재 코드/Schema/Test에 없음 | 없음 | `NOT_IMPLEMENTED` | 새 Active Gate로 추가하지 않음 |

KYC/AML/Wallet 값을 외부전달 규제정보로 사용해야 하는 경우에도 FPG가 상태를 판정하지 않는다. Upstream이
제공한 assertion의 source/version/digest 또는 required outbound field로만 취급한다.

## Component Impact Matrix

| Component | Current Meaning | New Meaning | Action | Migration Impact | Test Impact |
| --- | --- | --- | --- | --- | --- |
| `RuntimeExecutionService` / Pack Resolver | 공통 실행 orchestration | 동일 | `REUSE` | 없음 | Common Runtime 회귀 테스트 유지 |
| `ExecutionPackPolicyGateResolver` | Digital Asset Gate 필수, 미구성 시 BLOCK | Digital Asset Controls 필수, 미구성 시 BLOCK | `REUSE` | 없음 | fail-closed 테스트 유지 |
| `DigitalAssetPurchaseInput` | 고객/계좌/지갑/자산/금액 단일 입력 | `ApprovedTransaction`과 `OutboundRequest` 분리 | `DEPRECATED` | P0-3에서 신규 계약 추가 | 기존 request JSON 교체 |
| `DigitalAssetCanonicalContextBuilder` | Purchase Input과 Compliance assertion 병합 | 승인된 거래와 요청값을 별도 namespace로 병합 | `MEANING_CHANGE` | 신규 Evidence ref 필요 | subject/approval/request binding 테스트 추가 |
| `DigitalAssetComplianceContextPort/Resolver` | KYC/AML/Wallet authoritative eligibility 조회 | 선택적 Upstream assertion metadata boundary | `REMOVE_FROM_ACTIVE`, 이후 `LEGACY_ONLY` | 기존 저장 데이터 없음 | 미구성 시 실행 차단 테스트 제거 또는 legacy 테스트로 이동 |
| `DigitalAssetComplianceContext` / Local Adapter | eligibility 값과 digest | 역사적 fixture 또는 Upstream metadata | `LEGACY_ONLY` | 없음 | Active Decision 불변성 테스트로 교체 |
| `DigitalAssetPolicyGate` | KYC/AML/Wallet/Amount Limit으로 REVIEW | approved-vs-requested 및 outbound control orchestration | `REWRITE` | V19 history 보존 | 6 Controls 전 단계별 negative test로 교체 |
| `DigitalAssetPolicyProfilePort/Profile` | 허용 KYC/AML 상태와 risk amount limit | Versioned Runtime Control/Requirement profile | `MEANING_CHANGE` | 새 schema/version 필요 | profile binding/digest/effective time 테스트 유지, field 의미 교체 |
| `InputOnlyDigitalAssetRetrievalAdapter` | request dataset만 허용 | server-resolved approval + request-only 최소조회 | `REUSE_WITH_CHANGE` | Local profile field 갱신 | 임의 DB 조회 0 및 allowlist 테스트 유지 |
| `ProjectProvisionalApprovalScopeAdapter` | Digital Asset approval scope fixture | Approved Transaction reference와 Runtime scope | `REUSE_WITH_CHANGE` | 운영 DB 영향 없음 | approval identity binding 추가 |
| `ProjectProvisionalPolicySnapshotAdapter` | 공통 baseline policy | 공통 baseline policy | `REUSE` | 없음 | 회귀 테스트 유지 |
| `ProjectProvisionalTransformStrategyResolver` | Customer/Account token, 나머지 exact | Artifact가 허용한 transform과 exact field 분리 | `REUSE_WITH_CHANGE` | Vault schema 변경 없음 | exact/transform 허용 Matrix로 교체 |
| `DestinationProfile` / local adapter | Digital Asset request v1 | Destination-specific outbound schema | `REUSE_WITH_VERSION_BUMP` | 기존 row 보존, v2 fixture 추가 | caller URL/schema 주입 거부 유지 |
| `DigitalAssetExternalSchemaMapper` | 기존 Purchase/Compliance field mapping | Outbound Requirement + Transform 기반 payload | `REWRITE_BOUNDARY` | payload 저장 없음 | required/exact/destination mapping 테스트 추가 |
| `FakeDigitalAssetConnector` / state store | Mock settlement response | Mock external execution result | `REUSE_WITH_CHANGE` | 없음 | PASS만 호출, SENT_UNKNOWN 재전송 0 유지 |
| `DigitalAssetResponseGuard` | settlement response v1 검증 | external execution result schema/version 검증 | `REUSE_WITH_VERSION_BUMP` | 기존 Evidence 유지 | malformed/status/correlation 테스트 유지 |
| `DigitalAssetSettlementOutcomeHandler` | settlement finality를 Runtime 상태로 변환 | 외부 실행 상태를 Runtime 상태로 변환 | `MEANING_CHANGE` | 신규 external execution evidence 권장 | HTTP 성공과 외부 최종상태 분리 테스트 유지 |
| `FakeDigitalAssetStatusQueryAdapter` | settlement 전송 상태 조회 | external execution 상태 조회 | `REUSE` | 공통 Recovery schema 유지 | reconciliation-first 테스트 유지 |
| `DigitalAssetReconciliationEvaluator` | request transaction과 settled transaction 비교 | Approved + Outbound + External Result 비교 | `REWRITE_COMPARISON` | V20 history 보존 | asset/amount/destination/beneficiary/reference 비교로 교체 |
| `DigitalAssetMismatchField` | KYC/AML/Wallet 포함 mismatch enum | 승인·요청·외부실행 필드 enum | `VERSIONED_CHANGE` | 기존 enum value 삭제 금지 | Provider 임의 key 정규화 테스트 유지 |
| `JdbcDigitalAssetTransactionPersistence` | settlement evidence 저장 | legacy evidence reader; 신규 external execution persistence로 대체 | `LEGACY_ONLY` | V16~V18 row 보존 | 기존 upgrade test 유지 |
| `JdbcDigitalAssetMismatchPersistence` | V20 mismatch case 저장 | v2 mismatch evidence 저장 경계로 재사용 가능 | `REUSE_WITH_MIGRATION` | 허용 enum additive 확장 또는 v2 table | raw-free/auto-retry=false 유지 |
| Common Egress / Vault / Audit / Recovery / Observability | 공통 통제와 증적 | 동일 | `REUSE` | 기존 table/metric 유지 | 기존 공통 테스트 수정 금지 |

## Migration Strategy

V16~V20은 이미 적용 가능한 Flyway history이므로 수정하거나 삭제하지 않는다.

| Migration | Existing History | Strategy |
| --- | --- | --- |
| V16~V18 `digital_asset_transaction` | settlement 용어와 상태 조합 | 기존 row의 역사적 의미를 보존한다. 새 계약을 settlement로 덮어쓰지 않고 P0-3/P0-8에서 별도 external execution evidence를 additive migration으로 추가한다. |
| V19 `execution_pack_policy_evaluation` | KYC/AML/Wallet profile 판정과 assertion provenance | 공통 Pack evidence로 유지한다. 기존 assertion 컬럼은 nullable history로 보존하고, 새 Control/Artifact reference가 필요하면 새 table 또는 nullable column으로 추가한다. |
| V20 `digital_asset_mismatch_case` | 기존 transaction projection mismatch | 기존 KYC/AML/Wallet enum을 CHECK에서 제거하지 않는다. 신규 mismatch label은 additive 확장하거나 contract v2 table로 분리한다. |
| Local V4 fixture | Purchase/Compliance request fields와 v1 destination | 기존 migration을 수정하지 않고 다음 local migration에서 v2 workload/profile/destination fixture를 추가한다. |

다음 정식 migration 번호는 V26이다. 기존 컬럼 rename이나 legacy row backfill로 과거 의미를 새 의미처럼 바꾸지 않는다.

## Reason Code Decision

### Retain

- `EXECUTION_PACK_POLICY_GATE_NOT_CONFIGURED`: Digital Asset Controls가 없을 때 fail closed하는 공통 의미로 유지한다.
- `DIGITAL_ASSET_POLICY_PROFILE_INVALID`: P0-4 contract freeze 전까지 profile scope/version/digest 오류에 유지한다.
- 공통 Auth, Idempotency, Egress, Recovery Reason Code는 변경하지 않는다.

### Remove From Active / Deprecated

- `DIGITAL_ASSET_COMPLIANCE_CONTEXT_NOT_CONFIGURED`
- `DIGITAL_ASSET_KYC_REVIEW_REQUIRED`
- `DIGITAL_ASSET_AML_REVIEW_REQUIRED`
- `DIGITAL_ASSET_WALLET_REVIEW_REQUIRED`
- `DIGITAL_ASSET_AMOUNT_LIMIT_REVIEW_REQUIRED`

기존 Audit/DB 문자열 해석을 위해 enum 삭제는 즉시 수행하지 않는다. P0-2부터 신규 실행에서 생성하지 않고 문서상
deprecated 처리한다. Amount는 risk limit이 아니라 approved amount/limit과 requested amount 비교 Reason으로 교체한다.

### P0-3/P0-4 Candidate

- Approved Transaction missing/invalid/expired
- Asset/Amount/Destination/Beneficiary mismatch
- Approved Period violation
- Required Outbound Field missing
- Required Exact violation
- Transform not allowed
- Destination mapping unresolved
- Artifact reference/digest/schema/contract gap

최종 이름과 enum은 P0-4 Canonical Freeze에서 확정하며 그 전 DA가 임의 문자열을 사용하지 않는다.

## Test Impact

### Retain As Regression

- Common Runtime Auth/Scope/Idempotency와 다른 Pack 회귀 테스트
- Subject binding 및 허용되지 않은 input fail-closed
- Vault tokenization과 exact field lineage
- Provider correlation equality와 response schema guard
- `SENT_UNKNOWN` reconciliation-first 및 즉시 resend 0
- HTTP/Connector 상태와 외부 최종 상태 분리
- Mismatch 원문 미저장, server-owned field label, `auto_retry_allowed=false`
- Flyway fresh/upgrade chain

### Replace In P0-2/P0-3

- `routesDigitalAssetPolicyViolationsToReviewBeforeConnector`의 KYC/AML/Wallet/Amount Limit cases
- Compliance Port 미구성만으로 Runtime을 BLOCK하는 E2E
- `DigitalAssetPurchaseInput` 5-field JSON contract
- Provider request의 `kycStatus`, `amlStatus`, `walletVerified`
- KYC/AML/Wallet을 mismatch severity로 사용하는 test fixture

### Add

- KYC/AML/Wallet metadata 값 변경이 Digital Asset Decision에 직접 영향을 주지 않음
- Approved Transaction을 caller가 자기신고로 주입할 수 없음
- Approved Transaction과 Outbound Request의 asset/amount/destination/beneficiary/time binding
- BLOCK/REVIEW에서 Connector invocation 0
- 기존 V16~V20 row가 V26 이후에도 조회 및 migration 가능

## P0-2 작업 대상

1. `DigitalAssetCanonicalContextBuilder`에서 Compliance Resolver의 Active 호출과 eligibility field 병합을 분리한다.
2. `DigitalAssetPolicyGate`에서 KYC/AML/Wallet/Amount Risk 판정을 제거한다.
3. Local retrieval profile, destination mapping과 provider payload에서 eligibility field를 제거한다.
4. `DigitalAssetComplianceContext*`는 삭제하지 않고 legacy/upstream metadata boundary로 격리한다.
5. KYC/AML/Wallet 값 변경 불변성 및 Connector 0/호출 조건 테스트를 추가한다.
6. 기존 Reason Code와 V19/V20 data는 보존하고 신규 실행에서 deprecated Reason을 생성하지 않는다.

P0-2에서는 아직 `ApprovedTransaction`/`OutboundRequest` 최종 DTO를 확정하지 않는다. 임시로 기존 input을 유지하더라도
eligibility 판정만 Active Path에서 제거하고, 3 Domain Contract는 P0-3에서 versioned API 계약으로 고정한다.

## Completion Gate

- [x] PR #14~#17 구성요소를 재사용/의미변경/Active Path 제거/Legacy로 분류
- [x] KYC/AML/Wallet/Amount의 현재 Decision 경로 확인
- [x] VASP/Sanctions/Customer Risk가 현재 코드에 없음을 확인
- [x] V16~V20 비파괴 additive migration 전략 확정
- [x] Reason Code retain/deprecated/candidate 목록 확정
- [x] P0-2 대상 클래스와 교체/유지 테스트 범위 확정
- [x] Common Runtime/Egress/Recovery/Lifecycle 비수정 경계 확정

