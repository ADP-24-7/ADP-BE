# DA-P0-8 POST_EXECUTION Evidence / Re-binding / Recovery

## 목적

Provider HTTP 성공이나 transaction hash만으로 자산 이동 성공을 확정하지 않는다. Transaction Detail, Receipt/Finality,
Token Transfer 또는 Native Value를 독립된 Resolver 경계에서 확인한 뒤 P0-7에서 검증된 Approved/Requested Intent와
Executed tuple을 다시 결속한다.

## 처리 흐름

```text
Connector Result
-> ExternalExecutionResult strict parse
-> TransactionDetailResolver
-> ReceiptFinalityResolver
-> TokenTransferResolver / InternalTraceResolver
-> ExactExecutionAmountResolver
-> Requested vs Executed reconciliation
-> POST_EXECUTION Evidence 저장
-> Terminal Outcome 또는 SENT_UNKNOWN Recovery
```

## 완료 조건

`COMPLETED`는 다음 조건을 모두 충족할 때만 가능하다.

- Provider 결과가 `SETTLED`
- transaction hash 존재
- Receipt `SUCCESS`
- Finality `FINALIZED`
- Native Asset은 transaction value, Token/NFT는 transfer evidence에서 exact amount 확인
- P0-7 PRE_EXECUTION Guard가 존재하고 PASS한 execution
- Requested/Executed canonical tuple digest와 server-owned mismatch field 검증 완료

`tx.value=0`인 Token 실행은 이동 없음이 아니다. Token Transfer Evidence의 amount를 authoritative source로 사용한다.

## 상태

| 상태 | 의미 | Runtime 결과 |
| --- | --- | --- |
| `VERIFIED` | Final evidence와 tuple match 완료 | `COMPLETED` |
| `PENDING` | Receipt 또는 Finality 미확정 | `EGRESSING` |
| `SENT_UNKNOWN` | 전송 여부 또는 typed Provider 결과 불명확 | `EGRESSING` + Recovery |
| `REVIEW_REQUIRED` | Evidence 누락 또는 tuple mismatch | `REVIEW_REQUIRED` |
| `FAILED` | Provider/Receipt가 실패로 확정 | `FAILED` |

## Evidence

V31 `runtime.digital_asset_post_execution_evidence`는 다음 privacy-safe 값만 저장한다.

- Transaction, Receipt/Finality, Transfer/Internal Trace, Exact Amount evidence digest
- expected/actual canonical projection digest
- server-owned mismatch field enum
- Provider/Receipt/Finality 및 POST_EXECUTION 상태
- 관측 시각과 Provider response digest

원문 지갑 주소, 금액, transaction payload, transfer log, internal trace는 저장하지 않는다. P0-7 Guard FK를 통해
Approved/Requested 검증과 POST_EXECUTION Evidence의 lineage를 강제한다.

## Recovery

- Connector transport `SENT_UNKNOWN`과 typed Provider `SENT_UNKNOWN` 모두 `RECONCILE_FIRST`로 예약한다.
- 외부 응답 이후 Local outcome/evidence transaction이 실패해도 Recovery를 예약한다.
- 즉시 재전송하지 않으며 기존 Provider correlation key와 pinned Snapshot을 재사용한다.
- 공통 Recovery의 `EXTERNALLY_RECONCILED`는 외부 상태 조회 수렴을 뜻하며 새로운 자산 전송 성공을 임의 생성하지 않는다.

## 검증

- Token `nativeValue=0` + Transfer Evidence 성공
- settled token에서 Transfer Evidence 누락 시 Review
- transaction hash만 존재하고 Receipt/Finality 미확정 시 Pending
- typed Provider `SENT_UNKNOWN` Recovery 예약
- V31 Migration 및 Runtime Trace read model
- 기존 Digital Asset mismatch/recovery와 전체 회귀 테스트
