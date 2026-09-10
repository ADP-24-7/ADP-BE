# Admin Identity / Permission Read Model 트러블슈팅

## 로그인 화면 없이 관리자 API가 동작하는 이유

### 문제

로컬 Console은 로그인 화면이 없지만 관리자 API는 인증을 요구한다. 이 상태만 보면 FE가 인증을 우회하거나,
DB의 Principal과 무관한 권한을 사용하는 것처럼 보일 수 있다.

### 해결

로컬 Compose에서는 Vite dev server가 BFF 역할을 하며 `X-ADP-User-Id`, `X-ADP-User-Roles`를 BE 요청에만
주입한다. 브라우저 bundle에는 관리자 credential을 넣지 않는다. 이 방식은
`adp.local-user-auth.enabled=true`에서만 유효하며 production-like 환경에서는 비활성화한다.

Identity Read Model은 별도로 PostgreSQL의 `auth_principal`, `auth_principal_role`,
`auth_principal_workload`, `auth_subject_grant`를 조회한다. Local fixture의 `operator-local`도 Vite 기본 역할과
맞춰 저장하므로 Console에서 개발용 인증 주체와 DB 권한 구성을 함께 확인할 수 있다.

## 권한 화면에서 Secret과 Subject 원문을 반환하지 않는다

### 문제

관리자 권한을 설명하기 위해 `auth_api_key.key_hash`나 `auth_subject_grant.subject_id`를 그대로 반환하면,
Read API가 credential metadata와 대상 고객 식별자를 유출하는 경로가 된다.

### 해결

API Key는 활성/전체 개수만 반환한다. Subject grant는 Workload, Action, Purpose, Subject Type과 대상 수만
집계하며 `subject_id`는 반환하지 않는다. 목록과 상세 계약 테스트에서 `keyHash`, `apiKey`, `subjectId`가
응답에 없음을 고정한다.

## Institution과 Workload Scope를 SQL에서 강제한다

### 문제

Java에서 결과를 조회한 뒤 Scope를 필터링하면 다른 Institution의 Principal 존재 여부가 드러나고,
Workload count와 pagination도 실제 권한 범위와 달라진다.

### 해결

Principal 목록과 상세는 `institution_id`를 SQL 조건으로 사용한다. Workload mapping과 Purpose grant는
호출 Principal의 허용 Workload 조건을 SQL에 포함하며, 범위 밖 상세는 `ADMIN_IDENTITY_NOT_FOUND` 404로
처리한다. API Key 원문 저장 정책과 기존 Runtime Authorization 경로는 변경하지 않는다.

Identity row 자체도 대상 Principal의 Workload mapping과 호출자의 허용 Workload가 교차할 때만 조회한다.
이 predicate는 목록 select와 count, 상세에 동일하게 적용한다. 호출자가 `*`이면 Institution 범위를 사용하고,
제한된 집합이면 대상의 `*` 또는 교집합을 요구하며, 빈 Workload 집합이면 목록 0건과 상세 404로 fail-closed한다.
반환 배열만 잘라내고 Identity 메타데이터를 남기는 방식은 사용하지 않는다.

## Workload 비활성과 Registry 미등록을 구분한다

### 문제

`left join workload_registry` 결과에 `coalesce(enabled, false)`를 적용하면 실제 `enabled=false`인 Workload와
Registry row가 없는 역사적 Workload를 구분할 수 없다. FE가 둘을 모두 미등록으로 표시하면 운영 판단이 틀어진다.

### 해결

BE가 `workloadRegistryStatus`를 `ENABLED`, `DISABLED`, `UNRESOLVED` 중 하나로 계산해 반환한다. FE는 이 값을
추측하지 않고 각각 활성, 비활성, Registry 미등록 상태로 표시한다.

## Fixture grant 개수를 단일 값으로 가정하지 않는다

### 문제

기본 Runtime fixture와 AI Evaluation provenance fixture가 같은 Workload/Purpose에 서로 다른 Subject grant를
추가한다. 테스트가 grant를 항상 한 건으로 가정하면 실제 적재 순서와 무관한 실패가 발생한다.

### 해결

Read Model은 Subject ID를 노출하지 않고 `count(distinct subject_id)`로 유효 대상 수를 계산한다. 통합 테스트는
전체 Local fixture 적용 결과를 기준으로 집계값을 검증한다.
