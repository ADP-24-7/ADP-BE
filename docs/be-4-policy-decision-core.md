# BE-4 Policy & Decision Core

BE-4는 DA Handoff Artifact와 BE Runtime Policy를 분리하고, policy evaluation 시점에 선택된 Snapshot과 동일 Runtime Context에서 재현 가능한 최종 결정을 만드는 단계다.

## Goal

- DA에서 생성된 Evidence-backed Handoff Artifact를 BE Runtime에 직접 집행하지 않는다.
- BE는 현재 Caller, Workload, Action, Purpose, Subject Scope, Provider, Runtime DataClass, Canonical Context, Runtime Input Digest를 기준으로 2차 Applicability와 Runtime Guard를 수행한다.
- BE 최종 Decision은 BE-normalized `PolicyAction`보다 느슨해질 수 없다.
- DA `PolicyEvaluation Artifact`의 artifact identity와 BE `PolicySnapshot` identity는 서로 다르며, BE는 source artifact를 reference로만 보존한다.

## Fixed Boundaries

| Owner | Scope |
| --- | --- |
| ADP-DA | Evidence, Requirement, Control, Test, RegulatoryDataCategory, Handoff Disposition |
| ADP-BE | Workload, Purpose, Subject Scope, Provider, RuntimeDataClass, Canonical Context, Runtime Input Digest, BE-normalized `PolicyAction`, 최종 `RuntimeDecision` |
| Shared Contract | PolicyEvaluation Artifact, Runtime Binding, DataClass Crosswalk, Processing Context Vocabulary |

## Required Contracts

### PolicyEvaluation

BE가 DA Handoff Artifact를 정규화한 runtime policy evaluation을 표현한다.

- `matched_policy_refs`
- `matched_rule_refs`
- `requirement_refs`
- `evidence_refs`
- `policy_action`: BE runtime policy action enum인 `ALLOW`, `TRANSFORM`, `REVIEW`, `BLOCK`
- `required_controls`
- `validation_artifact_refs`
- `applicability_spec`
  - `analysis_status`
  - `applicability_status`
  - `processing_contexts`
  - `regulatory_data_categories`
  - `runtime_binding`

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
- `source_policy_evaluation_artifact_ref`
  - `artifact_id`
  - `artifact_version`
  - `artifact_digest.algorithm`
  - `artifact_digest.value`

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
4. [x] Policy evaluation 시점에 선택된 `policy_version + snapshot_digest + effective_at`을 해당 Decision 동안 불변으로 유지한다.
5. [x] Runtime Policy Context와 Applicability Evaluator 경계를 만든다.
6. [x] Monotonic Decision Combiner로 최종 Decision을 생성한다.
7. [x] Audit에 DA 1차 결과, BE 최종 결과, evidence/control/runtime context digest를 함께 남긴다.
8. [x] `PolicyAction`과 `FinalAction`을 실제 코드 타입으로 분리한다.
9. [x] DA source artifact identity와 BE snapshot identity를 분리한다.
10. [x] DA reference를 `ref_id/ref_type/version` typed reference로 보존한다.
11. [x] Runtime processing context를 복수형으로 모델링한다.
12. [x] Policy-side `analysis_status`, `applicability`, `processing_contexts`, `regulatory_data_categories`, `runtime_binding`을 normalized model에 보존한다.
13. [x] Applicability Evaluator에서 workload, purpose, processing context, runtime data class binding을 비교한다.
14. [x] Runtime processing context가 필요한데 비어 있으면 `NOT_APPLICABLE`이 아니라 `INCOMPLETE`로 판정한다.
15. [x] RuntimeDecision/Audit에 DA source artifact version/digest를 보존한다.
16. [x] `PolicySelectionContext`로 workload/purpose/provider/processing/data class 기반 Snapshot 선택 경계를 만든다.
17. [x] `/v1/runtime/executions`에서 Controlled Retrieval -> Canonical Context -> RuntimePolicyContext -> Decision 경로를 연결한다.
18. [x] Runtime Execution, Policy Evaluation, Runtime Decision 최소 persistence를 추가한다.
19. [x] DA Handoff Disposition과 BE `PolicyAction`을 분리하는 Adapter/Normalizer 경계를 추가한다.
20. [x] Versioned RuntimeDataClass Crosswalk Port와 provisional adapter를 추가한다.
21. [x] `/v1/runtime/executions` Controller/Service는 mock flag와 분리하고 provider/policy fixture adapter만 환경 조건으로 분리한다.
22. [x] Runtime request `input`은 raw 저장 없이 canonical SHA-256 `input_digest`로 저장하고 `runtime_context_digest`에 포함한다.
23. [x] Provisional Policy Snapshot fixture는 고정 scope lookup으로 선택하고 `effective_at`을 고정한다.
24. [x] Runtime Execution 실패 시 `FAILED` 상태로 전환한다.
25. [x] `/trace`는 flat metadata 중복이 아니라 BE-4 stage event 형태로 반환한다.
26. [x] Runtime/Policy 최소 persistence를 `runtime` / `governance` schema로 분리한다.
27. [x] DA Handoff Normalizer 앞단에 schema/status/reference/digest field validator를 추가한다.
28. [x] Runtime Execution GET/trace 조회에 workload object-level authorization을 적용한다.
29. [x] `(workload_id, idempotency_key)` unique constraint로 duplicate runtime execution을 차단한다.
30. [x] Runtime request validation size를 DB varchar contract와 맞춘다.
31. [ ] DA 실제 PolicyEvaluation Artifact 파일 ingest endpoint/loader 추가
32. [ ] DA Workload/Purpose Binding Contract 파일 loader 추가
33. [ ] DA Crosswalk Contract 파일 loader 추가
34. [ ] Policy 교체 시 code path 변경 없이 fixture/snapshot 교체 검증
35. [ ] Request ingress 시점 policy catalog revision pinning은 Policy Lifecycle 단계에서 구현한다.

