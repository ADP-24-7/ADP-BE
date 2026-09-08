# DA-P0-4 Canonical Identifier / Enum / Schema Freeze

## 목적

P0-3에서 확정한 `ApprovedTransaction`, `OutboundRequest`, `ExternalExecutionResult` Java 계약을 DA와 FE가
추측 없이 소비할 수 있는 versioned 외부 계약으로 고정한다. BE Java enum과 identifier가 Source of Truth이며,
JSON Schema, manifest, sample은 자동 테스트로 이 값과 일치해야 한다.

## 공개 산출물

| 파일 | 역할 |
| --- | --- |
| `docs/contracts/digital-asset-runtime-contract-v1.schema.json` | Draft 2020-12 계약과 재사용 가능한 `$defs` |
| `docs/contracts/digital-asset-runtime-contract-v1.json` | Version, identifier, enum inventory와 파일 digest manifest |
| `docs/contracts/samples/digital-asset-runtime-request-v1.json` | FE/DA가 참조할 Runtime 요청 sample |
| `docs/contracts/samples/digital-asset-external-result-v1.json` | Provider 결과와 POST_EXECUTION 분석용 sample |
| `docs/contracts/canonical/digital-asset-canonical-json-v1-vectors.json` | BE/DA 독립 구현이 공유하는 canonical golden vector |

Schema의 `$defs`는 `runtime_request`, `runtime_input`, `approved_transaction`, `outbound_request`,
`external_execution_result`, `transform_instruction`, `canonical_decision`을 제공한다.

## Version과 Identifier

| 항목 | v1 값 |
| --- | --- |
| Contract Schema | `adp-digital-asset-runtime-contract/v1` |
| Artifact ID | `ADP-DIGITAL-ASSET-RUNTIME-CONTRACT` |
| Artifact Version | `1.0.0` |
| Canonicalization | `adp-canonical-json/v1` |
| Execution Pack | `DIGITAL_ASSET` |
| Baseline Workload | `tokenized_asset_purchase` |
| Baseline Purpose | `DIGITAL_ASSET_PURCHASE` |
| Baseline Destination | `dest_mock_asset_platform_v1` / `1.0.0` |
| Destination Contract | `digital-asset-egress-contract/v1` |
| Provider Request Schema | `digital-asset-request/v1` |
| External Result Schema | `digital-asset-external-result/v1` |

Identifier와 Version 상수는 `DigitalAssetCanonicalContract`가 소유한다. Local fixture와 Runtime resolver는 같은
상수를 사용하므로 문서 또는 Adapter에 같은 문자열을 별도 Source of Truth로 만들지 않는다.

## Canonical Enum

- Decision: `PASS`, `BLOCK`, `REVIEW`
- Transform Strategy: `MASK`, `HMAC_PSEUDO`, `VAULT_TOKEN`, `REMOVE`, `KEEP`, `GENERALIZE`,
  `FIELD_SEPARATION`
- Asset Kind: `NATIVE`, `FUNGIBLE_TOKEN`, `NON_FUNGIBLE_TOKEN`
- Operation: `TRANSFER`, `CONTRACT_CALL`
- External Status: `SUBMITTED`, `SETTLING`, `SETTLED`, `FAILED`, `SENT_UNKNOWN`,
  `RECONCILIATION_REQUIRED`
- Canonical Execution Field: `chainId`, `recipientAddress`, `assetKind`, `assetSymbol`,
  `assetContractAddress`, `amount`, `operation`, `tokenId`

Runtime DataClass, Provider/Receipt/Finality/Reconciliation status와 active Digital Asset Reason Code의 전체 목록은
manifest의 `enum_sets`를 사용한다. Java enum에 값을 추가하거나 제거하면 Schema와 manifest를 함께 version bump하지
않는 한 contract test가 실패한다.

`DigitalAssetDecision`은 Common `FinalAction`과 별도 타입이다. `ALLOW`와 `TRANSFORM`은 외부전달 조건 관점에서
`PASS`로 정규화하고 Transform 결과는 `transformInstructions`에 별도로 유지한다. `PASS`는 거래 승인 의미가 아니다.

## Canonical JSON과 Digest

`adp-canonical-json/v1`은 다음 규칙을 사용한다.

1. JSON object key를 UTF-16 code unit의 unsigned lexical order로 재귀 정렬한다. Java `String` natural order와
   같으며 DA 구현도 Unicode code point order가 아닌 이 순서를 사용한다.
