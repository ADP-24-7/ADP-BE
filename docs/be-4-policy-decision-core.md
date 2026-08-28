# BE-4 Policy & Decision Core

BE-4는 DA의 1차 Policy Decision과 BE의 최종 Runtime Decision을 분리하고, 동일 Snapshot과 동일 Runtime Context에서 재현 가능한 최종 결정을 만드는 단계다.

## Goal

- DA에서 생성된 Evidence-backed 1차 Policy Decision을 BE Runtime에 직접 집행하지 않는다.
- BE는 현재 Caller, Workload, Action, Purpose, Subject Scope, Provider, Runtime DataClass, Canonical Context를 기준으로 2차 Applicability와 Runtime Guard를 수행한다.
- BE 최종 Decision은 DA 1차 판정보다 느슨해질 수 없다.

## Fixed Boundaries

| Owner | Scope |
| --- | --- |
| ADP-DA | Evidence, Requirement, Control, Test, RegulatoryDataCategory, 1차 `policy_action` |
| ADP-BE | Workload, Purpose, Subject Scope, Provider, RuntimeDataClass, Canonical Context, 최종 `RuntimeDecision` |
| Shared Contract | PolicyEvaluation Artifact, Runtime Binding, DataClass Crosswalk, Processing Context Vocabulary |

## Required Contracts

### PolicyEvaluation

DA 1차 결과를 표현한다.

- `matched_rule_ids`
- `evidence_refs`
- `policy_action`
- `required_controls`
- `policy_version`
- `snapshot_digest`

### RuntimeDecision

BE 최종 결과를 표현한다.

- `decision_id`
- `final_action`
- `runtime_reason_codes`
- `authorization_result`
- `applicability_result`
- `matched_rule_ids`
- `policy_version`
- `snapshot_digest`

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
5. [x] Canonical Runtime Context 기반 Applicability 결과를 만든다.
6. [x] Monotonic Decision Combiner로 최종 Decision을 생성한다.
7. [x] Audit에 DA 1차 결과와 BE 최종 결과를 함께 남긴다.
8. [ ] Versioned DataClass Crosswalk를 추가한다.
9. [ ] Runtime Binding Contract를 추가한다.
10. [ ] DA PolicyEvaluation Artifact Adapter/Normalizer를 추가한다.

## Tests

- 같은 Input + 같은 Snapshot은 같은 `RuntimeDecision`을 반환한다.
- Policy Version 변경 중 진행 Request는 시작 시점 Snapshot을 유지한다.
- DA `BLOCK`은 BE에서 `ALLOW`로 완화되지 않는다.
- DA `TRANSFORM`은 BE에서 `ALLOW`로 완화되지 않는다.
- Runtime 권한 부족은 DA `ALLOW`여도 BE `BLOCK`이 된다.
- Rule 미매칭, Rule 충돌, Unknown Data Class는 default allow가 되지 않는다.
- Audit에서 `policy_action`과 `final_action`을 함께 조회할 수 있다.

## Out of Scope

- Transform 세부 전략 구현은 BE-5에서 다룬다.
- Connector outbound guard는 BE-6에서 다룬다.
- Validated DA Artifact 정식 ingest와 Policy Lifecycle 승격은 BE-10에서 다룬다.
- DA repo 내부 `policy_candidates.json`을 Runtime 입력으로 직접 소비하지 않는다.
