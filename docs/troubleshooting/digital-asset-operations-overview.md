# Digital Asset Operations Overview 트러블슈팅

## 운영 Runtime과 DA 분석 데이터를 섞을 수 없었다

DA Repository에는 온체인 분석 Sample과 Synthetic 규제 거래 데이터가 있지만, 이 행들은 BE의 `execution_id`와
연결된 운영 실행이 아니다. 화면 완성도를 위해 이를 Runtime KPI에 합치면 합성 결과가 실제 외부 실행처럼 표시된다.

- 운영 KPI와 차트는 PostgreSQL `runtime` schema만 Source of Truth로 사용한다.
- DA Dataset과 Object Storage Artifact는 정책·계약 검증 근거이며 운영 거래 Fact로 집계하지 않는다.
- 현재 저장하지 않는 Asset Symbol, Exact Amount, Destination Category는 `NOT_COLLECTED`로 명시한다.

## 동일한 상태명이 서로 다른 계층에 존재했다

`FAILED`, `SENT_UNKNOWN`, `RECONCILED`는 Runtime, Connector, Transaction, Recovery에서 각각 다른 의미를 가진다.
한 카드에서 여러 계층의 row를 더하면 중복 집계와 잘못된 상태 해석이 발생한다.

- 정책 통과/차단은 `runtime_execution.final_action`을 기준으로 한다.
- 실행 실패와 조정 완료는 `runtime_execution.status`를 기준으로 한다.
- 미확정은 `EGRESSING` 실행 중 Connector 또는 Transaction Evidence가 `SENT_UNKNOWN`인 실행만 센다.
- Recovery row 개수와 Runtime execution 개수는 합산하지 않는다.

## 개발 단계가 다른 실행의 Evidence 완성도가 달랐다

로컬 DB에는 Digital Asset 초기 Slice부터 최신 P0 실행까지 함께 남아 있어 모든 실행에 Pre Guard, Transaction,
Post Evidence가 존재하지 않는다. 전체 실행을 완전한 E2E처럼 표시하면 Sankey와 비율이 과장된다.

Overview 응답은 Evidence별 연결 건수를 `coverage`로 함께 반환한다. FE는 이를 별도 패널에 표시하고, Flow는
저장된 `final_action`과 현재 Runtime 상태의 연결만 표현한다. 실제 상태 전이 시간 분석은 향후 append-only 상태
이력이 생기기 전까지 주장하지 않는다.

## 기간 집계의 권한과 비용 경계

Overview는 최대 31일만 허용하고 Institution과 허용 Workload 조건을 모든 SQL에 적용한다. 기간별 집계를 위해
Digital Asset 실행에 한정된 partial index를 사용하며, 자산 주소나 금액 같은 고 Cardinality 값을 Metric label이나
응답에 추가하지 않는다.

## 공유 개발 DB에서 전체 테스트를 실행해 기존 데이터와 충돌했다

실행 중인 `postgres` 서비스에 `gradle test`를 직접 연결하면 기존 Runtime·Fixture row와 테스트의 deterministic key가
충돌해 unrelated 테스트가 연쇄 실패한다. 이는 운영 대시보드 SQL 실패가 아니라 테스트 격리 위반이다.

- 개별 Overview 통합 테스트는 개발 컨테이너에서 빠르게 확인할 수 있다.
- 전체 회귀 검증은 `make test`로 일회성 `postgres-test`를 생성해 CI와 같은 clean database에서 수행한다.
- 공유 개발 DB의 기존 데이터를 테스트 편의를 위해 삭제하거나 초기화하지 않는다.
