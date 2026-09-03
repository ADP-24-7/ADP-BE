# BE-11A Observability Foundation

BE-11A는 DA Artifact와 Pack별 운영 구현에 의존하지 않는 공통 관측 계약을 고정한다. 이 Slice 완료는 BE-11 전체 완료를 의미하지 않는다.

## Prometheus

Spring Boot Actuator가 제공하는 `/actuator/prometheus`에서 Prometheus text format을 조회한다. 기본값은 인증 필요이며, 격리된 로컬·수집망에서만 `ADP_PROMETHEUS_PUBLIC=true`로 공개할 수 있다. Endpoint에는 원문 요청·응답·Subject·Institution·Execution ID를 노출하지 않는다.

공통 metric은 다음과 같다.

| Metric | Label |
| --- | --- |
| `adp.runtime.terminal.transition.total` | `status=COMPLETED|BLOCKED|FAILED|REVIEW_REQUIRED|EXTERNALLY_RECONCILED` |
| `adp.idempotency.resolution.total` | `outcome=NEW|REPLAY|CONFLICT|IN_PROGRESS` |
| `adp.recovery.processing.total` | `outcome=RECONCILED|RESCHEDULED|EXHAUSTED|MANUAL_REVIEW|STALE_LEASE` |

HTTP 요청 수와 latency는 Spring Boot의 `http.server.requests`를 사용한다. Domain metric은 응답 시점이 아니라 reservation 판정과 실제 Runtime/Recovery 상태 전이가 영속화된 시점에 기록한다. Runtime metric은 unique execution 결과 수가 아니라 terminal state transition 횟수다. 처리할 recovery job이 없는 polling은 정상 상태이므로 counter로 기록하지 않는다.

기존 Connector·Guard·Transform·Vault metric은 enum 또는 고정 Reason Code만 label로 사용한다. 자유 문자열과 식별자는 metric label로 사용할 수 없다.

## Structured Log

Console log는 Logstash JSON 형식을 사용하며 Trace filter가 MDC에 설정한 `request_id`, `trace_id`를 구조화 필드로 출력한다. Runtime과 Recovery 로그는 고정 outcome 및 error category만 기록한다.

다음 값은 log와 metric에 기록하지 않는다.

- Prompt, Provider Response, Canonical Context 원문
- Subject ID와 고객·계좌 식별자
- API Key, Secret, Token 원문
- Idempotency Key와 Provider Correlation Key

## Deferred Gates

- BE-11B Audit Read Model과 Evidence Export
- Policy Lifecycle, Artifact Drift, Rollback 전용 metric
- Recovery queue depth/age gauge와 운영 alert
- NCP Prometheus scrape network policy·retention 설정
