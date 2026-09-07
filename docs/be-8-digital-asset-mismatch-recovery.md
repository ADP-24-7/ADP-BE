# BE-8 Digital Asset Mismatch Recovery

이 Slice는 완료 상태로 응답한 Asset Platform의 거래 내용이 FPG가 전송 직전에 고정한 거래 내용과 다를 때 자동 완료하거나 재전송하지 않고 검토 대상으로 격리한다.

## Classification

- `MISMATCH`: KYC/AML 등 비핵심 상태 필드 불일치
- `CRITICAL_MISMATCH`: External Request ID, Wallet Address, Asset ID, Amount 중 하나 이상 불일치
- 두 결과 모두 Runtime은 `REVIEW_REQUIRED`, Controlled Delivery는 `WITHHELD`로 수렴한다.
- 이미 외부 처리가 확인된 응답이므로 SENT_UNKNOWN Recovery Queue에 넣거나 자동 재전송하지 않는다.

## Evidence

V20 `runtime.digital_asset_mismatch_case`는 실행별 다음 privacy-safe 증적을 저장한다.

- Severity와 정렬된 mismatched field 이름
- Expected/Actual server-defined transaction projection SHA-256 digest
- Case status `OPEN`
- `auto_retry_allowed=false`
- Opened/Updated timestamp

Expected/Actual transaction 원문은 저장하지 않는다. 불일치 field는 서버 소유 enum으로만 저장하며 Provider의 임의 key는 `UNEXPECTED_FIELD`로 정규화한다. DB CHECK는 허용 field label, severity/status, digest 형식, 비어 있지 않은 field 배열과 자동 재시도 금지를 강제한다.

## Deferred

- Operator 전용 case 조회 및 수동 판정 API
- 이중 승인 기반 `RESOLVED`/`REJECTED` 전이
- 실제 Provider status/detail API를 통한 추가 증적 수집
- FE Digital Asset Lab의 mismatch case 표시
