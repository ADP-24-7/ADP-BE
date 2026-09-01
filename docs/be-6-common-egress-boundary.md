# BE-6 Common Egress Boundary

BE-6은 AI, Digital Asset, SaaS Pack이 공유하는 외부 전송 경계를 고정한다.

## Scope

- `TransformResult`를 Connector에 직접 전달하지 않는다.
- Runtime은 `OutboundCandidatePayload`를 조립한 뒤 Outbound Guard를 통과한 payload만 Connector에 전달한다.
- BLOCK, REVIEW decision은 Connector Port를 호출하지 않는다.
- Connector 실행 후 Response Guard Port를 반드시 통과한다.
- Runtime trace는 Decision, Transform, Outbound Guard, Connector, Response Guard를 같은 executionId로 연결한다.
- Authorization 전에는 Destination Profile을 포함한 내부 policy/config data를 조회하지 않는다.

## Baseline Contracts

- `ExecutionPackType`: `COMMON`, `AI`, `DIGITAL_ASSET`, `SAAS`
- `DestinationProfile`: provider, pack type, schema version, workload/purpose allowlist
- Runtime request는 `destinationProfileId`를 받고, provider profile은 pinned Destination Profile에서 파생한다.
- Request 시작 시점의 Destination Profile id/version/digest를 `runtime.runtime_execution`에 고정한다.
- Runtime status는 `EGRESSING`/`COMPLETED`까지만 표현하고, Connector/Response Guard 세부 상태는 별도 external status 컬럼에 기록한다.
- Connector external status baseline은 `NOT_SENT`, `SENT_UNKNOWN`, `ACKNOWLEDGED`, `COMPLETED`, `FAILED`로 둔다.
- `DestinationBinding`: workload/purpose pair allowlist
- `DestinationFieldContract`: field별 obligation, required, exact transmission 허용 여부
- `FieldObligation`: `PROHIBITED`, `MINIMIZABLE`, `PSEUDONYMIZABLE`, `CONDITIONAL_EXACT`, `REQUIRED_EXACT`
- `FieldTreatment`: `REMOVED`, `TRANSFORMED`, `KEEP_EXACT_PROTECTED`
- `OutboundCandidatePayload`: privacy-safe outbound candidate identity, schema version, payload digest, fields
- `candidatePayloadDigest`: 실제 provider JSON body digest가 아니라 Outbound Candidate metadata digest
- `ResponseGuardPort`: connector response를 외부 상태 확정 전에 검사하는 Port
- 기본 Response Guard는 실제 검증 없이 `PASSED`를 만들지 않고 `NOT_EVALUATED`를 반환한다.
- Local fixture Response Guard만 fake response schema/digest를 검증한 뒤 `PASSED`를 반환한다.
- Secret 차단은 Guard 내부 regex가 아니라 `OutboundSensitiveFindingDetector`가 생성한 finding을 Guard가 소비하는 구조다.

## Persistence

- `egress.destination_profile`
- `runtime.outbound_candidate`
- `runtime.connector_execution`
- `runtime.response_guard_result`
- `runtime.runtime_execution` egress/connector/response guard trace columns

## Guard Rules

- Destination Profile이 workload/purpose를 허용하지 않으면 reject한다.
- Destination Profile이 request 시작 시점 기준 effective window 밖이면 reject한다.
- `ALLOW`, `TRANSFORM`이 아닌 final action은 egress 불가다.
- `UNKNOWN` 또는 `PROHIBITED` field가 payload에 남아 있으면 reject한다.
- `REMOVE` 처리된 field가 payload에 남아 있으면 reject한다.
- 민감 identifier/financial field의 raw `KEEP_EXACT_PROTECTED`는 baseline에서 reject한다.
- digest 없는 outbound field는 reject한다.
- payload schema version과 destination profile schema version이 다르면 reject한다.
- payload pack type과 destination profile pack type이 다르면 reject한다.
- required field contract가 payload에 없으면 reject한다.
- secret, credential, private key, seed, token pattern이 exact payload에 남아 있으면 reject한다.
- guard reject는 `runtime.outbound_candidate`에 `REJECTED`로 남기고 Runtime status는 `BLOCKED`로 기록한다.
- response metadata가 없으면 Response Guard는 `PASSED`가 아니라 `NOT_EVALUATED`로 기록한다.

## Metrics

- `egress.guard.total{result,reason}`
- `egress.guard.duration`
- `destination.profile.lookup.total{result}`
- `connector.execution.total{status}`
- `response.guard.total{result,reason}`

## Deferred

- Pack별 schema mapper
- DB-backed Destination Profile loader
- AI response leakage detector
- BE-3 Detection finding과 Outbound Sensitive Finding의 직접 연결
- Digital Asset Required Exact field matrix
- SaaS tenant/subprocessor/retention guard
- 실제 provider payload canonical JSON digest
- 실제 provider response canonical digest
- 실제 response leakage detection은 Pack별 Response Guard adapter에서 구현한다.
