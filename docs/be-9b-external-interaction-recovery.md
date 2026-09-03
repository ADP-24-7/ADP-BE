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
- 전송 전에 생성한 `providerRequestId`를 Provider Correlation Key로 사용하고 실제 HTTP `X-Idempotency-Key`와 AI payload의 `externalRequestId`로 전달한다.
- Recovery Job은 동일 Correlation Key를 저장해 Provider Status Query가 내부 ID가 아닌 Provider-visible identity로 조회할 수 있게 한다.
- PostgreSQL `FOR UPDATE SKIP LOCKED`와 lease owner/expiry로 worker 중복 claim을 방지한다.
- 상태 조회 Adapter가 없거나 일시 실패하면 job을 완료 처리하지 않고 재예약한다.
- Connector별 상태 조회 Adapter는 Resolver로 선택하며, 미구성 fallback은 항상 fail-closed한다.
- lease가 만료된 `CLAIMED` job은 다른 worker가 회수할 수 있고 최대 시도 횟수에 도달한 job은 다시 claim하지 않는다.
- 최대 시도 횟수 도달 시 `EXHAUSTED`, 불명확하거나 위험한 상태는 `MANUAL_REVIEW`로 종료한다.
- 원문 요청·응답·고객정보는 recovery table에 저장하지 않는다.

## Reconciliation Convergence

`ACKNOWLEDGED` 또는 `COMPLETED` 확인은 Recovery row만 완료하지 않는다. 하나의 DB transaction에서 다음 상태를 함께 수렴한다.

- Recovery: `RECONCILED` 및 최종 외부 상태·조회 시각·Evidence Digest
- Connector Execution: Status Query에서 확인한 실제 외부 상태
- Runtime Execution: `EXTERNALLY_RECONCILED`

`EXTERNALLY_RECONCILED`는 외부 처리가 확인되어 재전송할 수 없고 idempotency 처리도 더 이상 진행 중이 아니라는 terminal 상태다. Provider 응답 원문 전달이나 Digital Asset Settlement 완료를 의미하지 않는다.

자동 복구로 외부 상태를 확정하지 못한 `MANUAL_REVIEW`와 `EXHAUSTED`도 Recovery row만 terminal로 남기지 않는다. 동일 transaction에서 Runtime을 `REVIEW_REQUIRED`로 수렴시켜 idempotency replay가 영구적인 `IN_PROGRESS`로 남지 않게 한다. Connector의 `SENT_UNKNOWN`은 확인되지 않은 사실을 보존하기 위해 변경하지 않는다.

Status Query Adapter가 같은 Connector에 둘 이상 매칭되거나 fallback이 둘 이상 등록되면 임의 선택하지 않고 구성을 거부한다. 실행 중 발견된 Adapter ambiguity는 transient 장애로 재시도하지 않고 Manual Review로 전환한다. lease owner뿐 아니라 `lease_until`도 update 조건으로 검증하며 소유권 또는 유효 기간을 잃은 worker의 상태 변경은 성공으로 보고하지 않는다.

## Deferred Gates

- AI/Digital Asset Provider별 status query adapter
- Pack별 external status mapping과 mismatch recovery
- `NOT_SENT` 안전 재전송 executor와 retry policy artifact binding
- 운영 scheduler, alert, manual recovery API
- Destination/Provider별 versioned Recovery Policy와 lease/retry/backoff/max-attempt 설정
