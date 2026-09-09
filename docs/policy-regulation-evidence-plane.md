# Policy · Regulation · Evidence Plane

## 목적과 경계

DA의 산업·정책 분석을 Runtime Rule과 분리된 versioned Reference Evidence로 수용한다. BE는 DA Notebook이나 Markdown을
직접 읽지 않으며 strict JSON Schema, 개별 Evidence digest와 Bundle digest를 검증한 결과만 Institution namespace에 저장한다.

`REFERENCE_ONLY`는 제품에서 조회할 수 있는 분석 참고자료라는 뜻이며 `ACTIVE` Policy나 Runtime 허용 근거가 아니다. 해당
Evidence를 Runtime 통제에 사용하려면 별도의 Policy Artifact, 승인, Current Selection 절차가 필요하다.

## DA Handoff Contract

- Schema version: `adp-reference-evidence-bundle/v1`
- Trusted schema: `src/main/resources/contracts/reference-evidence-bundle-v1.schema.json`
- Bundle identity: `bundle_id + bundle_version + content_digest`
- Evidence identity: `evidence_id + evidence_version + content_digest`
- Canonical form: UTF-8, object key 정렬, 공백 없는 JSON

BE는 unknown field, enum 위반, identity 중복, count 불일치, 기간 역전, digest 불일치를 fail-closed한다. `REFERENCE_ONLY`에
`policy_artifact_refs`가 포함되면 자동 적용 오해를 막기 위해 ingestion을 거부한다.

## API

| Method | Endpoint | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/admin/reference-evidence/bundles` | PRIVILEGED_OPERATOR | DA Bundle 검증 및 idempotent ingestion |
| `GET` | `/api/admin/reference-evidence` | OPERATOR, PRIVILEGED_OPERATOR, AUDITOR | Evidence 목록·검색·Mapping 필터 |
| `GET` | `/api/admin/reference-evidence/{evidenceId}/versions/{version}` | OPERATOR, PRIVILEGED_OPERATOR, AUDITOR | Evidence 상세 조회 |

목록 API는 `evidenceType`, `status`, `workloadId`, `policyArtifactRef`, `query`, `limit`, `offset`을 지원한다. 모든 조회는
Principal의 Institution과 Workload scope를 SQL에서 강제하며 원문 문서 대신 bounded claim summary와 provenance만 반환한다.

## Persistence

V40은 다음 테이블을 추가한다.

- `evidence.reference_evidence_bundle`
- `evidence.reference_evidence`
- `evidence.reference_evidence_workload`
- `evidence.reference_evidence_policy_artifact`

같은 Bundle identity와 digest의 재전달은 `replayed=true`로 반환한다. 같은 identity에 다른 digest가 들어오거나 다른 Bundle이
기존 Evidence identity를 덮어쓰려 하면 conflict로 차단한다. 기존 Evidence row와 Runtime Snapshot은 재해석하지 않는다.

## Deferred

- 승인된 Policy Artifact와의 별도 maker-checker mapping command
- Cursor pagination과 중앙 운영자용 cross-tenant 조회
- NCP Object Storage reference ingestion
- FE Policy & Evidence 화면 연결
