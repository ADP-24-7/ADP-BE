# BE-9B External Interaction Recovery Core

BE-9B 선행 Slice는 Pack별 Provider 구현에 앞서 외부 전송의 불확실성을 안전하게 보존하고 복구 worker가 중복 처리되지 않는 공통 계약을 고정한다. 이 Slice는 BE-9 전체 완료가 아니다.

## Retry Classification

| 외부 상태 | 분류 | 동작 |
| --- | --- | --- |
| `SENT_UNKNOWN` | `RECONCILE_FIRST` | 상태 조회 전 재전송 금지 |
| `ACKNOWLEDGED`, `COMPLETED` | `NO_RETRY` | 재전송 금지 |
| `NOT_SENT`, 명시적 pre-send transient | `RETRY_ALLOWED` | 정책 한도 내 재시도 가능 |
| 원인 불명 `FAILED` | `MANUAL_REVIEW` | 자동 재전송 금지 |

상태만으로 transient 여부를 추정하지 않는다. 외부 전달 가능성이 있으면 항상 reconciliation을 먼저 수행한다.

## Recovery Queue

- `SENT_UNKNOWN` Connector 결과는 같은 DB transaction에서 recovery job으로 등록한다.
- PostgreSQL `FOR UPDATE SKIP LOCKED`와 lease owner/expiry로 worker 중복 claim을 방지한다.
- 상태 조회 Adapter가 없거나 일시 실패하면 job을 완료 처리하지 않고 재예약한다.
- Connector별 상태 조회 Adapter는 Resolver로 선택하며, 미구성 fallback은 항상 fail-closed한다.
- lease가 만료된 `CLAIMED` job은 다른 worker가 회수할 수 있고 최대 시도 횟수에 도달한 job은 다시 claim하지 않는다.
- 최대 시도 횟수 도달 시 `EXHAUSTED`, 불명확하거나 위험한 상태는 `MANUAL_REVIEW`로 종료한다.
- 원문 요청·응답·고객정보는 recovery table에 저장하지 않는다.

## Deferred Gates

- AI/Digital Asset Provider별 status query adapter
- Pack별 external status mapping과 mismatch recovery
- `NOT_SENT` 안전 재전송 executor와 retry policy artifact binding
- 운영 scheduler, alert, manual recovery API
