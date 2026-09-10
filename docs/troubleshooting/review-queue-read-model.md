# Review Queue Read Model 트러블슈팅

## Audit 검색을 Review Queue로 직접 사용하지 않은 이유

Audit 검색 API는 모든 Runtime 상태를 조회하는 범용 Evidence 탐색 경계다. FE에서
`status=REVIEW_REQUIRED`를 고정하는 것만으로 Review Queue를 만들면 상태 범위와 next action 의미를 client가
소유하게 된다.

Review Queue API가 서버에서 `REVIEW_REQUIRED` 조건을 고정하고 Policy, Recovery, Post-execution 상태를 조합해
`reviewSource`와 `nextAction`을 계산하도록 분리했다. Command API는 추가하지 않고 기존 Trace, Audit Evidence,
Recovery Operations 경로로 연결한다.

## AI Review가 COMMON으로 잘못 분류된 문제

초기 구현은 Pack 전용 Policy Evaluation 또는 Destination Profile 조인으로 Pack을 추론했다. 하지만 민감 입력은
Pack Policy Evaluation 전에 `REVIEW_REQUIRED`로 종료될 수 있어 AI 실행이 `COMMON`으로 분류됐다. 또한 조회
시점의 다른 테이블 상태에 따라 실행 당시 분류가 달라질 수 있었다.

V43에서 `runtime_execution.execution_pack` snapshot을 추가하고 Destination Profile pinning과 동시에 Pack을
저장하도록 변경했다. Review Queue의 응답과 필터는 이 실행 시점 snapshot만 사용한다. 기존 실행은 V43 적용 시
고정된 Destination Profile을 기준으로 backfill하며, caller가 전달한 값으로 저장 상태를 재해석하지 않는다.

통합 테스트는 Pack Policy Evaluation 전에 종료되는 민감 AI 입력도 `executionPack=AI`로 검색되는지 검증한다.

## 목록 권한과 상세 권한이 달라지는 문제

목록에만 Institution·Workload 조건을 적용하면 추측한 `executionId`로 상세 조회가 가능해질 수 있다. 목록과 상세
SQL 모두 동일한 scope predicate를 적용하고, 허용 범위 밖의 상세는 not found로 처리한다.

## 최신 main 전환 후 로컬 Docker의 Flyway checksum 충돌

과거 feature branch에서 V40/V41을 적용한 PostgreSQL volume은 merge 전에 정리된 최종 migration과 checksum이
다를 수 있다. 이 상태에서는 애플리케이션이 `Validate failed`로 기동하지 않는다. 운영 migration 문제가 아니라
아직 배포되지 않았던 개발 migration을 로컬 volume이 먼저 적용한 경우다.

기존 데이터를 보존해야 하면 별도 Compose project로 깨끗한 volume을 생성해 검증한다. 로컬 fixture 데이터만
있고 삭제가 승인된 경우에만 기존 volume을 명시적으로 초기화한다. `flyway repair`로 서로 다른 SQL을 같은
migration으로 인정시키지 않는다.
