# DA Industry Analysis to BE Runtime Contract Gap Review

## Decision

DA `main`의 `c9d474c` 산업 분석과 BE `main`의 `5561577` Runtime 계약을 비교한 결과, 현재는 BE Digital Asset
wire contract, enum, migration을 변경하지 않는다.

DA 변경은 Stablecoin, Deposit Token, RWA, Token Security를 개별 Runtime 정책 분기로 승격하는 versioned Handoff가
아니다. 산업·인프라 확장 가능성을 설명하는 분석 Evidence이며 개별 Gateway Rule의 직접 법적 근거가 아니라는 scope도
명시한다. 따라서 분석 용어만으로 BE enum을 추가하면 검증되지 않은 정책 의미를 Runtime에 고정하게 된다.

## Evidence Traceability and Current Coverage

모든 DA 근거는 commit `c9d474c` 기준이다. 아래 reference는 분석 파일과 cell/section 위치를 식별하지만 versioned Runtime
Evidence ID를 대체하지 않는다. 최신 분석에는 Runtime Handoff용 Artifact Version, Evidence ID와 Content Digest가 없다.

| DA analysis axis | DA evidence/source | Existing BE contract | Decision |
| --- | --- | --- | --- |
| Deposit Token | `01_industry_analysis/01_industry_trends/04_digital_asset_infrastructure_ref.md`, `1. Korea - Project Hangang / Deposit Token 기반 실거래` | `FUNGIBLE_TOKEN` + chain/symbol/contract identity | 기술 실행 가능, semantic class 미도입 |
| Tokenized Collateral/RWA | 같은 파일, `2. Global - CFTC Reference / Tokenized Collateral` | Fungible 또는 NFT technical kind와 asset identity | 정책 차이 확정 시 별도 semantic contract 검토 |
| Stablecoin/RWA custody and payment | `01_industry_analysis/01_industry_trends/03_five_major_banks_trends_r.ipynb`, `kb_data`, `shinhan_data`, `hana_data` code cell | `FUNGIBLE_TOKEN` + destination/beneficiary/approval binding | 산업 동향만으로 정책 분기 미도입 |
| Token Security/STO | 같은 Notebook의 `shinhan_data` code cell | Fungible 또는 NFT technical kind | 법적·상품 분류 Evidence가 있는 Handoff 전까지 미도입 |
| Asset technical type | BE `DigitalAssetKind` | `NATIVE`, `FUNGIBLE_TOKEN`, `NON_FUNGIBLE_TOKEN` | 현재 충분 |
| Token identity | BE `DigitalAssetDescriptor` | chain ID, symbol, contract address, token ID | 현재 충분 |
| Wallet/Destination | BE `ApprovedTransaction`, `OutboundRequest` | approved/requested destination, destination profile | 현재 충분 |
| Counterparty | BE `ApprovedTransaction`, `OutboundRequest` | approved/requested beneficiary reference | 현재 충분 |
| Amount/Period | BE Approval Binding | exact amount 또는 limit, approved from/until | 현재 충분 |
| Execution/Settlement | BE P0-8 Evidence | Provider, receipt, finality, transfer evidence | 현재 충분 |

Stablecoin, Deposit Token과 fungible RWA는 기술적으로 `FUNGIBLE_TOKEN`과 contract identity로 실행할 수 있다. 하지만
그 사실이 동일한 법적 성격이나 정책을 의미하지는 않는다. 현재 BE는 caller가 `semanticAssetClass` 같은 미승인 필드를
보내면 strict descriptor parser에서 거부한다.

## No-change Boundary

- DA Notebook 또는 산업 분석 파일을 Runtime이 직접 읽지 않는다.
- asset symbol이나 contract address 문자열로 semantic class를 추론하지 않는다.
- caller가 semantic class 또는 정책 Evidence를 선택하게 하지 않는다.
- 기존 `DigitalAssetKind`에 정책·법률 의미를 덧씌우지 않는다.
- 현재 P0-3~8 Canonical Contract와 FE request shape를 유지한다.

## Versioned Change Gate

다음 항목을 갖춘 DA to BE Handoff가 전달되고 실제 통제 차이가 확인될 때만 additive v2 계약을 검토한다.

1. semantic asset class enum과 각 값의 배타적 정의
2. 기존 `DigitalAssetKind` 및 chain/contract identity와의 mapping
3. class별 Destination, Counterparty, Approval, Finality 통제 차이
4. Dataset/Analysis version, source/evidence ID, content digest
5. 적용 시작·종료 시각과 정책 claim scope
6. server-owned resolver 및 Approved Transaction/Snapshot binding 방식
7. 기존 P0-3~8, migration, OpenAPI, FE contract 영향도

조건이 충족되면 기존 v1 파일이나 migration을 수정하지 않고 새 enum/schema/version을 추가한다. Classification은 caller
input이 아니라 검증된 Artifact와 Approved Transaction에서 resolve하고 실행 Snapshot에 pinning해야 한다.

## v1 and v2 Coexistence

- 기존 v1 Approved Transaction은 생성 당시 contract version과 Artifact binding을 유지한다.
- 실행 시작 시 선택한 contract/schema/artifact version과 digest를 Runtime Snapshot에 함께 pinning한다.
- v2는 신규 versioned Artifact와 Approved Transaction이 v2에 명시적으로 binding된 실행부터 적용한다.
- v2 배포 또는 ACTIVE 변경이 기존 v1 Snapshot을 재분류하거나 digest를 다시 계산하지 않는다.
- Replay와 Recovery는 원 실행에 pinning된 v1/v2 계약을 그대로 재사용하며 최신 계약으로 자동 승격하지 않는다.
- FE와 Caller가 contract version을 임의 선택하지 않는다. 서버가 승인 Snapshot과 ACTIVE Artifact에서 version을 resolve한다.
- v1 종료가 필요하면 retention/recovery window와 진행 중 execution이 없음을 확인한 별도 deprecation migration으로 처리한다.

## Verification Evidence

- `DigitalAssetDescriptorTests`는 미승인 `semanticAssetClass` 입력이 schema mismatch로 차단됨을 검증한다.
- 기존 Canonical Contract 테스트는 세 technical kind와 conditional field 계약을 계속 고정한다.
- 현재 결론은 `No-change`이며 향후 분석 결과를 무시한다는 뜻이 아니라, 정책 차이를 증명하는 versioned Handoff 전까지
  Runtime 변경을 유예한다는 뜻이다.
