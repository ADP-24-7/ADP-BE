# BE-11B Audit Read Model & Evidence Export

BE-11B는 기존 Runtime, Audit, Recovery 증적을 운영자가 검색하고 실행 단위로 내보낼 수 있는 privacy-safe read model을 제공한다. 이 Slice는 Policy Lifecycle, Artifact Drift, Rollback Evidence까지 포함하는 BE-11 전체 완료를 의미하지 않는다.

## API

- `GET /api/admin/audit/executions`: `OPERATOR` 전용 실행 검색
- `GET /api/admin/audit/executions/{executionId}/evidence`: `PRIVILEGED_OPERATOR` 전용 Evidence Pack

검색은 인증 Principal의 Institution으로 항상 제한한다. Workload filter가 지정되면 Principal의 workload scope도 검증한다. Evidence 단건 조회는 다른 Institution 또는 허용되지 않은 Workload의 존재를 노출하지 않고 not found로 처리한다.

## Evidence Contract

`adp-execution-evidence/v1`은 다음 증적을 조립한다.

- 실행 식별자, Request/Trace ID, Workload, Purpose, Runtime Status
- Approval, Policy Snapshot, Decision version/digest
- Subject, Input, Context와 Field Set의 digest/count
- Destination Profile, Outbound, Connector, Response Guard, Controlled Delivery digest/status
- Recovery status, attempt, external status query evidence digest
- Audit ID, reason code, evidence reference
- Evidence Pack 전체의 canonical SHA-256 digest

Prompt, Subject 원문, Idempotency Key, Provider Correlation Key, Provider Request/Response 원문은 포함하지 않는다.

## Deferred Gates

- DA Artifact Lifecycle, Drift, Approval, Rollback Evidence
- Denied request attempt evidence
- 서명된 Evidence Bundle과 장기 보관 정책
- Recovery queue depth/age 및 Audit completeness 운영 지표
