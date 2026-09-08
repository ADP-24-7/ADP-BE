# Policy Replay / Shadow Evidence 트러블슈팅

## Privacy-safe Runtime은 과거 입력 원문을 Replay할 수 없다

Runtime은 subject와 payload 원문을 보관하지 않으므로 execution ID만으로 과거 Policy를 다시 평가할 수 없다. 이를 우회해
관리자 API가 raw input이나 candidate outcome을 받으면 개인정보 최소화와 server-owned 판단 경계가 모두 깨진다.

Shadow API는 caller에게 Evaluation Case ID만 받고, versioned case와 결과는 `PolicyShadowEvaluator`가 소유하도록 분리했다.
동일 Case Version과 Input Digest가 아니면 Evidence를 저장하지 않는다. 실제 운영 case adapter가 없으면 fixture나 caller 값을
대신 신뢰하지 않고 fail-closed한다.

## ACTIVE baseline을 최신 1건으로 임의 선택하면 안 된다

Lifecycle Skeleton DB는 모든 Policy Scope에 대해 단일 ACTIVE를 아직 강제하지 않는다. `order by updated_at limit 1`로 조회하면
잘못된 중복 ACTIVE 상태를 숨기고 Candidate를 임의 baseline과 비교할 수 있다.

Baseline scope를 Institution + Policy Layer + Execution Pack + Workload + Purpose로 고정하고 최대 2건을 조회한다. 결과가
정확히 1건이 아니면 `POLICY_SHADOW_BASELINE_NOT_FOUND` 또는 `POLICY_SHADOW_BASELINE_AMBIGUOUS`로 중단한다.

Evidence FK도 Artifact ID/Version만 참조하면 수동 SQL에서 다른 digest나 scope를 결합할 수 있다. V33은 Artifact digest,
workload, purpose까지 포함한 복합 FK로 ACTIVE와 Candidate Evidence binding을 DB에서 강제한다.

## JdbcClient와 PostgreSQL JSONB 물음표 연산자 충돌

통합 테스트에서 `diff_fields ? 'FINAL_ACTION'`을 사용하자 Spring `JdbcClient`가 `?`를 bind placeholder로 해석해
`InvalidDataAccessApiUsageException`이 발생했다. 동일 의미의 `jsonb_exists(diff_fields, 'FINAL_ACTION')`를 사용해 SQL 의미와
parameter parsing을 명확히 분리했다.

## Shadow 실행에서 Connector 호출을 구조적으로 차단

Shadow Service는 Runtime Connector나 Egress Port를 의존하지 않고 Lifecycle Persistence, server-owned Evaluator, Evidence
Persistence만 의존한다. E2E 테스트는 실행 전후 `runtime.connector_execution` row count가 동일한지 검증한다.