2. Array 순서는 업무 의미가 있으므로 유지한다.
3. `null` field를 제거하지 않는다.
4. 문자열은 Unicode normalization을 수행하지 않으며 JSON 필수 escape만 적용하고 non-ASCII 문자는 escape하지 않는다.
5. 숫자는 입력 JSON token을 임의 변환하지 않는다. 현재 v1 digest 대상 업무 값은 amount와 version을 포함해 문자열로
   표현하며 숫자 표현 정규화가 필요한 필드는 v1에 추가하지 않는다.
6. UTF-8 compact JSON으로 직렬화한다.
7. SHA-256 결과는 lowercase hex와 `sha256:` prefix로 표현한다.

Manifest `content_digest`는 `content_digest` 자체를 제외한 manifest 전체를 canonicalize해 계산한다. `files[]`의
digest는 각 JSON 파일 전체를 같은 방식으로 계산한다. 이 값은 accidental tamper와 drift 검출용이며 전자서명이나
외부 anchoring을 의미하지 않는다.

DA의 Python canonicalizer는 golden vector의 `canonical`과 `digest`를 모두 재현해야 한다. 특히 supplementary
Unicode key vector는 Python 기본 code point 정렬을 그대로 사용해서는 통과하지 않도록 구성했다.

## JSON 계약

- Runtime wire name은 현재 API와 같은 camelCase다.
- `destinationProfileId`는 환경 중립적인 1~120자 identifier이며 mock destination은 manifest/sample의 baseline 값이다.
- Artifact manifest metadata는 기존 DA/BE Artifact 관례에 따라 snake_case다.
- Amount는 최대 78자리 decimal string이며 Runtime request에서는 0보다 커야 한다.
- Asset conditional field 규칙은 P0-3과 동일하다.
- Provider result는 nullable key도 key 자체는 모두 존재해야 한다.
- `SETTLED`는 transaction hash, receipt success, finalized evidence와 완전한 execution tuple을 요구한다.
- Unknown field는 모든 object boundary에서 fail closed한다.

## DA / FE Handoff

DA는 manifest의 identifier와 enum inventory를 기준으로 Binding/Crosswalk/Schema를 재생성한다. 매핑할 수 없는 DA
후보 값은 `DIGITAL_ASSET_RUNTIME_DATA_CLASS_UNMAPPED` 또는 `DIGITAL_ASSET_CONTRACT_GAP`으로 유지하고 임의의
BE enum으로 치환하지 않는다.

FE는 Runtime request sample과 `$defs/runtime_request`를 DTO/Form 기준으로 사용할 수 있다. 실제 실행 시에는
Swagger의 인증 및 공통 Runtime request contract도 함께 적용한다.

## 현재 경계

DA 후보의 `originator_identity`, `beneficiary_identity`, `counterparty_vasp` 등은 규제 및 Provider별 Source와
의미가 아직 확정되지 않았다. 따라서 `regulatoryOutboundData`는 v1에서 empty object만 허용한다. 후보 필드명을
BE가 추정하여 allowlist로 승격하지 않는다. P0-5에서 검증된 Artifact를 수신한 뒤 P0-7에서 목적지별 required/exact
control과 함께 활성화한다.

P0-4는 DB 상태를 추가하지 않으므로 Flyway migration이 없다. Artifact ingest/persistence와 Runtime Snapshot은
각각 P0-5와 P0-6의 책임이다.

## 검증 증적

- Draft 2020-12 validator로 manifest와 request/result sample 검증
- Published sample을 실제 P0-3 parser로 재검증
- Java enum, canonical field, active reason code와 Schema/manifest inventory equality 검증
- Identifier/version 상수와 manifest equality 검증
- Object insertion order 독립성, null 보존, array order 민감성 검증
- 한글과 supplementary Unicode를 포함한 cross-language golden vector 검증
- Runtime Guard, Fake Connector, manifest의 External Result Schema Version 결속 검증
- Java parser와 Schema의 approved reference 및 execution asset 길이 경계 검증
- Provider amount의 decimal string-only 및 non-final execution tuple 길이 경계 검증
- Manifest content/file digest 재계산 검증
- Asset conditional field와 unknown field negative test
- Common FinalAction에서 Digital Asset Decision으로의 분리 mapping 검증

## Version 변경 규칙

- Enum 추가/삭제, required field 변경, wire name 변경, canonicalization 변경은 기존 v1 파일을 수정해 배포하지 않는다.
- 호환 가능한 설명 보완을 제외한 contract 변경은 새 schema/artifact version과 sample을 추가한다.
- Canonicalization 알고리즘이 바뀌면 artifact version뿐 아니라 `canonicalization_version`도 변경한다.
- P0-5 Loader는 manifest version을 먼저 선택한 뒤 해당 version의 Schema와 digest 규칙을 적용한다.
