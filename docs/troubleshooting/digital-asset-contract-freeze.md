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
