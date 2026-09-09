# Slice 25 Policy Operations Read Model

## 목적

운영자가 Artifact ID와 Version을 미리 알지 않아도 권한 범위의 Policy Artifact를 목록에서 찾아 Lifecycle 상태와 승인 Evidence를
확인한 뒤 기존 Command API를 사용할 수 있게 한다. Query API는 Command API를 대신하지 않으며 Runtime Policy 선택을 변경하지
않는다.

## API Contract

| Method | Endpoint | 역할 |
| --- | --- | --- |
| `GET` | `/api/admin/policy-lifecycle` | Artifact 목록·검색·Pack/Stage/Attention 필터 |
| `GET` | `/api/admin/policy-lifecycle/{artifactId}/versions/{version}` | 현재 Lifecycle 상세 |
| `GET` | `/api/admin/policy-lifecycle/{artifactId}/versions/{version}/history` | Transition·Shadow Evidence 이력 |

목록은 `executionPack`, `lifecycleStage`, `workloadId`, `query`, `attentionRequired`, `limit`, `offset`을 지원한다. 정렬은
`updatedAt DESC, artifactId, artifactVersion`으로 고정한다. `attentionRequired=true`는 `SHADOW`, `APPROVED`, `REVIEW` 상태만
반환한다.

## Security Boundary

- Reader Role은 `OPERATOR`, `PRIVILEGED_OPERATOR`, `AUDITOR`다.
- Institution과 allowed Workload는 목록 SQL에서 항상 강제한다.
- 상세 History는 동일 scoped Artifact 조회를 통과한 뒤에만 읽는다.
- 다른 Institution 또는 Workload의 상세는 존재 여부를 노출하지 않고 `POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND`로 처리한다.
- `SAAS` Pack과 bounded search 범위를 벗어난 요청은 fail-closed한다.

## FE 연결

Policy 화면은 server-owned 목록에서 Artifact를 선택하고 Transition·Shadow Evidence를 조회한다. 선택된 Artifact만 기존
Governance Command 패널로 전달한다. Pack 변경 시 이전 Pack의 선택을 제거하고 Command 성공 후 목록과 History cache를
무효화한다.

## 현재 범위와 후속

이번 수직 Slice는 Policy와 Digital Asset Lifecycle 탐색을 닫는다. Security Finding, Admin Identity, Workload/Data Access
Read Model은 Slice 25의 후속 수직 Slice로 분리한다. Pack별 Operations Summary·Recovery·Policy Event scope는 Slice 26에서
별도 고정한다.
