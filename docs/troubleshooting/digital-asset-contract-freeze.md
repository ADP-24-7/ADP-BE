# Digital Asset Canonical Contract 트러블슈팅

## 범위

DA-P0-3 리뷰 보완과 DA-P0-4 Canonical Contract Freeze 과정에서 확인한 계약 drift, enum 의미 충돌,
canonical digest 재현성 문제와 해결 결정을 정리한다.

## Nullable과 Optional을 같은 의미로 처리한 문제

- 문제: P0-3 parser는 `assetContractAddress`, `tokenId` 값이 nullable이면서 JSON key는 항상 존재하도록 강제했지만
  OpenAPI에서는 optional로 노출했다. FE가 Schema에 맞게 key를 생략하면 Runtime은 422를 반환했다.
- 해결: 필수 key와 허용 key를 분리하고 asset kind별 constructor invariant를 유지했다. P0-4 JSON Schema도
  `if/then`으로 같은 conditional contract를 표현한다.
- 교훈: `nullable`, `optional`, `conditional`은 서로 다른 계약이며 parser, OpenAPI, JSON Schema를 같이 검증해야 한다.

## Identifier 문자열이 여러 Fixture에 복제된 문제

- 문제: Workload, Purpose, Destination Profile ID/version이 Context Builder, 승인 fixture, Policy fixture,
  Destination fixture에 중복되면 한 곳만 변경해도 DA manifest와 Runtime 선택 결과가 달라질 수 있다.
- 해결: `DigitalAssetCanonicalContract`에 baseline identifier와 contract/schema version을 모으고 기존 Adapter가 이를
  참조하도록 변경했다. Manifest equality test로 외부 계약 drift를 차단한다.
- 교훈: 문서에 적힌 문자열은 Source of Truth가 아니며 실행 코드 상수와 생성/검증 테스트에 결속돼야 한다.

## Common Decision과 Digital Asset Decision의 의미 충돌

- 문제: Common Runtime은 `ALLOW/TRANSFORM/REVIEW/BLOCK`을 사용하지만 DA Handoff는
  `PASS/BLOCK/REVIEW`를 요구한다. `TRANSFORM`을 DA decision으로 노출하면 Transform과 전달 판정이 다시 섞인다.
- 해결: `DigitalAssetDecision`을 별도 타입으로 만들고 `ALLOW/TRANSFORM -> PASS`로 정규화했다. Transform은
  별도 instruction inventory에 남긴다.
- 교훈: 공통 엔진의 내부 Action과 Pack 외부 계약의 Decision은 이름이 비슷해도 같은 타입으로 간주하지 않는다.

## 전역 ObjectMapper에 의존하는 Digest

- 문제: 일반 API용 Jackson 설정, property inclusion, map insertion order가 바뀌면 같은 contract의 digest가
  배포마다 달라질 수 있다.
- 해결: `DigitalAssetCanonicalJson`에서 object key 재귀 정렬, array order 유지, null 포함, UTF-8 compact JSON,
  `sha256:` lowercase hex 규칙을 독립적으로 고정했다.
- 교훈: Artifact digest는 일반 API serialization의 부산물이 아니라 versioned 알고리즘이어야 한다.

## Manifest Digest의 순환 참조

- 문제: Manifest가 자신과 Schema를 모두 보호하려 하면 `content_digest`가 자기 자신을 포함하는 순환이 생긴다.
- 해결: `content_digest`만 제외한 manifest 전체를 digest하고, Schema와 sample은 `files[]`에 각각 canonical digest를
  기록했다. Contract test가 모든 파일을 다시 읽어 검증한다.
- 교훈: logical artifact identity와 immutable content identity를 분리하고 digest 대상 범위를 문서로 고정한다.

## DA 후보 필드를 Runtime 계약으로 추정할 위험

- 문제: DA에는 `originator_identity`, `counterparty_vasp` 같은 후보가 있지만 Source, 법적 의미, Provider mapping이
  확정되지 않았다. 이름만 보고 BE allowlist에 추가하면 분석 후보가 운영 전송 계약으로 승격된다.
- 해결: P0-4 v1은 `regulatoryOutboundData`를 empty-only로 유지하고 `UNMAPPED/CONTRACT_GAP` reason namespace를
  공개했다. 실제 필드는 P0-5 Artifact validation과 P0-7 destination control을 통과한 뒤 활성화한다.
- 교훈: DA Candidate는 근거 입력이며 BE Runtime enum/schema를 직접 확장하는 명령이 아니다.

## Legacy Reason Code가 Active 계약에 섞이는 문제

