# Observability Operations 트러블슈팅

## Transaction Commit 전에 Counter를 증가시키는 문제

Policy Service에서 persistence 호출 직후 metric을 증가시키면 이후 commit failure가 발생해도 activation/rollback 성공으로
관측된다. Lifecycle과 Current Selection counter는 transaction synchronization의 `afterCommit`에서만 기록하도록 했다.
DB event가 authoritative evidence이고 metric은 운영 추세라는 책임도 분리했다.

## Commit 이후 Metric 예외가 성공 API를 실패로 바꾸는 문제

Transaction `afterCommit` callback에서 Meter Registry가 예외를 던지면 DB 변경은 이미 commit됐지만 API caller는
실패를 받을 수 있다. 이는 재시도를 유도해 동시성 충돌과 운영 혼선을 만든다. Post-commit 관측 호출을
best-effort로 격리하고, 고정된 `event`/`outcome`을 사용한 구조화 경고만 남기도록 변경했다. DB Evidence가
권위 있는 결과이며 Metric 실패는 이를 뒤집지 않는다.

## Selection Row 존재만으로 Drift가 없다고 판단한 문제

`current_selection` row가 있어도 대상 Lifecycle row가 삭제됐거나 stage/digest/revision이 달라질 수 있다. Selection과
authoritative Artifact를 left join해 누락, non-ACTIVE, digest mismatch, revision mismatch를 모두 drift로 계산한다.
Metric에는 artifact ID를 tag로 넣지 않고 전역 count만 노출하며 상세 원인은 scoped Policy Event API에서 확인한다.

## Prometheus Scrape마다 동일 DB Query를 반복하는 문제

여러 gauge supplier가 각각 DB를 조회하면 한 번의 scrape가 같은 집계를 반복하고 scrape replica 수만큼 부하가 증가한다.
모든 운영 gauge가 짧은 TTL의 단일 immutable snapshot을 공유하도록 구성했다. DB 일시 장애 시에는 마지막 snapshot을 유지한다.

초기 구현은 실패 사실을 구조화 로그로만 남겨 Prometheus가 stale 값을 정상 값으로 오인할 수 있었다. 이를 방지하기 위해 최근
refresh 성공 여부, 마지막 성공 snapshot age, refresh 실패 누적 counter를 함께 노출한다. Refresh 실패와 age 초과를 별도
alert로 감시해 Monitoring DB 장애가 정상 상태처럼 보이지 않도록 했다.

## 인증된 Runtime Principal이 전역 운영 Metric을 읽는 문제

기본 Prometheus 정책을 단순 `authenticated()`로 두면 `RUNTIME_EXECUTOR`도 전체 Recovery backlog와 Policy drift를 볼 수 있다.
또한 `OPERATOR`/`AUDITOR`는 Institution·Workload 범위를 갖지만 Prometheus metric은 전역 aggregate이므로 권한 의미가 맞지 않다.
기본 접근을 전용 `METRICS_SCRAPER` 역할을 가진 `SERVICE` Principal로 분리하고, 사용자 Principal에 같은 역할이
잘못 부여되어도 접근을 거부한다. 테넌트 운영자는 SQL scope가 강제된
`/api/admin/operations/**`만 사용하게 했다. 인증 없음은 401, 잘못된 역할은 403으로 구분하고, 공개 설정은
격리된 로컬 환경의 명시적 opt-in으로만 유지한다.

## Monitoring API가 Metric Tag를 대신해 Tenant ID를 노출하는 문제

Tenant별 dashboard를 만들기 위해 institution/workload를 Prometheus tag에 넣으면 cardinality와 정보 노출이 커진다.
Prometheus는 전역 bounded metric만 제공하고, tenant별 수치는 인증된 Monitoring Summary API가 Institution/Workload SQL scope를
적용해 반환하도록 분리했다.

## Alert Rule 파일이 있어도 CI가 실제 검증하지 않은 문제

YAML 파싱만으로는 PromQL 오류를 찾을 수 없다. 처음 Docker command는 이미지 기본 entrypoint 때문에 `prometheus promtool`로
해석되어 실패했다. `/bin/promtool` entrypoint를 명시하고 `check rules`를 실행해 rule을 CI에서 검증한다.

## Security Metric에 URL과 Request ID를 넣는 문제

Freshness, Authorization, Destination rejection을 조사하기 위해 URL, trace, principal을 metric tag로 넣으면 민감정보와
고카디널리티가 유입된다. Metric은 고정 enum outcome만 사용하며 개별 증적은 기존 Denied Attempt와 구조화 Audit 경계에서
조회한다.

## Crash 이후 IN_PROGRESS 운영 명령이 정상처럼 남는 문제

Recovery operation을 예약한 직후 프로세스가 종료되면 append-only event가 `IN_PROGRESS`로 남을 수 있다. 이를 자동으로
실패 처리하면 실제 외부 부수효과 여부를 모른 채 Evidence를 덮어쓰게 된다. 기본 5분 임계값을 넘긴 operation의 count와
oldest age를 별도 Gauge와 scoped Summary로 노출하고 Alert만 발생시킨다. 상태 변경은 후속 운영 정책에서 명시적으로 다룬다.

전역 Metric에는 operation ID, Institution, Workload를 tag로 넣지 않는다. 개별 대상 확인은 인증된 Summary/Recovery API의
Institution·Workload SQL scope를 사용한다. stale 조회는 V39 partial index로 `IN_PROGRESS` row에 한정한다.
