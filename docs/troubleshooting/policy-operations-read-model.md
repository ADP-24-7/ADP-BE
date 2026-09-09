# Policy Operations Read Model 트러블슈팅

## Command API만 있어 수동 Identity 입력이 필요했던 문제

Lifecycle 상세와 Action API는 존재했지만 목록 Query가 없어 FE가 Artifact ID와 Version을 수동 입력했다. Command 서비스와
분리된 Read Service·Port·JDBC Adapter를 추가하고 목록에서 상세·History·Command로 이동하도록 연결했다.

## Application에서만 Tenant와 Workload를 검사하는 문제

목록을 먼저 조회한 뒤 Application에서 제거하면 pagination total과 실제 노출 행이 어긋나고 새 호출 경로에서 scope가 빠질 수
있다. Institution과 allowed Workload를 count 및 page SQL 양쪽에 동일하게 적용하고 cross-tenant/workload 테스트로 고정했다.

## 테스트 Fixture가 다른 Lifecycle 테스트를 오염시킨 문제

Read Model 통합 테스트가 ACTIVE baseline을 남겨 이후 승인 테스트의 active baseline 조회가 ambiguous해졌다. 신규 테스트가 만든
Shadow Evidence, Transition, Artifact를 FK 역순으로 제거해 테스트 간 DB 상태를 격리했다.

## Pack 전환과 Command 이후 FE 상태가 오래 남는 문제

Pack을 바꿔도 이전 Artifact 선택을 유지하면 현재 화면 Pack과 Command 대상이 달라진다. Pack 변경 시 선택을 초기화하고 Create,
Transition, Shadow, Approval, Activation, Rollback 이후 관련 목록·History query를 무효화하도록 했다.

## 동적 SQL 조각 경계에서 Named Parameter가 합쳐진 문제

선택 필터 뒤에 정렬 SQL을 문자열로 결합하면서 공백 경계가 사라져 `:executionPackorder`라는 잘못된 parameter로 해석됐다. SQL
조각 사이에 명시적인 개행을 추가하고, 실제 PostgreSQL과 Docker API 호출에서 Pack 필터를 포함한 조회를 검증했다.

## 로컬 Docker DB의 Flyway Checksum 충돌

아직 main에 배포되지 않은 V40/V41 migration을 이전 브랜치에서 적용한 로컬 볼륨은 최종 migration 파일과 checksum이 달라
애플리케이션 시작이 거부됐다. 운영 이력을 `repair`로 덮거나 기존 개발 데이터를 삭제하지 않고 별도 Compose project
(`docker compose -p adp-slice25 ...`)와 새 볼륨으로 기동해 fresh migration과 현재 브랜치 통합 동작을 검증했다. 공유되거나 배포된
migration은 수정하지 않으며, 이 방식은 merge 전 migration 정리로 생긴 로컬 전용 충돌에만 사용한다.