## Tests

- 같은 Input + 같은 Snapshot은 같은 `RuntimeDecision`을 반환한다.
- Policy evaluation 시점에 선택된 Snapshot은 해당 Decision 동안 유지된다.
- DA `BLOCK`은 BE에서 `ALLOW`로 완화되지 않는다.
- DA `TRANSFORM`은 BE에서 `ALLOW`로 완화되지 않는다.
- Runtime 권한 부족은 DA `ALLOW`여도 BE `BLOCK`이 된다.
- Rule 미매칭, Rule 충돌, Unknown Data Class는 default allow가 되지 않는다.
- Audit에서 `policy_action`과 `final_action`을 함께 조회할 수 있다.
- Audit에서 `runtime_context_digest`, `evidence_refs`, `required_controls`를 함께 조회할 수 있다.
- DA source artifact version/digest와 BE snapshot version/digest가 달라도 snapshot 생성이 가능하다.
- Applicability Evaluator는 workload, purpose, processing context, runtime data class가 일치하지 않으면 `APPLICABLE`을 반환하지 않는다.
- Runtime processing context가 아직 결정되지 않은 경우 `INCOMPLETE`로 audit된다.
- `/v1/runtime/executions`는 Retrieval과 Canonical Context 조립 후 RuntimeDecision을 생성한다.
- Runtime execution, policy evaluation, runtime decision을 audit event와 별도 테이블에 저장한다.
- Runtime request input이 다르면 `input_digest`와 `runtime_context_digest`가 달라진다.
- `adp.mock-runtime.enabled=false`에서도 `/v1/runtime/executions` API는 존재하며, unconfigured policy/provider adapter는 fail-safe REVIEW/NOT_EXECUTED로 동작한다.
- `/v1/runtime/executions/{id}/trace`는 RECEIVED, AUTHORIZATION, RETRIEVAL, CANONICAL_CONTEXT, DECISION stage를 반환한다.
- Runtime Execution GET/trace는 principal workload scope가 맞지 않으면 403을 반환한다.
- 동일 workload에서 같은 idempotency key를 재사용하면 409를 반환한다.

## Out of Scope

- Transform 세부 전략 구현은 BE-5에서 다룬다.
- Connector outbound guard는 BE-6에서 다룬다.
- Validated DA Artifact 정식 승격과 Policy Lifecycle 운영은 BE-10에서 다룬다.
- DA repo 내부 `policy_candidates.json`을 Runtime 입력으로 직접 소비하지 않는다.
