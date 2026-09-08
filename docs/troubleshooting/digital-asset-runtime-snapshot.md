# Digital Asset Runtime Snapshot 트러블슈팅

## Lifecycle ACTIVE와 실행 선택을 같은 의미로 사용한 문제

기존 generic Lifecycle은 아티팩트 상태 전이를 관리하지만 같은 scope에서 실행에 사용할 단 하나의 버전을 선택하는
책임은 없었다. Lifecycle 테이블에 단순 unique index를 추가하면 과거 ACTIVE 이력이 있는 운영 DB migration이 실패할
수 있다. 별도 `digital_asset_active_artifact` 테이블을 authoritative selection으로 두고 Institution과 Workload를 PK로
고정했다. Runtime은 Lifecycle의 ACTIVE row를 임의 검색하지 않고 이 선택 테이블만 사용한다.

## 상태 전이와 ACTIVE 선택 사이의 중간 상태

Lifecycle을 먼저 ACTIVE로 바꾸고 selection 저장이 충돌하면 실행 가능 여부가 불명확해진다. 전용 Activation Service에서
APPROVED 검증, Maker-Checker ACTIVE 전이, selection insert를 하나의 transaction으로 처리했다. selection의 PK 충돌은
전체 transaction을 rollback하므로 Lifecycle만 ACTIVE로 남는 중간 상태를 만들지 않는다.

## V27 데이터에 Runtime 버전을 임의 backfill하는 위험

기존 ingestion row에는 Runtime Control과 Crosswalk version/digest가 없었다. 추정값을 migration에서 채우면 검증하지 않은
Artifact가 실행 가능해질 수 있다. V28 컬럼은 nullable로 추가하되 Runtime은 null을 fail-closed한다. 같은 Bundle을 다시
ingest하면 trusted Schema, reference, digest를 모두 재검증한 뒤 metadata를 채운다.

## 실행 중 최신 설정 재조회로 생기는 TOCTOU

Policy 평가 후 Connector 전까지 ACTIVE 버전이 바뀌면 Decision 근거와 실제 전송 조건이 달라질 수 있다. Policy를 선택한
직후 Artifact, Policy, Destination, Control, Crosswalk를 execution별 Snapshot으로 저장하고 Provider Request 직전에
동일한 Policy/Destination identity인지 다시 확인한다.

## Replay와 Recovery에서 최신 ACTIVE를 다시 선택하는 문제

재시도 시 최신 설정을 선택하면 같은 idempotency key가 다른 정책으로 외부 전송될 수 있다. Idempotency replay는 기존
execution을 반환하고, Recovery는 저장된 Connector correlation/evidence만 사용한다. 두 경로 모두 ACTIVE selection을
다시 조회하지 않으며 Recovery E2E에서 Snapshot digest가 유지되는지 검증한다.

## 로컬 fixture와 P0-5 Candidate 충돌

P0-5 sample Artifact ID를 Runtime ACTIVE fixture에도 사용하면 Loader 동시 적재 테스트와 상태가 충돌한다. 로컬 실행용
ACTIVE identity를 `DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001`로 분리하고, P0-5 sample은 Candidate ingestion 검증용으로
유지했다.

