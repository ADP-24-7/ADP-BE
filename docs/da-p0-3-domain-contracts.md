# DA-P0-3 Digital Asset Domain Contracts

## 목적

Digital Asset Runtime의 승인 근거, caller 요청, 외부 실행 결과를 각각 `ApprovedTransaction`,
`OutboundRequest`, `ExternalExecutionResult`로 분리한다. 세 타입은 BE Runtime의 Source of Truth이며 DA artifact의
개념 모델을 실행 가능한 Java 계약으로 고정한다.

## 계약

### ApprovedTransaction

서버가 `ApprovedTransactionPort`에서 조회하는 승인 snapshot이다. caller는 reference만 제공한다.

- identity: 승인 ID, version, digest, institution, subject digest, workload, purpose
- policy binding: `approvedPolicySnapshotId`
- terms: 승인 asset descriptor, exact amount 또는 amount limit, destination profile, destination, beneficiary
- validity: `approvedFrom`, `approvedUntil`

승인 asset, 승인 금액, 유효기간 등 `approved*` 값은 Runtime request에서 받지 않는다.

### OutboundRequest

caller 입력과 server-owned request scope를 결합한 실행 요청이다.

- caller 입력: requested asset/amount/destination/beneficiary와 regulatory outbound data
- server-owned: `requestedAt`, `destinationProfileId`, `idempotencyKey`
- amount: 최대 78자리의 0 이상 atomic-unit 정수이며 wire에서는 decimal string을 사용한다.

실행 금액은 0보다 커야 하며 소수, 음수, 부동소수점 반올림을 허용하지 않는다. 입력 객체와 nested asset 객체의
누락 또는 미지 field는 fail closed한다.

### ExternalExecutionResult

Provider 응답을 Runtime outcome으로 사용하기 전에 파싱하는 typed result다.

- correlation: external request ID와 provider external reference
- status: external/provider/receipt/finality enum
- executed tuple: chain ID, recipient, asset kind/symbol/contract, amount, operation, token ID
- evidence: transaction hash, token transfer/internal trace reference, executed/finalized time, response digest

`transactionHash` 또는 HTTP 성공만으로 완료 처리하지 않는다. `SETTLED + receipt SUCCESS + finality FINALIZED`와
완전한 executed tuple이 함께 있어야 final success다. Provider의 미지 field와 불완전한 final evidence는
Response Guard에서 차단한다.

## Canonical 실행 Tuple

```text
chain_id
+ recipient_address
+ asset_kind
+ asset_symbol
+ asset_contract_address
+ amount
+ operation
+ token_id
```

Reconciliation은 Provider request의 tuple과 typed external result의 tuple을 비교한다. raw projection은 mismatch
evidence에 저장하지 않고 digest와 server-owned mismatch enum만 남긴다.

## Runtime 연결

```text
Runtime request
-> DigitalAssetRuntimeInput strict parse
-> server-owned ApprovedTransaction resolve/pin
-> approval/request identity binding
-> Canonical Context + Policy Gate
-> Transform + Provider Request
-> ExternalExecutionResult strict parse
-> receipt/finality 검증
-> canonical tuple reconciliation
-> terminal outcome
```

OpenAPI의 `/v1/runtime/executions`에는 `DigitalAssetRuntimeInput` DTO와 실행 예제를 제공한다. FE는 이 schema를
request draft 기준으로 사용할 수 있다.

Local V6 migration은 P0-3 이전 request field(`walletAddress`, `assetId`, `amount`)를 retrieval fixture에서
제거한다. 이미 적용된 local migration의 checksum을 변경하지 않는 전진 migration이다.
운영 V26 migration은 기존 mismatch field 값을 유지하면서 canonical tuple field enum을 DB CHECK에 추가한다.

## 검증 증적

- 승인 reference의 institution/subject/workload/purpose scope 조회
- 승인 조건과 outbound identity 전체 binding
- amount precision/범위 검증
- request 누락/미지/server-owned field 주입 차단
- external result 미지 field와 불완전 finality 차단
- canonical tuple mismatch와 non-final WAIT 분리
- Digital Asset Thin E2E와 OpenAPI contract test

## 후속 범위

P0-4에서 enum/identifier의 외부 canonical value, versioned JSON Schema, canonical serialization과 DA consumer
contract를 freeze한다. 운영 승인 원장과 실제 Provider adapter, PRE_EXECUTION 재검증은 각각 후속 P0 단계의
책임이다.
