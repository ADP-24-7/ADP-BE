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

Pack Policy Gate는 Baseline Decision을 완화하지 않는다. Profile 위반이 없으면 기존 `TRANSFORM`을 유지하고, 조건 위반은 `REVIEW`, Profile scope 또는 effective time 불일치는 `BLOCK`으로 강화한다.

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
- Profile Evaluation Result
- Reason Codes
- Evaluated At

Decision identity는 Baseline Decision ID, Profile Digest, 강화된 Final Action, Profile Reason Codes를 포함해 결정적으로 생성한다.

## Review Reasons

- `DIGITAL_ASSET_KYC_REVIEW_REQUIRED`
- `DIGITAL_ASSET_AML_REVIEW_REQUIRED`
- `DIGITAL_ASSET_WALLET_REVIEW_REQUIRED`
- `DIGITAL_ASSET_AMOUNT_LIMIT_REVIEW_REQUIRED`

Review 결과에서는 Transform까지 privacy-safe하게 수행할 수 있지만 Connector는 호출하지 않는다.

## Deferred

- DB-backed Profile Loader와 Lifecycle
- 기관별·자산별 Amount Limit
- 실제 KYC/AML Provider binding
- Policy Candidate, Approval, Shadow, Active, Rollback
