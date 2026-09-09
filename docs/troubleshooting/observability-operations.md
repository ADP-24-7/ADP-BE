# Observability Operations 트러블슈팅

## Transaction Commit 전에 Counter를 증가시키는 문제

Policy Service에서 persistence 호출 직후 metric을 증가시키면 이후 commit failure가 발생해도 activation/rollback 성공으로
관측된다. Lifecycle과 Current Selection counter는 transaction synchronization의 `afterCommit`에서만 기록하도록 했다.
DB event가 authoritative evidence이고 metric은 운영 추세라는 책임도 분리했다.

## Selection Row 존재만으로 Drift가 없다고 판단한 문제

`current_selection` row가 있어도 대상 Lifecycle row가 삭제됐거나 stage/digest/revision이 달라질 수 있다. Selection과
authoritative Artifact를 left join해 누락, non-ACTIVE, digest mismatch, revision mismatch를 모두 drift로 계산한다.
Metric에는 artifact ID를 tag로 넣지 않고 전역 count만 노출하며 상세 원인은 scoped Policy Event API에서 확인한다.

## Prometheus Scrape마다 동일 DB Query를 반복하는 문제

여러 gauge supplier가 각각 DB를 조회하면 한 번의 scrape가 같은 집계를 반복하고 scrape replica 수만큼 부하가 증가한다.
모든 운영 gauge가 짧은 TTL의 단일 immutable snapshot을 공유하도록 구성했다. DB 일시 장애 시에는 마지막 snapshot을 유지하고
고정 outcome 구조화 로그만 남긴다.

## Monitoring API가 Metric Tag를 대신해 Tenant ID를 노출하는 문제

Tenant별 dashboard를 만들기 위해 institution/workload를 Prometheus tag에 넣으면 cardinality와 정보 노출이 커진다.
Prometheus는 전역 bounded metric만 제공하고, tenant별 수치는 인증된 Monitoring Summary API가 Institution/Workload SQL scope를
적용해 반환하도록 분리했다.

## Alert Rule 파일이 있어도 CI가 실제 검증하지 않은 문제

YAML 파싱만으로는 PromQL 오류를 찾을 수 없다. 처음 Docker command는 이미지 기본 entrypoint 때문에 `prometheus promtool`로
해석되어 실패했다. `/bin/promtool` entrypoint를 명시하고 `check rules`를 실행해 5개 rule을 CI에서 검증한다.

## Security Metric에 URL과 Request ID를 넣는 문제

Freshness, Authorization, Destination rejection을 조사하기 위해 URL, trace, principal을 metric tag로 넣으면 민감정보와
고카디널리티가 유입된다. Metric은 고정 enum outcome만 사용하며 개별 증적은 기존 Denied Attempt와 구조화 Audit 경계에서
조회한다.
