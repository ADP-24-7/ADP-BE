# BE-8 Digital Asset Policy Gate

이 Slice는 Digital Asset 구매 요청의 KYC, AML, Wallet Verification, Amount Limit을 외부 Connector 실행 전에 평가한다.

## Runtime Order

```text
Canonical Context
-> Baseline Runtime Decision
-> Execution Pack Policy Gate
-> Final Runtime Decision Persistence
-> Transform
-> Policy Harness / Outbound Guard
-> Connector
```

Pack Policy Gate는 Baseline Decision을 완화하지 않는다. Profile 위반이 없으면 기존 `TRANSFORM`을 유지하고, 조건 위반은 `REVIEW`, Profile scope 또는 effective time 불일치는 `BLOCK`으로 강화한다. Digital Asset처럼 필수로 지정된 Pack의 Gate Bean이 없을 때도 `EXECUTION_PACK_POLICY_GATE_NOT_CONFIGURED`로 차단한다.

## Compliance Trust Boundary

요청의 `kycStatus`, `amlStatus`, `walletVerified` 값은 정책 판단 근거로 신뢰하지 않는다. Canonical Context Builder가 `DigitalAssetComplianceContextPort`에서 고객, 계좌, 지갑 기준의 authoritative assertion을 조회해 해당 필드를 덮어쓰며, Gate는 Profile에 고정된 assertion source/version과 일치하는지도 확인한다.

Local fixture의 assertion source는 `BANK_COMPLIANCE_FIXTURE`, version은 `1.0.0`이다. 운영 환경에서는 이 Port를 금융기관 KYC/AML 및 Wallet 검증 시스템 Adapter로 교체해야 한다.

## Local Profile

- Profile ID: `digital-asset-policy/local/1`
- Version: `1.0.0`
- KYC 허용 상태: `VERIFIED`
- AML 허용 상태: `PASSED`
- Wallet Verification: 필수
- Amount Limit: `10,000,000`
- Effective At: `2026-01-01T00:00:00Z`

이 값은 `PROJECT_PROVISIONAL` Local fixture이며 실제 금융기관 정책이 아니다.

## Evidence

V19 `runtime.execution_pack_policy_evaluation`은 실행별 다음 값을 보존한다.

- Pack Type
- Profile ID, Version, Digest
- Baseline Action, Profile Action, Final Action
- Reason Codes
- Compliance Assertion Source, Version, Evidence Digest
- Evaluated At

Decision identity는 Baseline Decision ID, Profile Digest, 강화된 Final Action, Profile Reason Codes를 포함해 결정적으로 생성한다.

## Review Reasons

- `DIGITAL_ASSET_KYC_REVIEW_REQUIRED`
- `DIGITAL_ASSET_AML_REVIEW_REQUIRED`
- `DIGITAL_ASSET_WALLET_REVIEW_REQUIRED`
- `DIGITAL_ASSET_AMOUNT_LIMIT_REVIEW_REQUIRED`
- `EXECUTION_PACK_POLICY_GATE_NOT_CONFIGURED`

Review 결과에서는 Transform까지 privacy-safe하게 수행할 수 있지만 Connector는 호출하지 않는다.

## Deferred

- DB-backed Profile Loader와 Lifecycle
- 기관별·자산별 Amount Limit
- 운영 KYC/AML/Wallet Provider Adapter
- Policy Candidate, Approval, Shadow, Active, Rollback
