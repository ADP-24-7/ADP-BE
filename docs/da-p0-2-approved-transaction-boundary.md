# DA-P0-2 Approved Transaction Trust Boundary

## 목적

Digital Asset 외부 실행의 허용 근거를 caller 입력이나 KYC/AML/Wallet eligibility 값이 아니라
서버가 조회한 Approved Transaction snapshot으로 고정한다. 이 문서는 P0-3 최종 DTO/Schema Freeze 전의
최소 신뢰 경계이며, Active Runtime 기준 계약이다.

## 실행 경계

```text
Runtime authorization
-> retrieval context
-> approvedTransactionReference 파싱
-> institution + subject digest + workload + purpose 범위 조회
-> server-owned ApprovedTransactionSnapshot pinning
-> asset + amount + destination profile + wallet + beneficiary + period 비교
-> 일치 시 기존 Transform/Egress/Recovery 실행
-> 없음/범위 불일치/조건 불일치 시 fail closed, Connector 0회
```

`approvedTransactionReference`만 caller가 제공한다. 승인 자산, 한도, destination profile, wallet,
beneficiary, 유효기간은 `ApprovedTransactionPort`가 반환한 snapshot만 신뢰한다. Context digest에는
승인 snapshot digest를 결속하고 Pack Policy Evidence의 profile identity에도 승인 ID/version/digest를 남긴다.

Caller의 `beneficiaryReference`는 승인값과 비교하기 위한 일반 Canonical Field이며 `trustedMetadata`에 넣지 않는다.
현재 Provider 계약에는 beneficiary 전송이 필요하지 않으므로 Transform에서 `REMOVE`하고 outbound payload에서도 제외한다.
요청 결속 필드로서 Approval Scope에는 포함하지만 Destination Profile에는 포함하지 않는다. `trustedMetadata`에는
server-owned `approved*` 값만 저장한다.

## Active/Legacy 분리

- `DigitalAssetCanonicalContextBuilder`는 `DigitalAssetComplianceContextResolver`를 호출하지 않는다.
- `DigitalAssetPolicyGate`는 `DigitalAssetPolicyProfilePort`를 조회하지 않는다.
- Provider payload와 destination/approval field contract에서 `kycStatus`, `amlStatus`,
  `walletVerified`를 제거한다.
- V19 `profile_*`에 승인 ID/version/digest를 기록하는 것은 기존 persistence와의 P0-2 transitional compatibility
  mapping이다. Policy Profile과 Approved Transaction을 구분하는 canonical evidence schema는 P0-3/P0-4에서 정의한다.
- 기존 Compliance Context, Policy Profile, reason code, V16~V20 schema는 과거 Evidence 해석을 위해
  삭제하지 않는다.
- Local V4 fixture 이력은 유지하고 V5 local script가 Active retrieval field만 정리한다. 이 변경만을 위한
  운영 Flyway V26은 추가하지 않는다.

## Fail-closed 증적

- 승인 reference 미존재 시 `DIGITAL_ASSET_APPROVED_TRANSACTION_NOT_FOUND`, HTTP 422, Connector 0회
- asset/amount/destination/beneficiary/period 불일치 시 Runtime `BLOCKED`, Connector 0회
- 다른 institution 또는 subject scope에서 같은 정상 reference를 조회해도 미발견 처리
- legacy Compliance fixture를 비활성화해도 유효한 승인 거래는 정상 완료
- Provider가 legacy eligibility field를 반환하면 신뢰하지 않고 `UNEXPECTED_FIELD`로 격리

## 후속 범위

P0-3에서 `ApprovedTransaction`, `OutboundRequest`, `ExternalExecutionResult`의 최종 이름과 nullability를
고정한다. P0-4에서는 identifier/enum/schema version을 DA와 함께 freeze한다. 현재 local adapter는 개발용
server-owned fixture이며 운영 승인 원장 구현을 대신하지 않는다. Connector 직전 snapshot 재검증과 TOCTOU 방지는
P0-7 PRE_EXECUTION Guard에서 처리한다.
