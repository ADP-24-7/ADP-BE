# BE-9A Idempotency Core

BE-9A는 DA Artifact와 Pack별 외부 상태에 의존하지 않는 Runtime 중복 실행 방지 기준을 고정한다. 이 Slice 완료는 BE-9 Consistency & Recovery 전체 완료를 의미하지 않는다.

## Namespace

Idempotency 소유권은 다음 복합 키로 구분한다.

```text
institution_id + workload_id + idempotency_key
```

서로 다른 Institution 또는 Workload는 같은 Key를 독립적으로 사용할 수 있다. `request_id`와 `trace_id`는 재시도마다 변경될 수 있으므로 namespace와 request hash에 포함하지 않는다.

## Canonical Request Hash

SHA-256 request hash에는 다음 실행 의미를 포함한다.

- Institution
- Approval Reference
- Workload와 Purpose
- Subject Scope
- Destination Profile ID
- 정렬된 Processing Context
- key 순서가 정규화된 Input JSON

원문 요청은 저장하지 않고 hash만 `runtime.runtime_execution`에 저장한다.

## Resolution

| 조건 | 결과 |
| --- | --- |
| 새 namespace | 새 Runtime 실행 |
| 같은 namespace + 같은 hash + 완료 상태 | 기존 execution replay |
| 같은 namespace + 같은 hash + 진행 상태 | `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` |
| 같은 namespace + 다른 hash | `409 IDEMPOTENCY_KEY_CONFLICT` |

Replay 응답은 기존 `executionId`와 privacy-safe policy/transform/egress metadata를 반환하고 `replayed=true`로 표시한다. Provider 응답 원문은 재저장하거나 replay하지 않는다.

## Concurrency And Security

- PostgreSQL unique index가 동일 namespace의 실행 소유권을 한 요청에만 부여한다.
- namespace의 Institution은 요청 본문이 아니라 인증된 Principal을 Source of Truth로 사용한다.
- Institution binding과 Runtime Authorization을 통과한 요청만 idempotency namespace를 예약한다.
- 동시 중복 요청은 Runtime과 Connector 실행을 추가 생성하지 않는다.
- Replay 전에도 Institution·Workload·Purpose·Subject 인가를 다시 검증한다.
- `SENT_UNKNOWN` 복구와 Provider 상태 조회는 BE-9B에서 구현한다.

인가 거부 시도는 idempotency namespace를 소비하지 않는다. 거부 시도 자체의 영속 증적은 향후 별도 request-attempt 모델로 분리하며, 성공 실행과 동일한 reservation row로 표현하지 않는다.

## Migration

V12는 기존 `(workload_id, idempotency_key)` index를 새 namespace index로 교체한다. V1~V11 실행은 `LEGACY_UNSCOPED` Institution과 실행별 legacy hash로 backfill하여 기존 이력을 변경하거나 충돌시키지 않는다.
