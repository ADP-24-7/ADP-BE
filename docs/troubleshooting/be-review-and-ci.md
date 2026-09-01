# BE 리뷰 및 CI 트러블슈팅

## 목적

README의 완료 기준과 별도로, 리뷰 과정에서 실제로 문제가 됐던 지점과 해결 결정을 추적한다. 이 문서는 BE-0부터 BE-5까지의 커밋 흐름과 리뷰 대응을 기준으로 작성한다.

## BE-0

기준 커밋: `bfb912e chore: bootstrap BE-0 Spring Boot service (#1)`

- 문제: runtime contract, trace/idempotency/error response 같은 기본 계약이 흩어지면 이후 단계에서 API 의미가 흔들릴 수 있었다.
- 조치: Spring Boot 3/Java 21 기반 service skeleton, request/trace/idempotency/error/reason code baseline, health check, Flyway baseline, Docker Compose E2E 검증을 고정했다.
- 교훈: 이후 단계에서 API를 확장하더라도 request/trace/idempotency/error contract는 되도록 변경하지 않는다.

## BE-1

기준 커밋: `43e7c3c feat: implement BE-1 authentication authorization (#2)`

- 문제: runtime service credential과 admin/local user stub의 인증 경계가 섞이면 운영 환경에서 local-only 경로가 열릴 수 있었다.
- 조치: `X-ADP-API-Key` 기반 service principal 인증, API key hash lookup, RBAC/context permission, local user header stub opt-in 설정을 분리했다.
- 교훈: fixture와 운영 기본값은 항상 분리하고, local convenience는 명시적인 property 없이는 활성화하지 않는다.

## BE-2

기준 커밋: `2cfe418 feat: implement BE-2 internal data access core (#3)`

- 문제: 자유 SQL이나 field allowlist 누락은 retrieval 단계에서 원문 데이터 과다 노출로 이어질 수 있었다.
- 조치: workload registry, retrieval profile, dataset/field allowlist, subject/purpose/time-window/row-limit guard, predefined adapter만 허용하는 구조를 추가했다.
- 교훈: audit에는 원문을 남기지 않고 digest/metadata 중심으로 남긴다. 선택되지 않은 field는 downstream에 넘기지 않는다.

## BE-3

기준 커밋: `7db3734 feat: implement BE-3 context detection (#4)`

- 문제: Runtime DataClass의 Source of Truth가 DA artifact/raw payload 쪽으로 밀리면 BE field catalog와 policy binding 검증이 약해질 수 있었다.
- 조치: Canonical Context builder에서 서버 Field Catalog 기반 DataClass metadata를 부여하고, raw value 대신 value digest만 API에 노출했다.
- 문제: DA Policy Candidate에는 한국어 category가 포함될 수 있어 BE enum/crosswalk 경계가 필요했다.
- 조치: BE는 runtime data class를 내부 enum으로 유지하고, DA 한글 category는 이후 crosswalk/contract 경계에서 다루도록 분리했다.
- 교훈: DA가 제공하는 분석 언어와 BE runtime data class는 같은 개념이 아니므로 ingestion/crosswalk 경계를 둔다.

## BE-4

기준 커밋: `a95f500 feat: start BE-4 policy decision core (#5)`

- 문제: `PolicyAction`과 `FinalAction`을 같은 타입으로 다루면 DA policy disposition과 BE runtime final decision이 섞인다.
- 조치: 두 타입을 분리하고, DA handoff disposition을 BE policy action으로 정규화하는 경계를 추가했다.
- 문제: DA artifact identity와 BE policy snapshot identity를 같은 version/digest로 강제하면 provenance가 깨진다.
- 조치: DA source artifact ref와 BE snapshot digest/version/effective_at을 분리했다.
- 문제: `processingContext` 단수 문자열 모델은 DA `processing_contexts[]` 계약과 맞지 않았다.
- 조치: processing contexts를 복수 모델로 바꾸고, 미입력은 `NOT_APPLICABLE`이 아니라 `INCOMPLETE`로 fail-closed 처리했다.
- 문제: `/v1/runtime/executions`가 mock flag에 묶이면 FE/Business App 단일 runtime path 계약이 깨진다.
- 조치: controller/service는 mock flag에서 분리하고 provider/policy adapter만 환경별로 분리했다.
- 문제: 존재하지 않는 execution id 조회가 404가 아니라 500이 될 수 있었다.
- 조치: `RuntimeExecutionNotFoundException`과 공통 error response mapping을 추가했다.
- 교훈: Runtime API는 mock harness가 아니라 운영 ingress이므로 feature flag로 닫히면 안 된다.

## BE-5

기준 커밋:

- `3822de4 feat: add BE-5 transform vault baseline`
- `4128b55 fix: harden BE-5 transform vault contracts`
- `c87caad fix: close BE-5 transform wiring gaps`
- `b012902 fix: enforce BE-5 transform invariants`

### HMAC이 실제 HMAC이 아니었던 문제

- 문제: 초기 `HMAC_PSEUDO`는 `SHA256("HMAC_PSEUDO:" + valueDigest)`였고 keyed hash가 아니었다.
- 조치: `PseudonymizationKeyPort`와 `HmacSHA256` 기반 pseudonymization으로 변경했다.
- 추가 조치: local fixture key는 stable/versioned map으로 제한하고, 기본 환경은 unconfigured key provider로 fail-closed 처리했다.