- 문제: 기존 KYC/AML/Wallet review reason은 역사적 Evidence 보존을 위해 Java enum에 남아 있지만 최신 Active DA
  Runtime 책임에는 포함되지 않는다.
- 해결: 전체 `ReasonCode`를 Schema에 노출하지 않고 `ACTIVE_REASON_CODES` inventory를 별도로 고정했다.
- 교훈: DB 호환을 위한 retained enum과 신규 요청에서 생성 가능한 active enum을 구분한다.

## Schema가 Java보다 강하거나 느슨해지는 문제

- 문제: Schema만 수작업으로 관리하면 nullable key, finality, asset kind 조건이 실제 parser와 다르게 변할 수 있다.
- 해결: Draft 2020-12 validation에 더해 sample을 `DigitalAssetRuntimeInput`과 `ExternalExecutionResult` parser로 다시
  통과시키고 Java enum과 Schema enum set equality를 검증한다.
- 교훈: Schema 문법 검증만으로 producer contract 일치는 증명되지 않는다. 실제 parser와 양방향으로 묶어야 한다.

## External Result Schema Version이 Runtime과 Manifest에서 달라진 문제

- 문제: Manifest는 `digital-asset-external-result/v1`을 공개했지만 Fake Connector와 Response Guard는 과거 문자열인
  `digital-asset-external-execution-result/v1`을 사용했다. Schema와 sample 테스트만 통과해 실제 Runtime drift를
  발견하지 못했다.
- 해결: Connector와 Guard가 `DigitalAssetCanonicalContract.EXTERNAL_RESULT_SCHEMA_VERSION`을 직접 참조하게 하고,
  실제 Connector 결과가 Guard를 통과하며 manifest 값과도 같은지 검증하는 테스트를 추가했다.
- 교훈: 계약 테스트는 파일끼리의 일관성뿐 아니라 실제 producer와 consumer boundary를 함께 실행해야 한다.

## Schema 길이 제한이 Domain Parser보다 넓었던 문제

- 문제: 승인 거래 참조와 SETTLED execution tuple의 chain, symbol, token ID가 Schema에서는 Java Domain보다 긴 값을
  허용해 Schema PASS 이후 Runtime 422가 발생할 수 있었다.
- 해결: 공통 `$defs`로 identifier 길이를 고정하고 request/result Schema에서 재사용했다. 최대 길이와 최대 길이 + 1을
  Schema와 실제 parser 양쪽에 통과시켜 동일 경계를 검증한다.
- 교훈: 정상 sample 하나로는 Schema가 parser보다 느슨한지 증명할 수 없으므로 boundary negative test가 필요하다.

## Baseline Fixture를 전역 타입 계약으로 고정한 문제

- 문제: local mock destination ID를 Runtime Schema의 `const`로 두면 실제 Provider destination이 추가될 때 Java는
  지원해도 v1 Schema가 거부한다.
- 해결: destination ID 타입은 1~120자 환경 중립 identifier로 정의하고 mock ID는 manifest와 sample의 baseline
  compatibility 값으로만 유지했다.
- 교훈: fixture identity와 wire type contract를 분리해야 계약 버전을 불필요하게 올리지 않는다.

## 언어별 문자열 정렬 차이로 Digest가 달라질 수 있는 문제

- 문제: Java 문자열 정렬과 Python 문자열 정렬은 supplementary Unicode key에서 순서가 달라질 수 있다.
- 해결: v1 정렬을 UTF-16 code unit lexical order로 명시하고 null, array, 한글, supplementary key를 포함한 golden
  vector를 공개했다. Enum inventory도 Java 선언 순서와 exact list equality로 검증한다.
- 교훈: canonicalization 설명만으로 cross-language 재현성을 주장하지 않고 입력, canonical bytes, digest vector를
  함께 배포해야 한다.

## Provider Result의 상태별 Wire 검증이 달랐던 문제

- 문제: External Result Schema는 amount를 decimal string으로 제한했지만 Java parser는 Number도 허용했다. 또한
  chain ID, asset symbol, token ID 길이는 SETTLED에서만 검증해 SETTLING 등 non-final 결과가 Schema와 다르게
  처리될 수 있었다.
- 해결: Provider 결과 parser가 `DigitalAssetAmount.fromWire()`를 사용하도록 변경하고, execution tuple의 길이 제한을
  상태와 무관하게 입력 단계에서 적용했다. SETTLED에서는 완전성, receipt, finality, asset kind 의미 검증을 추가로
  수행한다.
- 교훈: Wire type과 개별 필드 제약은 상태 전이 검증보다 먼저 적용하고, final 상태는 그 위에 semantic invariant를
  추가해야 한다.
