# Recovery Operations 트러블슈팅

## `SENT_UNKNOWN`을 Retry 대상으로 직접 처리한 위험

Timeout이나 응답 유실은 외부 실행 실패를 뜻하지 않는다. 수동 Retry API도 자동 worker와 동일하게 Status Query를 먼저
수행하고, 외부 시스템이 `NOT_SENT`를 반환한 경우에만 Retry Port를 호출한다. `SENT_UNKNOWN`은 backoff 후 다시 조회하며
`ACKNOWLEDGED`와 `COMPLETED`는 재전송하지 않는다.

## 수동 명령과 Scheduler가 서로 다른 동시성 경계를 가진 문제

운영 API가 Recovery 상태를 읽은 뒤 직접 처리하면 scheduler와 동시에 같은 incident를 실행할 수 있다. 모든 경로가 DB의
`claimById`/`claimNext`와 lease owner/expiry 조건을 사용하도록 통합했다. lease를 잃은 worker의 terminal update는
`RECOVERY_STALE_LEASE`로 거부한다.

## API 재호출이 동일 운영 명령을 반복 실행한 문제

HTTP retry와 운영자 중복 클릭을 Recovery 재실행으로 취급하면 외부 부수효과가 반복될 수 있다. `operationId`를 incident
scope의 unique key로 먼저 예약하고, actor/type/outcome/reason/evidence digest를 append-only event로 기록한다. 동일 ID와
동일 명령은 저장된 결과를 replay하고 다른 명령 type은 conflict로 거부한다.

## Raw Payload를 저장하지 않고 안전 재전송하는 방법

Recovery table에는 요청 payload를 저장하지 않는다. 따라서 공통 worker가 payload를 재구성하거나 현재 ACTIVE 정책을 다시
선택해서는 안 된다. Connector별 Retry Adapter가 저장된 provider correlation identity를 이용해 Provider의 멱등 retry
계약을 수행한다. 이 계약을 제공하지 못하는 Connector는 fallback adapter에서 fail-closed한다.

## 설정값과 실제 Worker Lease가 달랐던 문제

수동 API는 `adp.recovery.lease-duration`을 사용했지만 자동 worker는 30초 상수를 사용하면 환경별 lease 조정 시 경쟁 조건이
달라진다. 두 경로 모두 같은 설정을 주입받도록 통일하고 0 이하 값은 시작 단계에서 거부한다.

## SQL 파라미터 잔재가 수동 Reconcile을 500으로 만든 문제

상태 전이 SQL을 단순화한 뒤 삭제된 분기에서 사용하던 named parameter가 남아 `InvalidDataAccessApiUsageException`이
발생했다. 미사용 조건을 제거했고 Controller 통합 테스트로 operation reservation부터 재예약과 replay까지 검증한다.

## 통합 테스트의 PENDING Row가 다른 Worker 테스트를 오염시킨 문제

Recovery worker는 전역 due queue에서 `SKIP LOCKED`로 하나를 선택하므로 다른 테스트가 남긴 PENDING row를 정상적으로
claim할 수 있다. 신규 API 테스트가 생성한 operation/recovery row를 매 테스트 후 제거해 대상 선택의 재현성을 보장했다.
운영 동작의 결함이 아니라 공유 DB fixture 격리 문제였지만, queue 테스트에서는 데이터 생명주기를 명시해야 한다.

## 멱등성 TTL을 요청 생성 시점부터 계산한 위험

장시간 실행이나 `SENT_UNKNOWN` 복구 중 key가 먼저 만료되면 동일 외부 요청이 다시 전송될 수 있다. TTL은 요청 생성 시점이
아니라 `COMPLETED`, `BLOCKED`, `EXTERNALLY_RECONCILED`처럼 안전하게 종료된 시점부터 계산한다. `FAILED`,
`REVIEW_REQUIRED`와 진행 중 Recovery는 자동으로 namespace를 해제하지 않는다.

## Key 재사용을 위해 Runtime Row를 삭제한 증적 훼손 위험

멱등성 namespace와 Audit evidence의 생명주기를 같은 것으로 취급하면 key 재사용 과정에서 실행 이력이 삭제될 수 있다.
V38은 만료된 row에 `idempotency_archived_at`만 기록하고 partial unique index의 활성 namespace에서 제외한다. Runtime,
Connector, Recovery와 Audit row는 그대로 남아 사후 추적이 가능하다.

## 기존 실행에 신규 TTL을 일괄 적용한 복구 계약 변경 위험

과거 실행은 당시 Provider idempotency 보존기간과 reconciliation window를 알 수 없다. 임의 backfill로 namespace를 풀지 않고
`LEGACY_INDEFINITE`로 유지한다. 신규 실행만 생성 시점의 retention seconds를 pinning해 이후 설정 변경과 분리한다.

## Recovery Reconcile이 Terminal TTL 설정을 우회한 문제

일반 Runtime 상태 갱신은 `EXTERNALLY_RECONCILED`의 만료시간을 설정하지만 Recovery persistence는 상태를 직접 갱신했다.
그 결과 정상 수렴한 reservation의 `idempotency_expires_at`이 비어 namespace를 영구 점유할 수 있었다. Recovery,
Connector, Runtime을 수렴시키는 동일 트랜잭션에서 실행에 pinning된 retention seconds로 만료시간도 함께 설정했다.

## MARK_REVIEW가 외부 처리 Attempt를 소비한 문제

모든 수동 명령이 같은 claim SQL을 사용해 `MARK_REVIEW`도 attempt count를 증가시켰다. 마지막 budget 직전에 Review로
전환하면 이후 Provider 상태가 확인되어도 Reconcile할 수 없는 dead-end가 생겼다. 외부 상태 조회와 재전송 claim은 attempt를
소비하고, 운영자의 Review 전환은 별도 claim을 사용해 lease만 획득하도록 분리했다.

## Worker Crash 후 IN_PROGRESS Operation Evidence

operation 예약 직후 프로세스가 종료되면 event가 `IN_PROGRESS`로 남을 수 있다. 다른 operation ID와 만료된 incident lease로
복구할 수 있어 현재 Recovery 자체를 막지는 않는다. BE-11 운영 보강에서 stale operation age metric과
`STALE`/`ABANDONED` 전이 정책을 함께 정의한다.
