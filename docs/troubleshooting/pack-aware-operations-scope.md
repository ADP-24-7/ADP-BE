# Pack-aware Operations Scope 트러블슈팅

## FE Pack Selector를 데이터 권한으로 오해하지 않는다

### 문제

전역 Pack Selector만 바꾸고 BE 조회 조건을 그대로 두면 화면 문구는 AI 또는 Digital Asset으로 바뀌지만,
Operations Summary, Recovery Incident, Policy Event는 모든 Pack 데이터를 계속 반환한다. 반대로 Pack을 새로운
Principal 권한으로 취급하면 기존 Workload 권한 모델과 중복되고 현재 인증 계약에 없는 권한을 임의로 만든다.

### 해결

Pack은 server-owned `execution_pack`을 사용하는 조회 Scope로 적용한다. Institution과 `allowedWorkloads`는 기존
인가 경계로 계속 강제하고, 요청한 Pack은 그 결과를 한 번 더 좁힌다. Recovery 목록, 상세, 명령 예약에도 동일한
Pack 조건을 적용해 목록에서 보이지 않는 다른 Pack Incident를 ID로 직접 조작하지 못하게 한다.

## Pack 미지정 의미를 암묵적으로 두지 않는다

### 문제

optional `executionPack`을 단순히 SQL 조건 생략으로만 구현하면 소비자는 응답이 전체 Pack인지 특정 Pack인지
판단할 수 없다. 특히 `request_attempt`은 Runtime execution이 만들어지기 전의 거부 증적이라 Pack이 없다.

### 해결

Pack 미지정은 `ALL_AUTHORIZED_WORKLOADS`로 고정한다. Operations Summary v2는 요청 Pack, 기본 semantics,
Pack 기준 섹션과 전체 허용 Workload 기준 섹션을 `scope`에 반환한다. Pack을 요청해도 Security 거부 집계는
원본 Workload 권한 범위로 유지하며 이를 `allAuthorizedWorkloadSections`에 명시한다.

## Summary, Incident, History가 같은 Pack으로 움직여야 한다

### 문제

각 API의 query key에 Pack이 포함되지 않거나 이전 데이터를 placeholder로 유지하면 Pack 전환 중 기존 Pack의
Summary나 Incident를 새 Pack 데이터처럼 클릭할 수 있다.

### 해결

FE는 전역 Selector를 Summary, Recovery, Policy Event 요청에 모두 전달한다. Pack이 바뀌면 선택된 Incident와
페이지를 초기화하고, 이전 Pack 목록을 표시하지 않은 상태에서 새 응답을 기다린다. BE 통합 테스트는 동일
Institution과 Workload에서도 다른 Pack 조회가 0건 또는 404가 되는지 검증한다.

## 저장된 UI 상태와 동시 명령도 같은 경계를 지켜야 한다

### 문제

UI에서 더 이상 제공하지 않는 Pack이 브라우저 저장소에서 복원되면 Summary와 Review Queue가 서로 다른 Pack을
조회할 수 있다. 또한 같은 `operationId`의 Recovery 명령이 동시에 도착하면 순차 replay 테스트만으로는 외부
효과의 단일성을 증명할 수 없다.

### 해결

FE는 현재 선택 가능한 AI와 Digital Asset만 복원하고 테스트마다 저장소를 초기화한다. Policy History 페이지도
Pack 변경 시 첫 페이지로 되돌린다. BE는 `(recovery_id, operation_id)` unique 경계에서 명령을 예약하며, 동시
통합 테스트로 operation event 1건과 Recovery attempt 1회만 발생하는 것을 검증한다.

## Decision Trace도 전역 Pack Scope를 따라야 한다

### 문제

FE의 Decision Trace는 선택한 Pack을 화면에 표시했지만 Audit 목록 API에는 Pack 조건을 전달하지 않았다.
그 결과 Digital Asset 화면에서 AI 실행이 함께 노출되고, 선택한 실행의 실제 도메인과 상세 제목이 달라질 수 있었다.

### 해결

Audit 목록 API에 `executionPack`을 추가하고 Institution·Workload 인가 조건과 함께
`runtime_execution.execution_pack`을 SQL에서 강제한다. 목록 응답에도 `executionPack`을 포함해 조회 결과의
도메인을 명시한다. 통합 테스트는 AI와 Digital Asset 실행을 함께 준비한 뒤 요청 Pack 이외의 실행이 반환되지
않는지 검증한다. FE는 Pack 변경 시 기존 선택과 Workload 검색을 초기화해 다른 Pack의 상세 증적이 남지 않게 한다.
