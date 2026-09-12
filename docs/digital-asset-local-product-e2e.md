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

## Canonical Fixture Identity

- Repository-relative path: `ADP-DA/03_digital_asset/artifacts/local_product_e2e_v1`
- Source commit: `c586b44c4913454f8359f4a0cc53f2a2ccddeaff`
- Schema version: `adp-digital-asset-local-product-e2e-fixture/v1`

| Fixture | Content digest |
| --- | --- |
| `GOLDEN_PASS` | `sha256:3cc634b824a7749e626616e0f870440b282fbb768c6cb6cd5361815b1acd3713` |
| `BLOCK_AMOUNT` | `sha256:35933adc924f85d5c45bca642ea839a771f05fd3b8f23d996eda2e71b23f714a` |
| `BLOCK_DESTINATION` | `sha256:5c1f0a7410499288953448de808e84e7bebd53525c7a303525889afa795e1e98` |
| `EXECUTION_FAILED` | `sha256:43ae2826bd64d589b73e0e849a9dd121f21036697299e1d711a01aaae9a6944f` |
| `SENT_UNKNOWN_RECOVERED` | `sha256:50a8e79aee2fe93c07367357a5c2b14f562c13fcf7ecac0bcadf10924934c52c` |
| `DUPLICATE_REQUEST` | `sha256:ec79f3fb275d1c8163d99a2b1cb12d60f578d8198e92c23fcbdba3e98730bce4` |

## Closure Audit

| Case | Existing implementation | Existing test | Gap | Required change |
| --- | --- | --- | --- | --- |
| `GOLDEN_PASS` | Active Runtime, six-control guard, independent post evidence | Final state and effect count | Trace/control and evidence identity were only indirectly covered | Assert `/trace`, all six controls, independent evidence and PostgreSQL lineage |
| `BLOCK_AMOUNT` | Approved Transaction Policy fail-fast | Reason code and effect count | Connector/provider row absence was implicit | Assert zero provider request, connector, guard and post-evidence rows |
| `BLOCK_DESTINATION` | Server-owned approval/destination Policy fail-fast | Reason code and effect count | Destination bypass prevention was only indirectly covered | Assert pinned server profile and zero external-boundary rows |
| `EXECUTION_FAILED` | Independent failed receipt maps to `FAILED` | Final state and effect count | Receipt/finality provenance was not asserted here | Assert failed receipt, independent source and persisted outcome |
| `SENT_UNKNOWN_RECOVERED` | Common reconciliation-first recovery | Recovery convergence and effect count | Retry disposition and pinned digest were not asserted here | Assert `RECONCILE_FIRST`, status evidence and unchanged snapshot digest |
| `DUPLICATE_REQUEST` | Common idempotency replay | Replayed execution ID and effect count | Single active namespace row was implicit | Assert one execution, one provider/connector row and one external effect |

The DA fixture field `expected_pre_execution_decision=BLOCK` denotes a decision made before
external execution. In the Active Runtime, approved amount and destination mismatches are denied
earlier by the Approved Transaction Policy Gate. A P0-7 guard row is intentionally absent because
no outbound candidate or provider request is built. This fail-fast ordering is stronger than
deferring the same denial to the connector-adjacent guard and is not changed by the closure test.

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
