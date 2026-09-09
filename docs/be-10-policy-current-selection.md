# BE-10 Policy Current Selection / Rollback

## 목적

Shadow Evidence에 결속되어 `APPROVED`가 된 Policy를 Institution, Execution Pack, Workload, Purpose 단위의
server-owned Current Selection으로 승격한다. 활성화와 롤백은 Lifecycle 상태, Current Selection, append-only Evidence를
하나의 트랜잭션에서 변경한다.

## API

- `POST /api/admin/policy-lifecycle/{artifactId}/versions/{artifactVersion}/activations`
- `POST /api/admin/policy-lifecycle/{artifactId}/versions/{artifactVersion}/rollbacks`
- `GET /api/admin/policy-lifecycle/current-selection`

활성화 요청은 `expectedArtifactRevision`과 `expectedSelectionRevision`, 롤백 요청은 `expectedTargetRevision`과
`expectedSelectionRevision`을 필수로 받는다. Digest, Scope, 다음 revision은 서버가 authoritative row에서 결정한다.

## 활성화와 롤백

활성화는 `APPROVED`만 허용하고 승인 Evidence가 결속한 Baseline이 현재 `ACTIVE`와 같은지 다시 검증한다. 이후 기존
Current Selection의 `ACTIVE`를 `SUPERSEDED`로 바꾼 뒤 새 버전을 `ACTIVE`로 만든다.
롤백 대상은 `APPROVED`와 `ACTIVE` 전환 이력이 모두 존재하는 `SUPERSEDED` 버전으로 제한한다. 현재 `ACTIVE`는
`ROLLED_BACK`, 복원 대상은 `ACTIVE`가 되며 선택 revision은 단조 증가한다.

Current Selection scope advisory lock은 Policy Layer를 제외한 실제 PK와 동일한
`Institution + Pack + Workload + Purpose`를 사용한다. 서로 다른 Layer 후보도 같은 Current Selection을 놓고 경쟁하므로
동일 lock과 fencing revision으로 직렬화한다.

## Runtime Snapshot 경계

Local Runtime Adapter는 Current Selection이 존재하면 선택된 Artifact identity/version/digest/revision으로 PolicySnapshot을
생성한다. `runtime.policy_evaluation`에는 source Artifact와 함께 Current Selection revision, Artifact revision, Policy Layer를
저장한다. 실행 도중 Current Selection이 바뀌어도 이미 resolve된 Snapshot 객체와 영속 Evidence는 수정하지 않는다.

운영 환경의 실제 Artifact 평가 Adapter는 별도 구성 대상이며, 미구성 환경은 기존과 같이 fail-closed한다. Digital Asset
Bundle 활성화는 추가 Runtime Control/Crosswalk 결속이 필요하므로 기존 전용 `/api/admin/digital-assets/artifacts/.../activate`
경계를 유지하고 범용 Policy activation API에서는 처리하지 않는다.

## DB

V35는 다음을 추가한다.

- `policy.current_selection`: Scope당 한 행인 authoritative pointer
- `policy.current_selection_event`: activation/rollback append-only Evidence
- `runtime.policy_evaluation` Current Selection pinning metadata
- Rollback 복원을 위한 `ROLLBACK_RESTORED` typed transition reason

V34 이전 `ACTIVE`가 여러 개일 수 있어 Migration에서 임의 backfill하지 않는다. 기존 이력은 그대로 보존하고 첫 명시적
activation에서 단일 Current Selection을 확정하며, 기존 ACTIVE가 모호하면 fail-closed한다.
