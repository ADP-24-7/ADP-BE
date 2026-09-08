# BE-10 Policy Replay / Shadow Diff Evidence Core

## 목적

Lifecycle `REPLAY` Candidate와 동일 Scope의 현재 `ACTIVE` Artifact를 같은 server-owned Evaluation Case로 평가하고,
결과 차이를 raw input 없이 Evidence로 남긴다. 이 Slice는 Connector나 외부 Action을 호출하지 않는다.

## 처리 흐름

```text
Operator request (Candidate ID/Version + Evaluation Case ID)
-> Institution/Workload scope Candidate 조회
-> Candidate stage == REPLAY 검증
-> Institution + Policy Layer + Pack + Workload + Purpose ACTIVE baseline 단일 조회
-> server-owned PolicyShadowEvaluator로 동일 Case 각각 평가
-> Case Version/Input Digest 동일성 검증
-> Outcome canonical digest와 typed diff 계산
-> append-only Shadow Evidence 저장
```

Caller는 action, reason, transform, destination 결과를 제출할 수 없다. 평가 결과는 `PolicyShadowEvaluator`가 생성한다.
운영 Adapter가 미구성된 환경은 `POLICY_SHADOW_EVALUATOR_NOT_CONFIGURED`로 fail-closed한다. Local fixture는
`GOLDEN_ALLOW`, `FAILURE_BLOCK` case만 제공하며 실제 운영 Policy 평가 결과가 아니다.

## Diff 계약

- `FINAL_ACTION`
- `REASON_CODES`
- `REQUIRED_CONTROLS`
- `TRANSFORM_STRATEGY`
- `DESTINATION_PROFILE`

Evidence에는 Artifact identity/version/digest, Candidate revision, Evaluation Case identity/version, input digest,
baseline/candidate outcome digest, diff enum, actor와 시각만 저장한다. Runtime 원문 payload, subject, credential은 저장하지 않는다.

## API

`POST /api/admin/policy-lifecycle/{artifactId}/versions/{artifactVersion}/shadow-evaluations`

```json
{
  "evaluationCaseId": "GOLDEN_ALLOW"
}
```

## DB

V33 `policy.shadow_evaluation_evidence`는 Candidate revision과 Case version 조합을 유일하게 고정한다. ACTIVE와 Candidate는
모두 Artifact digest·workload·purpose를 포함한 Lifecycle 복합 FK로 결속한다. `MATCH`는 빈 diff, `DIFF`는 하나 이상의
typed diff를 DB constraint로 강제한다.

## 현재 범위

이번 Slice는 Shadow 실행과 Diff Evidence Core다. 다음 BE-10 Slice에서 Evidence Gate를 Lifecycle `SHADOW -> APPROVED`와
연결하고, Active Runtime Selection 및 Rollback Propagation을 완성한다.
