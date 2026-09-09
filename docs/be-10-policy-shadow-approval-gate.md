# BE-10 Policy Shadow Approval Gate

## 목적

Lifecycle `SHADOW` Candidate를 `APPROVED`로 전환할 때 PR #34의 Shadow Evidence를 필수 승인 근거로 결속한다.
승인 전용 API만 이 전이를 수행하며 범용 Lifecycle transition API를 통한 우회는 차단한다.

## 승인 흐름

```text
Privileged checker + Candidate ID/Version + Shadow Evidence ID
-> Institution/Workload scope Candidate 조회
-> Maker-Checker와 SHADOW stage 검증
-> 승인 정책이 요구하는 Candidate revision + Evaluation Case ID/Version 범위의 최신 Shadow Evidence 조회
-> server-owned Approval Policy 검증
-> Lifecycle scope advisory lock
-> Candidate identity/revision과 ACTIVE baseline 재검증
-> Candidate SHADOW -> APPROVED CAS
-> Shadow binding을 포함한 Transition Evidence 저장
```

## 승인 정책

현재 `PolicyShadowApprovalPolicy` `policy-shadow-approval/1.0.0`은 `GOLDEN_ALLOW/1.0.0` Case의 `MATCH` Evidence만
승인 가능하다. `DIFF`는 변화의 강화/완화 의미를 아직 안전하게
분류하지 않으므로 `POLICY_SHADOW_DIFF_NOT_APPROVABLE`로 fail-closed한다. Caller가 diff 허용 여부를 제출할 수 없다.

Shadow Evidence는 Candidate가 `REPLAY` revision에서 생성되고, 이후 `REPLAY -> SHADOW` 전환으로 revision이 한 번 증가한다.
따라서 승인 시 현재 Candidate revision은 Evidence의 `candidate_revision + 1`이어야 한다. ACTIVE baseline identity/digest가
평가 이후 변경되었거나 Evidence가 다른 Institution/Workload/Candidate에 속하면 승인하지 않는다.

## API

`POST /api/admin/policy-lifecycle/{artifactId}/versions/{artifactVersion}/approvals`

```json
{
  "shadowEvaluationId": "shadow_..."
}
```

필요 역할은 `PRIVILEGED_OPERATOR`이며 Candidate 생성자와 승인자는 달라야 한다.

## Transition Evidence

V34부터 신규 승인 이벤트는 다음 값을 함께 저장한다.

- `approval_gate_version = SHADOW_EVIDENCE_V1`
- Shadow Evidence ID
- Candidate shadow revision
- Baseline Artifact ID/Version/Digest
- Evaluation Case ID/Version
- Shadow result
- Approval Policy Version

복합 FK는 Transition의 Institution, Candidate identity/digest와 Shadow Evidence binding integrity를 DB에서 보장한다.
Evidence의 approvability와 Approval Policy Version의 의미는 server-owned application policy가 검증한다. V34 이전
승인 이력은 의미를 변경하지 않고 `LEGACY_UNBOUND`로 구분한다.

## 현재 범위

이번 Slice는 `SHADOW -> APPROVED` Evidence Gate까지 완료한다. `APPROVED -> ACTIVE` Current Selection과 Rollback
Propagation은 다음 BE-10 Slice에서 구현한다.
