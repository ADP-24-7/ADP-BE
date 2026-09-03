# BE-8 Digital Asset Thin E2E

BE-8은 은행 내부 Tokenized Asset 구매 요청을 기존 단일 Runtime API와 공통 Policy, Transform, Egress, Recovery 경계에 연결한다. FPG는 Asset 발행, 원장, Private Key, 서명 또는 Custody를 수행하지 않는다.

## Runtime Contract

- Workload: `tokenized_asset_purchase`
- Purpose: `DIGITAL_ASSET_PURCHASE`
- Processing Context: `DIGITAL_ASSET`
- Destination: `dest_mock_asset_platform_v1`
- Required Input: `customerId`, `accountId`, `walletAddress`, `assetId`, `amount`, `kycStatus`, `amlStatus`, `walletVerified`

Digital Asset Profile은 `request` dataset만 선언하는 Input-only Retrieval Profile이다. 데이터베이스에서 고객 전체 정보를 조회하지 않으며 승인된 Transaction Request 필드만 Canonical Context에 추가한다.

## Privacy And Treatment

- Customer/Account ID: `VAULT_TOKEN`
- Wallet Address/Asset ID/Amount: `KEEP_EXACT_PROTECTED`
- KYC/AML/Wallet Verification: 승인된 상태 값만 exact 전달
- 허용되지 않은 입력 필드: Pack Input Schema 단계에서 거부
- Provider request/response 원문: Runtime, Audit, Trace에 저장하지 않음

## Settlement Evidence

V16 `runtime.digital_asset_transaction`은 Runtime Execution과 다음 privacy-safe 외부 상태를 연결한다.

- External Request/Transaction ID
- Settlement ID와 Settlement Status
- Reconciliation Result
- Provider Response Digest

HTTP/Connector 상태와 Settlement 상태는 별도 컬럼과 의미로 유지한다. Response Guard는 schema를 검증하고, Digital Asset Outcome Handler가 Settlement finality와 Runtime 상태를 해석한다. `SETTLING`은 `EGRESSING`, `CRITICAL_MISMATCH`는 `REVIEW_REQUIRED`, `SETTLED + MATCH`만 `COMPLETED`로 수렴한다.

Reconciliation 결과는 Provider 선언을 신뢰하지 않는다. FPG가 전송 직전 고정한 Customer/Account Token, Wallet, Asset, Amount 및 상태 필드와 Provider Settlement Evidence를 비교해 `MATCH`, `MISMATCH`, `CRITICAL_MISMATCH`, `WAIT`를 계산한 뒤 Response Guard 통과 후 저장한다.

V17부터 `SENT_UNKNOWN`은 External Request ID만 필수이며 Transaction/Settlement ID와 Response Digest 없이 저장할 수 있다. 기존 Recovery Core의 reconciliation-first 원칙에 따라 Runtime은 `EGRESSING`을 유지하고 Recovery Job을 생성한다.

## Current Fixture Scope

Local Mock Asset Platform은 Settlement 상태와 실제 처리 필드를 반환하며 `MATCH`는 FPG가 계산한다. 실제 Provider Status Query, mismatch recovery와 수동 복구 API는 BE-9 Pack별 최종 Gate에서 연결한다.
