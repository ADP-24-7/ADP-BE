# BE-11B Audit Read Model & Evidence Export

BE-11B는 기존 Runtime, Audit, Recovery 증적을 운영자가 검색하고 실행 단위로 내보낼 수 있는 privacy-safe read model을 제공한다. 이 Slice는 Policy Lifecycle, Artifact Drift, Rollback Evidence까지 포함하는 BE-11 전체 완료를 의미하지 않는다.

## API

- `GET /api/admin/audit/executions`: `OPERATOR` 전용 실행 검색
- `GET /api/admin/audit/executions/{executionId}/evidence`: `PRIVILEGED_OPERATOR` 전용 Evidence Pack

검색은 인증 Principal의 Institution과 workload scope로 항상 제한하며, 선택적인 Workload filter는 이 권한 범위 안에서만 적용한다. Evidence 단건 조회도 Institution과 workload predicate를 SQL에 포함해 다른 권한 범위의 데이터를 애플리케이션 계층으로 읽지 않고 not found로 처리한다.

## Evidence Contract

`adp-execution-evidence/v1`은 다음 증적을 조립한다.

- 실행 식별자, Request/Trace ID, Workload, Purpose, Runtime Status
- Approval, Policy Snapshot, Decision version/digest
- Subject, Input, Context와 Field Set의 digest/count
- Destination Profile, Outbound, Connector, Response Guard, Controlled Delivery digest/status
- Recovery status, attempt, external status query evidence digest
- Audit ID, reason code, evidence reference
- 조회 시점 Export Content의 SHA-256 fingerprint

Prompt, Subject 원문, Idempotency Key, Provider Correlation Key, Provider Request/Response 원문은 포함하지 않는다.

`exportContentDigest`는 digest 필드를 `null`로 둔 고정 DTO의 Jackson JSON 직렬화 결과를 SHA-256으로 계산한 조회 시점 content fingerprint다. 서명, 외부 anchoring 또는 DB 변조 탐지 기능을 제공하지 않으며 tamper-evident Evidence Bundle로 해석하지 않는다.

Audit Event는 결정론적으로 재사용될 수 있는 `decision_id`가 아니라 `execution_id` FK로 Runtime Execution에 직접 연결한다. 따라서 동일한 Decision을 공유하는 별도 실행도 각 실행에서 생성된 Audit Event만 Evidence Pack에 포함한다.

## Deferred Gates

- DA Artifact Lifecycle, Drift, Approval, Rollback Evidence
- Denied request attempt evidence
- 서명된 Evidence Bundle과 장기 보관 정책
- Recovery queue depth/age 및 Audit completeness 운영 지표
