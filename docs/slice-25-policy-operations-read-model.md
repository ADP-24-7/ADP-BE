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

목록은 `executionPack`, `lifecycleStage`, `workloadId`, `query`, `actionableOnly`, `limit`, `offset`을 지원한다. 정렬은
`updatedAt DESC, artifactId, artifactVersion`으로 고정한다. `actionableOnly=true`는 Lifecycle Command를 실행할 수 있는
Artifact만 반환하며 각 항목은 `actionable`과 typed `nextAction`을 제공한다. `SUPERSEDED` rollback은 Generic Selection을 사용하는
Pack에서만 Action으로 분류한다.

History는 `transitionLimit`, `shadowLimit`을 각각 기본 100, 최대 200으로 제한한다. 응답의 `transitionTotal`, `shadowTotal`과 각
`hasMore` 필드로 반환 범위 밖의 이력 존재 여부를 표시한다.

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

로컬 통합 Compose는 Vite proxy에서 개발용 Admin Principal을 주입하므로 별도 로그인 화면 없이 실제 BE API를 사용한다. 기본
AI Context에서도 Read Model을 확인할 수 있도록 `AI-POLICY-LOCAL-VALIDATED-001` fixture를 제공하며 운영 profile에서는 로드하지
않는다.

## 현재 범위와 후속

이번 수직 Slice는 Policy와 Digital Asset Lifecycle 탐색을 닫는다. Security Finding, Admin Identity, Workload/Data Access
Read Model은 Slice 25의 후속 수직 Slice로 분리한다. Pack별 Operations Summary·Recovery·Policy Event scope는 Slice 26에서
별도 고정한다.

현재 contains 검색은 `lower(field) like '%query%'`이므로 V41 B-tree index는 Scope 필터와 정렬에 사용되고 검색어 자체를 완전히
최적화하지 않는다. Artifact 규모가 커지면 `pg_trgm` 기반 GIN 또는 prefix 검색 계약을 별도 성능 측정 후 도입한다.
