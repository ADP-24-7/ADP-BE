# BE-10 Policy Lifecycle Skeleton

이 Slice는 DA Artifact의 최종 payload를 가정하지 않고 Policy Lifecycle의 상태, 권한, 영속화 경계를 먼저 고정한다.

## State Machine

```text
DRAFT -> VALIDATED -> CANDIDATE -> REPLAY -> SHADOW
      -> APPROVED -> ACTIVE -> SUPERSEDED
                           -> REVIEW -> ROLLED_BACK
```

`ACTIVE`에서는 정상적인 새 버전 교체를 위한 `SUPERSEDED`, 검토를 위한 `REVIEW`, 긴급 `ROLLED_BACK` 전이를 허용한다.
정의되지 않은 역방향, 단계 건너뛰기, 동일 상태 전이는 차단한다. Transition reason은 자유 문자열이 아니라 target
stage에 대응하는 서버 정의 enum만 허용한다.

## Authorization

- `OPERATOR`: Artifact 생성, VALIDATED/CANDIDATE/REPLAY/SHADOW 전이
- `PRIVILEGED_OPERATOR`: APPROVED 및 전용 Current Selection command 실행
- `AUDITOR`: Institution과 Workload scope 안에서 조회만 가능
- Artifact 생성자는 자신의 Artifact를 APPROVED 또는 ACTIVE로 전이할 수 없다.

HTTP role 검사 이후 Service에서 Institution, Workload, 역할, Maker-Checker를 다시 검증한다.
`ACTIVE`에서 `REVIEW`로 가는 상태 정의는 유지하지만, Current Selection과 원자적으로 처리하는 전용 command가 없으므로
범용 Transition API에서는 차단한다.

## Persistence

- Artifact 식별자는 `(institution_id, artifact_id, artifact_version)` 복합 키로 기관별 분리한다.
- Lifecycle 조회와 전이는 SQL 단계에서 institution 및 허용 workload 조건을 강제한다.
- 지원 Pack은 `COMMON`, `AI`, `DIGITAL_ASSET`로 제한하며 `SAAS`는 영속화 전에 거부한다.

V21은 `policy.lifecycle_artifact`와 append-only `policy.lifecycle_transition_event`를 추가한다.

- Institution + Artifact ID + Version 복합 식별자
- Artifact SHA-256 Digest
- Institution, Policy Layer, Execution Pack, Workload, Purpose scope
- Current Stage와 optimistic revision
- Actor와 서버 정의 Transition Reason
- Created/Updated/Occurred timestamp

Artifact 내용 원문은 저장하지 않는다. 현재 Skeleton은 lifecycle metadata와 digest만 관리한다.

## API

- `POST /api/admin/policy-lifecycle`
- `GET /api/admin/policy-lifecycle/{artifactId}/versions/{artifactVersion}`
- `POST /api/admin/policy-lifecycle/{artifactId}/versions/{artifactVersion}/transitions`

## Deferred

- DA Versioned Artifact Bundle Loader와 schema validation
- Golden/Failure/Incident Replay 실행기
- Shadow Runtime Decision과 diff evidence
- Active Policy Snapshot selection 연결
- Rollback 대상 version 검증과 Runtime propagation
- Candidate diff의 TIGHTEN/LOOSEN/CORRECTION 분류
