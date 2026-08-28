# BE-4 Policy & Decision Core

BE-4는 DA의 1차 Policy Decision과 BE의 최종 Runtime Decision을 분리하고, 동일 Snapshot과 동일 Runtime Context에서 재현 가능한 최종 결정을 만드는 단계다.

## Goal

- DA에서 생성된 Evidence-backed 1차 Policy Decision을 BE Runtime에 직접 집행하지 않는다.
- BE는 현재 Caller, Workload, Action, Purpose, Subject Scope, Provider, Runtime DataClass, Canonical Context를 기준으로 2차 Applicability와 Runtime Guard를 수행한다.
- BE 최종 Decision은 DA 1차 판정보다 느슨해질 수 없다.
- DA `PolicyEvaluation Artifact`의 artifact identity와 BE `PolicySnapshot` identity는 서로 다르며, BE는 source artifact를 reference로만 보존한다.

## Fixed Boundaries

| Owner | Scope |
| --- | --- |
| ADP-DA | Evidence, Requirement, Control, Test, RegulatoryDataCategory, 1차 `policy_action` |
| ADP-BE | Workload, Purpose, Subject Scope, Provider, RuntimeDataClass, Canonical Context, 최종 `RuntimeDecision` |
| Shared Contract | PolicyEvaluation Artifact, Runtime Binding, DataClass Crosswalk, Processing Context Vocabulary |

## Required Contracts

### PolicyEvaluation

DA 1차 결과를 표현한다.

- `matched_policy_refs`
- `matched_rule_refs`
- `requirement_refs`
- `evidence_refs`
- `policy_action`: BE runtime policy action enum인 `ALLOW`, `TRANSFORM`, `REVIEW`, `BLOCK`
- `required_controls`
- `validation_artifact_refs`

DA handoff schema의 현재 `policy_action` 값이 `candidate_handoff`, `requires_evaluation`, `hold`, `reject`, `no_runtime_action`이면 BE의 runtime `PolicyAction`으로 직접 소비하지 않는다. 해당 값은 handoff/analysis disposition으로 해석하고, BE runtime `PolicyAction`은 별도 normalizer에서 확정해야 한다.

### PolicySnapshot

BE runtime snapshot을 표현한다.

- `policy_version`
- `snapshot_digest`
- `effective_at`
- `lifecycle_stage`
- `source_policy_evaluation_artifact_ref`
- `evaluation`

`source_policy_evaluation_artifact_ref.artifact_version/digest`는 DA artifact의 identity이고, `policy_version/snapshot_digest`는 BE runtime snapshot의 identity다. 두 identity가 같아야 한다는 invariant를 두지 않는다.

### RuntimeDecision

BE 최종 결과를 표현한다.

- `decision_id`
- `policy_action`
- `final_action`
- `runtime_reason_codes`
- `authorization_result`
- `applicability_result`
- `matched_rule_ids`
- `evidence_refs`
- `required_controls`
- `policy_version`
- `snapshot_digest`
- `runtime_context_digest`

`policy_action`과 `final_action`은 이름이 같더라도 서로 다른 타입이다. `PolicyAction`은 BE가 정규화한 1차 정책 판단이고, `FinalAction`은 runtime authorization/applicability guard까지 반영한 최종 집행 판단이다.

## Decision Monotonicity

BE는 DA의 1차 판정을 완화하지 않는다.

| DA Policy Action | Allowed BE Final Action |
| --- | --- |
| `BLOCK` | `BLOCK` |
| `REVIEW` | `REVIEW`, `BLOCK` |
| `TRANSFORM` | `TRANSFORM`, `REVIEW`, `BLOCK` |
| `ALLOW` | `ALLOW`, `TRANSFORM`, `REVIEW`, `BLOCK` |

Rule 미매칭, Rule 충돌, Unknown Data Class, 불완전 Applicability는 암묵적 `ALLOW`로 처리하지 않는다.

## Implementation Order

1. [x] Policy action enum과 runtime final action enum을 분리한다.
2. [x] `PolicyEvaluation`과 `RuntimeDecision` 타입을 추가한다.
3. [x] `PROJECT_PROVISIONAL` Policy Snapshot 모델을 만든다.
4. [x] Request 시작 시 `policy_version + snapshot_digest + effective_at`을 고정한다.
5. [x] Runtime Policy Context와 Applicability Evaluator 경계를 만든다.
6. [x] Monotonic Decision Combiner로 최종 Decision을 생성한다.
7. [x] Audit에 DA 1차 결과, BE 최종 결과, evidence/control/runtime context digest를 함께 남긴다.
8. [x] `PolicyAction`과 `FinalAction`을 실제 코드 타입으로 분리한다.
9. [x] DA source artifact identity와 BE snapshot identity를 분리한다.
10. [x] DA reference를 `ref_id/ref_type/version` typed reference로 보존한다.
11. [x] Runtime processing context를 복수형으로 모델링한다.
12. [ ] Runtime path에서 Canonical Context 조립 결과를 Applicability 입력으로 연결한다.
13. [ ] Versioned DataClass Crosswalk를 추가한다.
14. [ ] Runtime Binding Contract를 추가한다.
15. [ ] DA PolicyEvaluation Artifact Adapter/Normalizer를 추가한다.

## Tests

- 같은 Input + 같은 Snapshot은 같은 `RuntimeDecision`을 반환한다.
- Policy Version 변경 중 진행 Request는 시작 시점 Snapshot을 유지한다.
- DA `BLOCK`은 BE에서 `ALLOW`로 완화되지 않는다.
- DA `TRANSFORM`은 BE에서 `ALLOW`로 완화되지 않는다.
- Runtime 권한 부족은 DA `ALLOW`여도 BE `BLOCK`이 된다.
- Rule 미매칭, Rule 충돌, Unknown Data Class는 default allow가 되지 않는다.
- Audit에서 `policy_action`과 `final_action`을 함께 조회할 수 있다.
- Audit에서 `runtime_context_digest`, `evidence_refs`, `required_controls`를 함께 조회할 수 있다.
- DA source artifact version/digest와 BE snapshot version/digest가 달라도 snapshot 생성이 가능하다.

## Out of Scope

- Transform 세부 전략 구현은 BE-5에서 다룬다.
- Connector outbound guard는 BE-6에서 다룬다.
- Validated DA Artifact 정식 ingest와 Policy Lifecycle 승격은 BE-10에서 다룬다.
- DA repo 내부 `policy_candidates.json`을 Runtime 입력으로 직접 소비하지 않는다.
