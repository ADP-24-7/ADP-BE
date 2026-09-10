# Digital Asset Local Product E2E 트러블슈팅

## Approved Transaction 차단과 PRE_EXECUTION Guard를 혼동한 문제

### 증상

DA fixture의 `expected_pre_execution_decision=BLOCK`을 PRE_EXECUTION Guard row의 `BLOCKED`로 해석해
Amount와 Destination 차단 Case에서 테스트가 실패했다.

### 원인

실제 Runtime은 승인 조건을 다음 순서로 평가한다.

```text
Approved Transaction Policy Gate
-> Runtime Snapshot
-> PRE_EXECUTION Guard
-> Connector
```

금액 초과와 승인 목적지 불일치는 Policy Gate에서 먼저 확정되므로 PRE_EXECUTION Guard와 Connector는 호출되지
않는다. 이는 Evidence 누락이 아니라 fail-fast 경계다.

### 해결

- BLOCK Case는 `execution_pack_policy_evaluation.reason_codes`와 Connector row 0건을 확인한다.
- PASS Case만 `digital_asset_pre_execution_guard.status=PASSED`와 6개 control을 확인한다.
- 모든 Case에서 External Effect Count를 별도로 검증한다.

## Recovery Evidence와 Runtime 상태의 부분 Commit 방지

### 증상

Domain adapter가 자체 transaction으로 Evidence를 먼저 commit한 뒤 공통 Recovery lease CAS가 실패하면,
Evidence는 `VERIFIED/RECOVERED`지만 Recovery와 Runtime은 미완료인 부분 성공이 발생할 수 있었다. 반대로 generic 상태만
수렴시키면 `digital_asset_transaction`은 `SENT_UNKNOWN/WAIT`에 남고 post-execution evidence도 복원되지 않는다.

### 위험

generic connector 상태만으로 Runtime을 수렴시키면 실제 receipt 실패나 transfer 불일치를 확인하지 못한다.
Digital Asset에서는 ACK 자체가 settlement 성공 증거가 아니다.

### 해결

- 공통 Recovery에 pack-neutral `ExternalReconciliationEvidencePort`와 별도 Transactional Coordinator를 둔다.
- 외부 status query는 transaction 밖에서 수행하고, lease CAS와 모든 DB write만 짧은 단일 transaction으로 묶는다.
- 같은 transaction에서 lease ownership을 먼저 검증하고 Digital Asset Evidence, transaction, Recovery, Runtime을 갱신한다.
- stale lease 또는 Evidence 저장 실패 시 전체 transaction을 rollback한다.
- Digital Asset status adapter는 reconciliation evidence가 필수임을 선언하며 adapter 누락을 fail-closed한다.
- 최초 connector 호출 횟수를 Fake Platform state에 기록해 blind resend가 없음을 검증한다.

통합 테스트는 stale worker와 Evidence source 누락을 각각 주입해 post evidence 0건, transaction
`SENT_UNKNOWN/WAIT`, Runtime `EGRESSING`, Recovery `CLAIMED`가 유지되는지 확인한다.

## tx hash를 성공으로 오판할 수 있는 로컬 시나리오 부재

### 증상

기존 Fake Connector는 성공, pending, mismatch, SENT_UNKNOWN만 만들 수 있어 receipt가 `FAILED`인 실행을 실제
Runtime 경로에서 재현하지 못했다.

### 해결

`asset-execution-failed` local trigger를 추가했다. transaction hash와 finality가 존재하더라도 independent receipt가
`FAILED`이면 post-execution evidence와 Runtime 최종 상태가 모두 `FAILED`가 되는지 검증한다.

## Fixture 복제로 인한 Drift 방지

BE test resource에 DA JSON을 복사하면 DA 계약 변경과 BE 검증 입력이 분리될 수 있다. 따라서 로컬에서는 sibling
`ADP-DA`, CI에서는 고정 DA commit을 read-only source로 사용한다. fixture 디렉터리가 없으면 테스트를 skip하지 않고
명시적으로 실패시킨다.
