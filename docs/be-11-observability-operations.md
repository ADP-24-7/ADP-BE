# BE-11 Observability Operations

BE-11 Observability Operations는 기존 Runtime counter, 구조화 로그와 Audit Read Model을 Recovery/Policy/Security 운영 상태까지
확장한다. Metric에는 institution, workload, execution ID 같은 고카디널리티 식별자를 tag로 사용하지 않는다.

## Operations Summary API

| Method | Endpoint | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/admin/operations/summary` | OPERATOR, PRIVILEGED_OPERATOR, AUDITOR | 지정 시간창의 Runtime/Recovery/Policy/Security 요약 |
| `GET` | `/api/admin/operations/policy-events` | OPERATOR, PRIVILEGED_OPERATOR, AUDITOR | Lifecycle 및 Current Selection event 검색 |

두 API는 인증 Principal의 Institution과 허용 Workload를 SQL에서 항상 적용한다. Summary는 Runtime terminal 분포, Recovery
backlog/age/manual review/exhausted, Current Selection/drift/activation/rollback, Denied Attempt를 반환한다. Policy Event는
append-only Lifecycle Transition과 Activation/Rollback Event를 단일 시간순 Read Model로 제공한다.

응답에는 artifact identity/digest, bounded reason, actor와 revision evidence만 포함한다. Runtime payload, subject,
idempotency key, provider correlation key와 credential은 포함하지 않는다.

## Metrics

| Metric | Type | Meaning |
| --- | --- | --- |
| `adp.recovery.queue.depth` | Gauge | 현재 자동 처리 가능 backlog |
| `adp.recovery.queue.oldest.age.seconds` | Gauge | 가장 오래된 backlog age |
| `adp.recovery.manual.review.count` | Gauge | 현재 수동 검토 incident |
| `adp.recovery.exhausted.count` | Gauge | 현재 exhausted incident |
| `adp.recovery.operation.stale.count` | Gauge | 임계시간을 넘긴 `IN_PROGRESS` 운영 명령 수 |
| `adp.recovery.operation.oldest.age.seconds` | Gauge | 가장 오래된 stale 운영 명령 age |
| `adp.policy.current.selection.count` | Gauge | 현재 selection 수 |
| `adp.policy.drift.count` | Gauge | Lifecycle Artifact와 불일치하는 selection 수 |
| `adp.policy.lifecycle.transition.total` | Counter | target stage별 committed transition |
| `adp.policy.current.selection.total` | Counter | activation/rollback event |
| `adp.security.control.total` | Counter | freshness/authorization/destination rejection |
| `adp.operational.metrics.refresh.success` | Gauge | 최근 전역 운영 Snapshot 갱신 성공 여부(1/0) |
| `adp.operational.metrics.snapshot.age.seconds` | Gauge | 마지막 성공 Snapshot의 경과 시간 |
| `adp.operational.metrics.refresh.failures` | Counter | 운영 Snapshot 갱신 실패 누적 횟수 |

Drift는 selected artifact가 없거나 `ACTIVE`가 아니거나, selected digest/revision이 authoritative Lifecycle row와 다를 때다.
Gauge는 scrape마다 같은 DB 집계를 반복하지 않도록 짧은 cache snapshot을 공유하며 기본 TTL은 5초다. DB 조회 실패 시
마지막 성공 Snapshot을 유지하되 refresh 성공 여부와 Snapshot age를 별도 노출해 stale 값이 정상 상태로 해석되지 않게 한다.
운영 명령은 기본 5분 이상 `IN_PROGRESS`일 때 stale로 관측한다. 이는 Evidence를 자동 변경하는 정책이 아니라 후속 조사를
발생시키는 신호이며 `ADP_STALE_OPERATION_THRESHOLD`로 조정한다.

`/actuator/prometheus`는 기본적으로 전용 `METRICS_SCRAPER` 역할을 가진 `SERVICE` Principal만 접근할 수 있다. 전역 운영
상태를 포함하므로 테넌트 범위의 `OPERATOR`, `PRIVILEGED_OPERATOR`, `AUDITOR`와 `RUNTIME_EXECUTOR`에는
허용하지 않는다. `ADP_PROMETHEUS_PUBLIC=true`는 격리된 로컬 개발·수집망에서만 사용하는 명시적 opt-in이다.

## Alert Rules

`ops/prometheus/alerts.yml`은 다음 alert를 정의한다.

- Recovery oldest age 15분 초과
- Exhausted recovery 존재
- Stale recovery operation 존재
- Current Selection drift 존재
- 15분 내 rollback 3회 초과
- 5분 Runtime failure rate 5% 초과
- 운영 Snapshot refresh 실패
- 마지막 성공 Snapshot age 60초 초과

CI는 `prom/prometheus:v3.5.0`의 `/bin/promtool`로 rule syntax와 expression을 검증한다.

## Transaction Semantics

Lifecycle/Current Selection counter는 Service method 반환 시점이 아니라 Spring transaction `afterCommit`에서 증가한다.
DB rollback 또는 commit failure를 성공 metric으로 기록하지 않는다. 기존 append-only DB event가 audit source이며 counter는
운영 추세 확인용이다. Commit 이후 Meter Registry 장애는 구조화 경고로 남기고 업무 API 결과로 전파하지
않는 best-effort 부수 효과로 격리한다.

## Deferred NCP Gates

- Management port 분리와 private subnet/ACG 기반 scrape 제한
- Prometheus credential 주입과 rotation
- Metric, log, audit evidence별 retention 및 archive 기간
- Stale operation의 `STALE`/`ABANDONED` 상태 전이와 운영 승인 정책
- Alert routing, on-call destination과 운영 SLA 확정

로컬에서 `/actuator/prometheus`를 공개하는 설정은 개발 opt-in이며 NCP 운영 보안의 대체물이 아니다.
