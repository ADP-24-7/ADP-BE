# Digital Asset Local Product E2E Closure

## 목적

DA PR #31이 고정한 6개 deterministic fixture를 별도 Policy 또는 Execution Engine 없이 실제 BE Runtime에
통과시킨다. 검증 기준 DA commit은 `c586b44c4913454f8359f4a0cc53f2a2ccddeaff`다.

## 실행 경로

```text
DA fixture
-> POST /v1/runtime/executions
-> Approved Transaction / Policy Gate
-> Runtime Snapshot
-> PRE_EXECUTION Guard
-> Fake Digital Asset Platform
-> independent POST_EXECUTION Evidence
-> Recovery / Reconciliation
-> PostgreSQL Runtime state
-> GET /v1/runtime/executions/{executionId}
```

`DigitalAssetLocalProductE2ETests`는 `ADP_DA_ROOT` 아래의 원본 JSON을 직접 읽는다. BE 저장소에 fixture를
복제하지 않으며 CI는 위 DA commit을 별도 checkout해 contract source로 사용한다.

## Case 계약

| Case | 핵심 검증 | 최종 상태 | External Effect |
| --- | --- | --- | --- |
| `GOLDEN_PASS` | 독립 receipt/finality/transfer evidence 검증 | `COMPLETED` | 1 |
| `BLOCK_AMOUNT` | 승인 금액 초과를 connector 전에 차단 | `BLOCKED` | 0 |
| `BLOCK_DESTINATION` | 승인 목적지 불일치를 connector 전에 차단 | `BLOCKED` | 0 |
| `EXECUTION_FAILED` | tx hash가 있어도 실패 receipt를 성공으로 판정하지 않음 | `FAILED` | 1 |
| `SENT_UNKNOWN_RECOVERED` | blind resend 없이 status query와 독립 evidence로 수렴 | `EXTERNALLY_RECONCILED` | 1 |
| `DUPLICATE_REQUEST` | 동일 idempotency key의 기존 execution replay | `COMPLETED` | 1 |

각 Case는 Runtime/API 응답뿐 아니라 Institution, Workload, Purpose, Idempotency Key, Policy Version,
Policy reason, Digital Asset Evidence, Recovery row와 최종 PostgreSQL 상태를 함께 확인한다.

## Recovery Evidence 경계

공통 Recovery Service는 Digital Asset 타입을 직접 알지 않는다. Transactional Coordinator가 같은 DB transaction
안에서 lease ownership을 CAS로 재검증한 뒤 `ExternalReconciliationEvidencePort`를 통해 connector별 독립 Evidence를
복원하고 generic recovery와 Runtime 상태를 함께 전환한다. 어느 저장 단계든 실패하면 전체 변경을 rollback한다.

로컬 Digital Asset adapter는 status query로 확인된 실행의 transaction, receipt/finality, transfer evidence를
재검증하고 `VERIFIED`일 때만 transaction을 `SETTLED/RECOVERED`로 갱신한다. Digital Asset status adapter가
Evidence를 필수 capability로 선언하므로 Evidence adapter가 누락되거나 결과가 completion-safe하지 않으면 fail-closed하고
Runtime을 성공 상태로 수렴시키지 않는다.

## 실행

ADP-BE와 ADP-DA가 같은 상위 디렉터리에 있을 때 다음 명령을 사용한다.

```bash
make digital-asset-e2e
```

다른 위치라면 `ADP_DA_ROOT=/absolute/path/to/ADP-DA`를 지정한다.
