# BE-8 Digital Asset Recovery

이 Slice는 BE-8 Thin E2E에서 `SENT_UNKNOWN`으로 보존한 외부 전송을 공통 BE-9B Recovery Core에 연결한다.

## Contract

- `fake-digital-asset-platform` 전용 `ExternalStatusQueryPort`를 Local fixture에서만 등록한다.
- Fake Connector가 Provider-side 상태 저장소에 correlation별 결과를 기록하고 Status Query Adapter가 이를 조회한다.
- 조회에는 저장된 Provider Correlation Key를 사용한다.
- 조회 증적은 원문 응답 대신 SHA-256 digest로 저장한다.
- `ACKNOWLEDGED` 확인 시 Recovery, Connector Execution, Runtime Execution을 기존 공통 transaction에서 함께 수렴한다.
- Digital Asset Adapter는 공통 claim, lease, retry, terminal observability를 우회하지 않는다.

## State Convergence

`SENT_UNKNOWN -> ACKNOWLEDGED` 확인 결과는 다음 상태로 수렴한다.

- Recovery: `RECONCILED`
- Connector: `ACKNOWLEDGED`
- Runtime: `EXTERNALLY_RECONCILED`
- Digital Asset Transaction: `SENT_UNKNOWN / WAIT` 유지

마지막 항목은 의도적이다. Provider 전송 확인은 Settlement finality 증거가 아니므로 별도의 settlement ID나 finality 응답 없이 `SETTLED`로 승격하지 않는다.

## Deferred

- 실제 Provider Status API와 인증
- Settlement 상세 응답을 포함하는 Pack별 reconciliation result
- Mismatch recovery와 수동 복구 API
- KYC, AML, Wallet, Amount Limit용 versioned Policy Profile
