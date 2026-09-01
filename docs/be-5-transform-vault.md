# BE-5 Transform Engine & Vault

## Scope

BE-5는 `RuntimeDecision.finalAction=TRANSFORM` 이후 Connector 전달 전에 privacy-safe payload를 생성하는 단계다. Runtime API는 FE/Business App에 단일 `POST /v1/runtime/executions` 진입점을 유지하고, detection/decision/transform/connector 세부 오케스트레이션은 BE 내부에서 수행한다.

## Upstream Check

- ADP-DA 최신 `main`에서는 Raw/Mask/HMAC/Vault Token 처리기법 비교와 handoff 방향은 문서화되어 있으나, workload별 최종 transform mapping artifact는 아직 코드 계약으로 고정되어 있지 않다.
- 따라서 BE는 strategy enum과 context-aware resolver port를 먼저 고정하고, 현재 mapping은 local fixture 조건에서만 `ProjectProvisionalTransformStrategyResolver`에 둔다.
- ADP-FE 최신 `main`은 Runtime Execution API와 trace stage 표시를 사용한다. BE는 기존 응답 필드를 유지하면서 `privacySafeOutput`을 추가 필드로 확장한다.

## Runtime Flow

1. Runtime request 수신
2. Retrieval Profile 기반 data access
3. Canonical Context 조립
4. Policy Snapshot selection
5. Runtime Decision 생성
6. Transform Engine 실행
7. Connector 실행
8. Audit 기록

## Strategy Baseline

- `MASK`: `visibleSuffix` parameter 기반 문자열 마스킹
- `HMAC_PSEUDO`: key provider port에서 받은 key material로 HMAC-SHA256 pseudonym 생성
- `VAULT_TOKEN`: `vault.token_mapping` token reference 생성/재사용
- `REMOVE`: payload에서 값 제거
- `KEEP`: 허용된 값만 runtime memory에서 유지
- `GENERALIZE`: `bucketSize` parameter 기반 금액 등 연속값 일반화
- `FIELD_SEPARATION`: 분리 저장/전달 대상 digest 생성

## Persistence

- `runtime.transform_execution`: execution/decision 단위 transform 실행 기록
- `runtime.transform_field`: field 단위 strategy, strategy version, key version, mapping version, instruction digest, source/transformed digest, token metadata 기록
- `vault.token_mapping`: mapping scope, data class, source digest, token ref, key version, mapping version, lifecycle status 저장
- raw value는 DB와 API 응답에 저장하거나 노출하지 않는다.
- `privacySafeOutput`은 source digest와 field-level transformed digest를 외부 응답으로 내리지 않는다.
- 만료된 token은 재사용하지 않고 기존 row를 `EXPIRED`로 보존한 뒤 새 `ACTIVE` row를 발급한다.
- 기본 환경에서 transform mapping이 구성되지 않으면 fail closed 처리한다.
- Vault/HMAC scope는 workload, purpose, provider profile, data class를 canonical hash한 `TransformScope.scopeId`로 격리한다.
- `policyVersion`과 `snapshotDigest`는 token namespace가 아니라 audit provenance로 유지한다.
- 모든 instruction은 `strategyVersion`, `keyVersion`, `mappingVersion`을 공통 재현성 identity로 가진다.
- `instructionDigest`는 strategy, strategy version, key version, mapping version, TTL, canonical parameters를 포함한다.
- 잘못된 strategy parameter는 fallback output으로 숨기지 않고 fail closed 처리한다.

## Remaining Work

- Privileged Re-map API
- TTL 만료 정책
- Vault 장애 정책
- DA 실제 transform mapping artifact loader
- BE-6 Outbound Guard에서 `OutboundCandidatePayload`와 outbound candidate metadata digest 도입. 실제 provider payload canonical digest는 Pack별 schema mapper 단계에서 구현한다.

## Boundary

현재 BE-5 Vault baseline은 reversible token vault가 아니라 scope-aware opaque token registry다. `vault.token_mapping`은 raw 원문을 저장하지 않고 `token_ref`와 `source_value_digest`를 연결한다. 향후 원문 복원이 필요한 reversible vault가 요구되면 별도 보안 저장소와 KMS 기반 encrypted mapping을 추가해야 한다.
