# BE-5 Deep Dives

## Default Wiring Fail-Closed

현상: 기본 설정에서 Spring context가 transform resolver/key provider wiring 문제로 깨질 수 있었다.

재현 조건: `adp.local-fixtures.enabled=false` 또는 설정 미지정 상태에서 `AdpBeApplicationTests.contextLoads()` 실행.

원인: provisional resolver/key provider가 local fixture 전용이어야 하는데, fallback bean 조건과 fixture property 조합이 명확하지 않았다. CI에 `ADP_LOCAL_FIXTURES_ENABLED=true`를 넣으면 초록색으로 만들 수는 있지만 운영 기본 환경 부팅 실패를 숨기게 된다.

선택한 해결책: 기본 환경은 `UnconfiguredTransformStrategyResolver`와 `UnconfiguredPseudonymizationKeyAdapter`로 정상 부팅하되, 실제 transform mapping/key 사용 시 fail-closed 되도록 했다. local fixture 환경에서만 provisional resolver와 stable versioned key를 활성화했다.

검증: `ApplicationContextRunner`로 default/local fixture wiring을 DB 없이 검증하고, 별도로 local fixture 없는 default context load 테스트를 실행했다.

## Vault Concurrent Replacement

현상: 만료 token 교체 중 old row를 먼저 `EXPIRED` 처리하고 new token insert가 실패하면 `replaced_by_token_ref`가 존재하지 않는 token을 가리킬 수 있었다.

재현 조건: expired ACTIVE row가 있는 상태에서 동시에 여러 요청이 같은 mapping을 refresh하는 경우.

원인: lineage update와 new ACTIVE token insert가 하나의 원자적 작업으로 묶이지 않으면 중간 실패 또는 race condition에서 감사 이력이 깨질 수 있다.

고려한 대안: advisory lock, row lock 기반 `SELECT FOR UPDATE`, partial unique index와 transaction 조합.

선택한 해결책: ACTIVE row uniqueness는 partial unique index로 DB가 최종 arbiter가 되게 하고, expired row update와 new ACTIVE insert는 transaction template으로 묶었다. duplicate conflict가 발생하면 winner token을 재조회한다.

검증: concurrent same mapping, concurrent expired replacement 테스트에서 모든 요청이 하나의 ACTIVE token으로 수렴하고 expired row의 `replaced_by_token_ref`가 실제 winner token을 가리키는지 확인했다.

## HMAC Scope Isolation

현상: 단순 `HMAC(key, valueDigest)`는 같은 key version을 공유하는 workload/purpose 사이에서 같은 pseudonym을 만들 수 있었다.

위험: 업무 A와 업무 B가 같은 원문 값을 처리할 때 HMAC 결과가 같으면 cross-context linkability가 생긴다.

선택한 해결책: workload, purpose, provider profile, data class를 canonical hash한 `TransformScope.scopeId`를 HMAC message에 포함했다.

Trade-off: `policyVersion`과 `snapshotDigest`는 token namespace에서 제외했다. 이 값들은 왜 해당 transform이 선택됐는지 설명하는 audit provenance이며, tokenization 자체와 무관한 policy evidence 변경만으로 token continuity가 끊기면 운영 복잡도가 커진다.

검증: purpose가 다르면 Vault token/HMAC이 달라지고, policy/snapshot만 달라지면 동일 namespace를 유지하는 테스트를 추가했다.
