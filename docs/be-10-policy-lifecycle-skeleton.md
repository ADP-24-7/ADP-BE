# BE-10 Policy Lifecycle Skeleton

이 Slice는 DA Artifact의 최종 payload를 가정하지 않고 Policy Lifecycle의 상태, 권한, 영속화 경계를 먼저 고정한다.

## State Machine

```text
DRAFT -> VALIDATED -> CANDIDATE -> REPLAY -> SHADOW
      -> APPROVED -> ACTIVE -> REVIEW -> ROLLED_BACK
```

`ACTIVE`에서는 긴급 `ROLLED_BACK` 전이도 허용한다. 정의되지 않은 역방향, 단계 건너뛰기, 동일 상태 전이는 차단한다. Transition reason은 자유 문자열이 아니라 target stage에 대응하는 서버 정의 enum만 허용한다.

## Authorization

- `OPERATOR`: Artifact 생성, VALIDATED/CANDIDATE/REPLAY/SHADOW/REVIEW 전이
- `PRIVILEGED_OPERATOR`: APPROVED/ACTIVE/ROLLED_BACK 전이
- `AUDITOR`: Institution과 Workload scope 안에서 조회만 가능
- Artifact 생성자는 자신의 Artifact를 APPROVED 또는 ACTIVE로 전이할 수 없다.

HTTP role 검사 이후 Service에서 Institution, Workload, 역할, Maker-Checker를 다시 검증한다.

## Persistence

V21은 `policy.lifecycle_artifact`와 append-only `policy.lifecycle_transition_event`를 추가한다.

- Artifact ID + Version 복합 식별자
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
