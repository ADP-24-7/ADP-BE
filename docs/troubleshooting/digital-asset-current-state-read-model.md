# Digital Asset Current State Read Model 트러블슈팅

## Ingestion Stage를 현재 Lifecycle로 사용하지 않는다

### 문제

`policy.digital_asset_artifact_ingestion.lifecycle_stage`는 Artifact가 검증을 통과해 등록된 시점의
`CANDIDATE` 상태를 보존한다. 이후 Replay, Shadow, Approval, Active 전이는
`policy.lifecycle_artifact.lifecycle_stage`에서 진행된다. Ingestion 값을 현재 상태로 표시하면 실제 ACTIVE
Artifact도 CANDIDATE로 보인다.

### 해결

Current State Read Model은 Ingestion과 Lifecycle Artifact를 Institution, Artifact ID, Version으로 결합하고,
현재 Lifecycle은 `policy.lifecycle_artifact`만 authoritative source로 사용한다. Ingestion 테이블은 Manifest,
Canonical Contract, Runtime Control, Crosswalk와 적재 시점 메타데이터를 제공하는 역할로 제한한다.

## ACTIVE 문자열과 Current Selection을 같은 의미로 보지 않는다

### 문제

Lifecycle이 ACTIVE여도 `policy.digital_asset_active_artifact`의 Workload 단일 선택과 Digest, Purpose가
일치하지 않으면 Runtime이 사용할 수 있는 Current Artifact가 아니다. 반대로 Active Selection row만 존재하고
Lifecycle이나 Digest가 어긋난 상태를 정상 CURRENT로 표시하면 운영자가 stale selection을 신뢰하게 된다.

### 해결

Read Model은 다음 조건이 모두 일치할 때만 `CURRENT`를 반환한다.

- Institution, Workload, Artifact ID, Version
- Artifact Digest와 Purpose
- Lifecycle Stage `ACTIVE`

선택 row 또는 ACTIVE Lifecycle 중 하나가 존재하지만 위 조건이 맞지 않으면 `INCONSISTENT`, 둘 다 아니면
`NOT_CURRENT`로 반환한다. `currentOnly=true`는 `CURRENT`만 조회한다.

## 최신 실행은 Runtime Snapshot으로 Artifact에 연결한다

### 문제

`runtime_execution`의 Workload만으로 Artifact 실행 이력을 추정하면 활성 버전 교체 전후 실행을 구분할 수 없다.
현재 Active Artifact가 과거 실행에도 사용됐다고 잘못 표시할 수 있다.

### 해결

실행 이력은 요청 시작 시 저장된 `runtime.digital_asset_runtime_snapshot`의 Artifact ID, Version, Digest를 기준으로
연결한다. 최신 Snapshot에서 Runtime 상태, 승인 Policy Snapshot, Destination Profile, Post-execution Evidence를
조회하고 Decision Trace와 Audit Evidence 경로를 함께 반환한다. 원본 Provider 응답이나 민감 값은 반환하지 않는다.

## 목록, count, 상세에 동일한 Scope를 적용한다

### 문제

목록 결과만 Workload로 필터링하고 count나 상세 조회를 별도로 처리하면 페이지 개수 또는 Artifact 존재 여부를 통해
권한 밖 상태가 노출된다.

### 해결

Institution과 호출 Principal의 `allowedWorkloads` 조건을 목록 SELECT, count, 상세에 동일하게 적용한다.
빈 Workload Scope는 목록 0건과 상세 404로 fail-closed한다. FE는 이 서버 소유 Scope 결과만 표시한다.

Count 쿼리는 Scope와 Current Selection 판정에 필요한 Ingestion, Lifecycle, Active Selection만 결합한다.
최신 Runtime Snapshot과 Post-execution Evidence를 찾는 LATERAL JOIN은 실제 목록과 상세 조회에만 적용해,
페이지 개수 계산이 Artifact별 Runtime 이력 탐색 비용을 발생시키지 않도록 한다.

`runtimeExecutionCount`는 Snapshot row 개수를 사용한다. V28의 `UNIQUE(execution_id)`가 실행 한 건당 Runtime
Snapshot 한 건만 허용하므로 이 값은 Artifact를 사용한 실행 건수와 동일하며, Flyway 테스트에서 해당 제약을
명시적으로 검증한다.

## 통합 테스트 Snapshot Digest는 실행별로 고유해야 한다

### 문제

`runtime.digital_asset_runtime_snapshot.snapshot_digest`는 전역 unique다. 서로 다른 Scope 테스트가 같은 고정
Digest를 사용하면 개별 테스트는 통과해도 전체 Suite에서 fixture 간 충돌이 발생한다.

### 해결

테스트마다 64자리 고유 SHA-256 형식 Digest를 생성한다. 이를 통해 테스트 순서와 기존 fixture 존재 여부에 영향을
받지 않고 Current State와 Runtime Evidence 연결을 검증한다.

## 동적 SQL 조각 경계는 명시적인 공백으로 고정한다

### 문제

Current State 목록 SQL은 기본 `WHERE` 절 뒤에 선택 검색 조건을 붙이고 마지막에 `ORDER BY`를 결합한다.
선택 조건이 하나도 없는 기본 조회에서는 `:institutionId`와 `order`가 공백 없이 이어져
`:institutionIdorder`라는 Named Parameter로 해석됐고, 관리자 화면이 `INTERNAL_ERROR`를 표시했다.
Workload, Query 또는 Current-only 조건을 넣던 기존 테스트는 각 조건이 앞뒤 공백을 제공해 이 경로를 놓쳤다.

### 해결

`WHERE` 조각과 `ORDER BY` 조각 사이에 명시적인 줄바꿈을 두고, FE의 최초 요청과 동일하게 선택 필터 없이
`page`, `size`, `currentOnly=false`만 전달하는 Controller 통합 테스트를 추가했다. 동적 SQL은 개별 조각의
우연한 선행·후행 공백에 의존하지 않도록 경계 문자와 최소 조건 요청을 함께 검증한다.
