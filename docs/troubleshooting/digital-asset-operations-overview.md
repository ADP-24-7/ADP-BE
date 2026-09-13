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

Overview 응답은 Evidence별 연결 건수를 `coverage`로 함께 반환한다. Flow는 저장된 Evidence를 사용해
`요청 -> 정책 판정 -> 외부 실행 -> 증적 수집 -> 조정 -> 최종 Runtime 상태`를 연결한다. 외부 실행 단계는
Connector와 Transaction의 확정 상태를 기준으로 성공, 실패, 결과 미확정, 복구 확인, 미전송으로 분류하고,
증적과 조정 단계는 실제 row 존재 여부와 상태만 사용한다. 실제 상태 전이 시간 분석은 향후 append-only 상태
이력이 생기기 전까지 주장하지 않는다.

## 기간 집계의 권한과 비용 경계

Overview는 최대 31일만 허용하고 Institution과 허용 Workload 조건을 모든 SQL에 적용한다. 기간별 집계를 위해
Digital Asset 실행에 한정된 partial index를 사용하며, 자산 주소나 금액 같은 고 Cardinality 값을 Metric label이나
응답에 추가하지 않는다.

## 최신 신호만 나열하면 차단 이벤트가 조사 목록을 독점했다

최근 이벤트를 발생 시각 역순으로만 조회하면 반복 발생한 `BLOCKED`가 목록을 채워 `FAILED`, `EGRESSING`,
`REVIEW_REQUIRED` 같은 즉시 조치 대상이 밀려날 수 있다. 또한 상태와 Execution ID만으로는 운영자가 무엇을
확인해야 하는지 판단할 수 없다.

- 신호는 `FAILED -> EGRESSING -> REVIEW_REQUIRED -> BLOCKED -> EXTERNALLY_RECONCILED` 우선순위 안에서 최신순으로 정렬한다.
- 각 신호에 Workload, 조사 단계, 서버가 저장한 Reason Code와 권장 다음 조치를 함께 제공한다.
- 사전 Guard JSON 배열과 사후 mismatch JSON 배열은 첫 번째 구체 원인을 사용하고, 정책 판정 CSV는 첫 코드를 사용한다.
- FE는 원인을 임의 추론하지 않고 이 계약으로 Audit, Recovery, Review Queue 조사 경로를 제시한다.
- 이 정렬은 심각도 분류를 대체하지 않으며, 동일 우선순위 안에서는 최신 Evidence를 먼저 보여준다.

## 공유 개발 DB에서 전체 테스트를 실행해 기존 데이터와 충돌했다

실행 중인 `postgres` 서비스에 `gradle test`를 직접 연결하면 기존 Runtime·Fixture row와 테스트의 deterministic key가
충돌해 unrelated 테스트가 연쇄 실패한다. 이는 운영 대시보드 SQL 실패가 아니라 테스트 격리 위반이다.

- 개별 Overview 통합 테스트는 개발 컨테이너에서 빠르게 확인할 수 있다.
- 전체 회귀 검증은 `make test`로 일회성 `postgres-test`를 생성해 CI와 같은 clean database에서 수행한다.
- 공유 개발 DB의 기존 데이터를 테스트 편의를 위해 삭제하거나 초기화하지 않는다.

## 로컬 대시보드에서 기간 비교 데이터가 부족했다

단발성 E2E fixture만으로는 7일 현재 기간과 직전 기간의 변화율, 시간대별 밀도, 상태별 페이지 검색을 함께
검증하기 어렵다. FE에 고정 응답을 넣으면 실제 API·SQL·PostgreSQL 경로를 검증할 수 없다.

- `ADP_LOCAL_DASHBOARD_FIXTURES_ENABLED=true`인 로컬 Compose에서만 14일간 420건의 Runtime 활동을 적재한다.
- 행은 PostgreSQL의 실제 Runtime·Decision·Transaction·Mismatch·Recovery 테이블에 저장하며 FE mock으로 대체하지 않는다.
- 현재 7일은 245건, 이전 7일은 175건으로 구성하고 완료 비중을 가장 높게 유지한다.
- 동일 ID를 upsert하고 시간을 시작 시점 기준으로 갱신해 컨테이너 재기동이 행을 무한 증식시키지 않게 한다.
- 운영 환경은 해당 속성을 활성화하지 않으며 이 데이터로 운영 성능이나 실제 거래량을 주장하지 않는다.

## 최근 실행을 FE에서만 필터링하면 페이지 결과가 왜곡됐다

10건만 받은 뒤 브라우저에서 검색하면 전체 기간이 아니라 현재 페이지 안에서만 찾게 된다. Overview API가
Request ID, Execution ID, Workload 검색과 Runtime 상태 필터, 페이지·크기를 받아 Institution·Workload 조건과
같은 SQL에서 처리한다. 검색어 길이, 상태 allowlist, 페이지 범위를 Service에서 먼저 검증한다.
