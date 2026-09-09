# BE-9 Recovery Operations

BE-9 Recovery Operations는 `SENT_UNKNOWN`을 저장하고 조회하는 Core를 실제 운영 가능한 worker와 관리자 API로 연결한다.
핵심 원칙은 외부 상태가 확인되지 않은 요청을 재전송하지 않는 것이다.

## Processing Contract

```text
claim + lease
-> provider status query
-> ACKNOWLEDGED/COMPLETED: reconcile
-> SENT_UNKNOWN: backoff 후 reconciliation 재시도
-> NOT_SENT: connector-specific safe retry
-> FAILED/ambiguous/permanent error: manual review
```

- 자동 scheduler와 수동 명령은 동일한 PostgreSQL claim/lease 경계를 사용한다.
- worker는 지수 backoff와 최대 시도 횟수를 적용하며 batch 크기를 제한한다.
- `NOT_SENT`는 Status Query Adapter가 확인한 경우에만 Retry Port 호출을 허용한다.
- Status/Retry Adapter가 없거나 둘 이상 매칭되면 외부 호출 없이 fail-closed한다.
- Local Digital Asset Adapter는 provider correlation identity를 사용해 상태 조회와 멱등 재시도를 검증한다.
- 실제 Provider API를 연결할 때는 동일 Port에 provider별 status mapping과 idempotent retry 계약을 구현해야 한다.

## Operations API

| Method | Endpoint | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/admin/recovery/incidents` | OPERATOR, PRIVILEGED_OPERATOR, AUDITOR | scoped incident 검색 |
| `GET` | `/api/admin/recovery/incidents/{recoveryId}` | OPERATOR, PRIVILEGED_OPERATOR, AUDITOR | 상태, 시도 횟수, digest evidence 조회 |
| `POST` | `/api/admin/recovery/incidents/{recoveryId}/reconcile` | PRIVILEGED_OPERATOR | 상태 조회만 수행 |
| `POST` | `/api/admin/recovery/incidents/{recoveryId}/retry` | PRIVILEGED_OPERATOR | 상태 조회 후 `NOT_SENT`일 때만 재전송 |
| `POST` | `/api/admin/recovery/incidents/{recoveryId}/review` | PRIVILEGED_OPERATOR | 수동 검토로 전환 |

조회 SQL은 인증 Principal의 Institution과 허용 Workload를 항상 적용한다. 명령은 caller가 제공한 `operationId`를
`recovery_operation_event`의 unique key로 예약해 동일 명령을 한 번만 실행한다. Evidence에는 actor, 고정 operation/outcome,
reason code와 digest만 저장하며 provider correlation key와 요청/응답 원문은 API에 노출하지 않는다.

## Configuration

| Property | Default | Description |
| --- | --- | --- |
| `adp.recovery.lease-duration` | `30s` | worker lease |
| `adp.recovery.initial-backoff` | `1m` | 최초 재예약 지연 |
| `adp.recovery.max-backoff` | `15m` | backoff 상한 |
| `adp.recovery.scheduler.enabled` | `false` | scheduler opt-in |
| `adp.recovery.scheduler.fixed-delay` | `30s` | polling 간격 |
| `adp.recovery.scheduler.batch-size` | `20` | 1회 처리 상한, 최대 100 |
| `adp.idempotency.retention` | `72h` | 안전한 terminal 실행의 key namespace 보존기간 |

## Persistence

V37은 append-only `runtime.recovery_operation_event`와 incident 조회 인덱스를 추가한다. V38은 신규 실행에
`TERMINAL_TTL_V1` 정책을 pinning하고 `COMPLETED`, `BLOCKED`, `EXTERNALLY_RECONCILED`에 도달한 시점부터 만료를 계산한다.
동일 namespace가 다시 요청될 때 만료된 예약만 lazy archive하며 Runtime과 Audit evidence row는 삭제하지 않는다.

`FAILED`, `REVIEW_REQUIRED`, 진행 중 Recovery가 있는 실행은 자동 archive하지 않는다. V38 이전 실행은 당시 Provider와
reconciliation 보존 계약을 알 수 없으므로 `LEGACY_INDEFINITE`로 유지한다.

## Deferred Production Gates

- 실제 AI/Digital Asset Provider별 Status Query와 Safe Retry Adapter
- Destination/Provider별 versioned backoff/max-attempt 정책
- queue depth/oldest age alert와 management network isolation
- 운영 Provider별 reconciliation window보다 짧지 않도록 retention 값을 검증하는 배포 Gate
- 법적 Audit 보존기간에 따른 evidence archive store와 물리 cleanup 정책

Runtime row를 임의 삭제해 key를 재사용하게 만드는 방식은 Audit evidence를 훼손하므로 사용하지 않는다. V38의 archive는
멱등성 unique namespace에서만 제외하는 논리적 archive이며 영속 evidence는 그대로 보존한다.
