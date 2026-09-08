# Approved Transaction Trust Boundary 트러블슈팅

## Eligibility 제거가 승인 없는 외부 호출을 만들 수 있던 문제

Digital Asset Runtime을 최신 DA Handoff에 맞추면서 KYC/AML/Wallet 기반 legacy eligibility gate를 먼저 제거하면,
`ApprovedTransaction` 경계가 설치되기 전 외부 호출이 가능한 중간 상태가 생길 수 있었다.

P0-2는 다음 순서를 하나의 안전 Gate로 고정했다.

```text
Server-owned ApprovedTransaction Resolver 설치
-> 승인 없음/무효/만료/Scope 불일치 차단
-> 승인 조건과 Outbound Request 비교
-> Legacy eligibility 판단을 Active Path에서 분리
```

기존 `ApprovalScope`는 institution/workload/purpose/subject와 허용 field/destination을 제한하는 인가 범위다.
자산, 금액, 목적지, 수익자, 유효기간을 승인하는 거래 원장으로 재사용하지 않는다.

## 승인 존재만 확인하면 충분하지 않았던 문제

정상 approval reference가 존재해도 다른 institution/subject에서 가져왔거나 요청 asset, amount/limit, destination,
beneficiary, period가 다르면 실행할 수 없다. Resolver는 reference 단독 전역 조회가 아니라
institution/subject/workload/purpose scope를 입력으로 받고, `ApprovedTransactionBindingEvaluator`가 승인 조건 전체를
요청과 비교한다. 불일치는 Connector 호출 0으로 수렴한다.

## Caller 값을 trusted metadata로 승격한 문제

초기 beneficiary 비교는 caller가 보낸 값을 `requestedBeneficiaryReference`라는 이름으로 `trustedMetadata`에 넣었다.
이렇게 하면 server-owned 승인값과 caller-controlled 요청값의 신뢰 수준이 같아진다.

수정 후 caller beneficiary는 일반 Canonical Field로 유지하고 `trustedMetadata`에는 `approved*` 값만 둔다. 현재 Provider
계약에 beneficiary 전송이 필요하지 않으므로 Transform에서 제거하되, 승인-요청 binding에는 계속 포함한다.

## Approved Transaction Authority가 둘 이상 연결되는 문제

여러 `ApprovedTransactionPort`가 동시에 등록되면 주입 순서에 따라 승인 원장이 달라질 수 있다. Resolver 생성 시
authority가 둘 이상이면 애플리케이션을 실패시켜 authoritative source를 하나로 고정한다. 운영 원장 전환은 두 Adapter를
동시에 켜는 방식이 아니라 configuration 교체로 수행한다.

## Optional, Nullable, Conditional의 차이

`assetContractAddress`와 `tokenId`는 모든 자산에서 동일한 optional field가 아니다. Native, fungible token, NFT에 따라
존재 조건이 다르다. Parser와 OpenAPI가 서로 다른 key 존재 규칙을 사용하면 FE가 문서상 정상 요청을 보내도 422가 된다.

필수 key와 허용 key를 분리하고 asset kind별 constructor invariant를 적용했다. 이 계약은 P0-4 JSON Schema의
`if/then`과 실제 parser를 함께 실행하는 테스트로 고정한다.