### Transform resolver 계약이 좁았던 문제

- 문제: `DataClass -> TransformStrategy`만 받는 resolver로는 workload/purpose/provider/policy/snapshot/field 차이를 수용할 수 없었다.
- 조치: `TransformResolutionContext`와 `TransformInstruction`으로 확장했다.
- 추가 조치: default 환경은 unconfigured resolver, local fixture 환경만 provisional resolver를 사용하도록 wiring을 분리했다.

### Instruction 재현성 누락

- 문제: field별로 strategy만 저장하면 어떤 version/key/mapping/parameter로 변환했는지 재현할 수 없었다.
- 조치: `strategyVersion`, `keyVersion`, `mappingVersion`, `instructionDigest`를 `TransformFieldResult`와 `runtime.transform_field`에 저장했다.
- 추가 조치: `instructionDigest`에는 strategy, strategy version, key version, mapping version, TTL, canonical parameters를 포함한다.

### 잘못된 parameter가 정상 transform으로 숨겨지던 문제

- 문제: `GENERALIZE.bucketSize=0`, `MASK.visibleSuffix=-1`, 잘못된 integer parameter가 정상 `APPLIED`로 끝날 수 있었다.
- 조치: instruction validation을 추가하고 잘못된 policy parameter는 `TransformResolutionException`으로 fail-closed 처리했다.
- 추가 조치: `GENERALIZE` 입력값 자체가 숫자가 아니면 `TransformInputException`으로 실패시킨다.

### Runtime 상태가 `DECIDED`에 머물던 문제

- 문제: transform이 성공하고 connector까지 실행되어도 runtime status가 `DECIDED`였다.
- 조치: transform 성공 후 `TRANSFORMED`로 전환하고 API/trace 테스트로 고정했다.

### Vault token TTL/lineage/concurrency

- 문제: 만료 token을 그대로 재사용하거나 기존 row를 overwrite하면 감사 이력이 사라진다.
- 조치: `ACTIVE`/`EXPIRED` lifecycle과 partial unique index를 추가했다.
- 문제: expired row에 `replaced_by_token_ref`를 먼저 기록하고 새 token insert가 실패하면 lineage가 깨질 수 있다.
- 조치: expire stale mapping과 active mapping insert를 transaction template으로 묶고, duplicate conflict 시 winner token을 재조회한다.
- 추가 조치: same scope reuse, different scope isolation, expired token refresh, concurrent same mapping, concurrent expired replacement 테스트를 추가했다.

### Vault boundary

- 문제: 현재 `vault.token_mapping`은 `token_ref`와 `source_value_digest`를 연결하므로 reversible token vault가 아니다.
- 조치: BE-5 문서에 현재 구현을 scope-aware opaque token registry baseline으로 명시했다.
- 교훈: 원문 복원이 필요한 reversible vault는 별도 보안 저장소와 KMS 기반 encrypted mapping이 필요하다.

### Vault/HMAC scope isolation

- 문제: scope가 `snapshotDigest:dataClass`에만 의존하면 workload/purpose/provider가 다른 실행 사이에서 같은 token/HMAC이 나올 수 있다.
- 조치: workload, purpose, provider profile, data class를 canonical hash한 `TransformScope.scopeId`를 도입했다.
- 추가 조치: Vault mapping scope와 HMAC message 모두 `TransformScope.scopeId`로 domain separation한다.
- 설계 결정: `policyVersion`과 `snapshotDigest`는 token namespace가 아니라 audit provenance로 둔다. 정책 evidence나 snapshot만 바뀌어도 token/HMAC이 회전하면 continuity가 깨지기 때문이다.

### Privacy-safe API

- 문제: `privacySafeOutput`에 `sourceValueDigest`를 내려주면 낮은 entropy 값의 dictionary 추측과 실행 간 correlation 위험이 생긴다.
- 조치: 외부 응답은 `path`, `dataClass`, `strategy`만 노출하고 source/transformed digest와 token mapping 내부정보는 DB persistence에만 남긴다.

### CI와 local DB 이슈

- 문제: 기존 compose Postgres에는 과거 migration checksum이 남아 fresh migration 검증과 다르게 실패할 수 있었다.
- 조치: PR 검증은 fresh Postgres 컨테이너로 수행하고, default context load는 local fixture 없이 별도 실행했다.
- 문제: wiring test를 `@SpringBootTest` nested class로 만들면 전체 테스트에서 불필요한 DB connection을 추가로 열어 connection limit에 걸릴 수 있었다.
- 조치: `ApplicationContextRunner` 기반 bean wiring test로 바꿔 DB 없이 default/local fixture 조건을 검증한다.

## BE-6로 이관한 항목

- Connector가 `TransformResult`에 직접 결합된 구조는 BE-6에서 `OutboundCandidatePayload`와 Outbound Guard로 분리한다.
- 현재 transform output digest는 transform result metadata digest이며, BE-6 baseline은 outbound candidate metadata digest만 생성한다. 실제 provider payload canonical digest는 Pack별 schema mapper가 추가될 때 별도 구현한다.
- DA 실제 transform mapping artifact loader와 Privileged Re-map/Audit는 BE-5 후속 완료 항목으로 남긴다.
