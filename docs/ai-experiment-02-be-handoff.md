# AI Experiment 02 — BE Handoff

Status: `E2_POLICY_REQUIREMENT_VALIDATED`

External execution: `PENDING_EXTERNAL_EXECUTION`

Provider governance: `PROVIDER_GOVERNANCE_BLOCKED`

## E2가 BE에 넘기는 것

E2는 `customer_summary` / `CUSTOMER_SUPPORT` 업무에 대해 다음의 frozen context를 BE에 전달한다.

- Workload / Purpose / business domain / subject scope / requester role
- 10 Regulatory Sources와 39 Regulatory Requirements
- 역할과 적용 위치가 분류된 Internal Controls 19건
- requirement별 Applicability
- 3 case × 20 field = 60 Field Requirements
- Transform Intent와 Utility Requirement
- NVIDIA processing region, retention, model-improvement reuse가 미해소된 Provider Governance State
- content-addressed Evidence Digest
- E3 input contract `E2_TO_E3_TRANSFORM_REQUIREMENTS.json`

E3 handoff contract version은 `1.1.1`, digest는 `sha256:899cf31a920c1363cfb21b9c7d6f3204819222935bcbb9008a01ccbf9a8ba73e`다. `account.balance`와 `transaction.amount`는 `REQUIRED_EXACT`, `KEEP_ONLY`, GENERALIZE prohibited로 고정된다. Provider call authorization은 `false`다.

Canonical source는 ADP-DA repository의 다음 repository-relative 파일이다.

- `02_ai/artifacts/experiment_03/E2_TO_E3_TRANSFORM_REQUIREMENTS.json`
- `02_ai/docs/EXPERIMENT_02_REGULATORY_RUNTIME_VALIDATION.md`

## BE 사용 계약

BE는 위 결론을 다시 분석하거나 Runtime 의미를 재정의하지 않는다. 기존 Runtime에서 다음 reference와 evidence projection에 사용한다.

- Policy reference
- Transform requirement reference
- Governance API
- Evaluation Bundle
- Audit Trace와 Stage Timing
- FE Controller API

Canonical Runtime 순서는 다음과 같다.

```text
Authentication
→ Authorization
→ Workload / Purpose / Subject / Action
→ Policy
→ Retrieval
→ Transform
→ Outbound Guard
→ Provider
→ Response Guard
→ Controlled Delivery
→ Evaluation Bundle
→ Stage Timing / Trace
```

Authorization와 Policy decision은 독립 상태다. Outbound Guard 통과는 Provider governance 승인이 아니며 Provider 성공도 Response Guard 또는 Delivery 성공을 의미하지 않는다.

## Read-only Governance API

BE governance projection은 E2 handoff digest, requirement version, E2 validation status, provider governance status, external execution status, provider authorization, field controls와 requirement enforcement gaps를 반환한다.

Provider가 실행되지 않은 latency, token, response, response finding과 cross-model result는 만들지 않는다. 기존 schema convention에 따라 `NOT_EXECUTED`, `NOT_AVAILABLE` 또는 `null`로 유지한다.

FE는 이 API를 Security / AI Governance Controller Console에서 read-only로 표시한다. Prompt/Chat/Run/Model selection, 임의 score 또는 model ranking을 추가하지 않는다.

## Boundary

이 handoff는 새 Runtime architecture, E3 execution 또는 external Provider 승인 요청이 아니다. E2가 확정한 requirement를 기존 BE Runtime/API/Bundle/Audit contract에 연결하는 경계 문서다.

Repository와 대상 E2 DB는 V50이다. 별도 `postgres-test`의 V41 checksum mismatch는 Known Test Environment Gap이며 Flyway repair나 기존 migration 변경 대상으로 취급하지 않는다.
