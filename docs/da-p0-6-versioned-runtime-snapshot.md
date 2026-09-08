# DA-P0-6 Versioned Runtime Snapshot

## 목적

Digital Asset Runtime이 실행 도중 최신 설정을 다시 조회하지 않고, 실행 시작 시 선택된 승인 아티팩트와 Policy,
Destination, Runtime Control, Crosswalk 버전을 하나의 불변 Snapshot으로 고정한다.

## 실행 흐름

```text
Authorization
-> Approval Scope / Destination Profile 조회
-> Canonical Context 조립
-> ACTIVE Policy Snapshot 조회
-> Institution x Workload ACTIVE Artifact 선택
-> execution별 Runtime Snapshot 저장
-> Decision / Transform / Guard
-> Snapshot identity 재검증
-> Provider Request / Connector
```

`CANDIDATE`, `REPLAY`, `SHADOW`, `APPROVED` 상태만으로는 실행할 수 없다. `PRIVILEGED_OPERATOR`가
Maker-Checker 규칙에 따라 전용 활성화 API를 호출하여 Lifecycle을 `ACTIVE`로 전이하고 authoritative selection을
생성해야 한다.

## Snapshot 계약

`runtime.digital_asset_runtime_snapshot`은 execution당 한 건만 허용하며 다음 값을 보존한다.

- Snapshot ID와 canonical SHA-256 digest
- Digital Asset artifact ID/version/digest
- 승인된 Policy Snapshot ID/version/digest
- Destination Profile ID/version/digest
- Runtime Control version/digest
- Runtime Data Crosswalk version/digest
- Institution, Workload, Purpose와 선택 시각

Runtime Trace와 Audit Evidence Export는 같은 Snapshot identity를 반환한다. 원문 아티팩트나 요청 payload는
Snapshot과 Evidence에 저장하지 않는다.

## API

```http
POST /api/admin/digital-assets/artifacts/{artifactId}/versions/{artifactVersion}/activate
```

`PRIVILEGED_OPERATOR`만 호출할 수 있다. 이미 같은 Institution과 Workload에 ACTIVE selection이 있으면 `409`로
종료하며 기존 선택을 암묵적으로 교체하지 않는다.

```http
GET /v1/runtime/executions/{executionId}
GET /v1/runtime/executions/{executionId}/trace
GET /api/admin/audit/executions/{executionId}/evidence
```

Digital Asset 실행은 `digitalAssetRuntimeSnapshot`을 포함한다. 다른 Execution Pack은 이 값이 `null`이다.

## 실패 계약

- ACTIVE selection 없음: `DIGITAL_ASSET_ACTIVE_ARTIFACT_NOT_FOUND`
- 선택 scope/digest/runtime metadata 불일치: `DIGITAL_ASSET_RUNTIME_SNAPSHOT_INVALID`
- 동시 활성화 충돌: `DIGITAL_ASSET_ACTIVE_ARTIFACT_CONFLICT`
- execution snapshot 중복: `DIGITAL_ASSET_RUNTIME_SNAPSHOT_CONFLICT`

모든 선택/검증 실패는 Connector 전에 Runtime `BLOCKED`로 수렴한다.

## Replay와 Recovery

Idempotency replay는 새 execution을 만들지 않으므로 기존 execution의 Snapshot을 그대로 조회한다. `SENT_UNKNOWN`
Recovery도 Provider 상태만 조회하며 ACTIVE Artifact, Policy, Destination을 다시 선택하지 않는다. E2E 테스트는 Recovery
전후 Snapshot digest가 동일함을 검증한다.

## 검증

- ACTIVE Policy가 아닌 경우 Snapshot 저장 전 차단
- ACTIVE Artifact가 없으면 저장 전 차단
- Artifact/Policy/Destination/Control/Crosswalk identity 전체 저장
- V28 schema와 Institution x Workload 단일 ACTIVE PK 검증
- Digital Asset E2E의 Snapshot DB, Runtime Trace, Audit Evidence 일치 검증
- `SENT_UNKNOWN` Recovery 전후 Snapshot digest 불변 검증

