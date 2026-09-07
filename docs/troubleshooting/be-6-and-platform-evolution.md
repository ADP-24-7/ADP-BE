# BE-6 이후 플랫폼 트러블슈팅

## 범위

BE-6부터 Security Baseline까지의 기능 커밋과 리뷰 후속 `fix` 커밋을 검토해, 설계 변경의 원인과
검증 기준을 정리한다. 기준 범위는 `bb1cca3`부터 `d2cea8f`까지다.

## Egress 상태와 기존 Audit Migration

- 관련 커밋: `0c327f1`, `c1e41ce`, `b0a0216`, `40b2df8`, `eca6596`, `ffa6168`
- 현상: Connector 상태가 문자열로 흩어지고, Guard 차단과 외부 장애가 모두 Runtime `BLOCKED`로 귀결될 수 있었다.
- 원인: Policy/Guard 상태와 외부 Interaction 상태의 책임이 분리되지 않았고 V8 CHECK가 기존 Audit 상태를 고려하지 않았다.
- 해결: Destination Profile을 request 단위로 pinning하고 Response/Secret Guard를 분리했다. Connector 장애는
  `FAILED`, 전송 불확실은 `SENT_UNKNOWN`, 정책 차단은 `BLOCKED`로 분리했다. 기존 `audit_event`에는 신규 강한
  CHECK를 소급 적용하지 않아 역사적 row의 의미와 migration 안정성을 보존했다.
- 검증: fresh migration뿐 아니라 legacy connector status가 존재하는 previous-version upgrade를 검증했다.
- 교훈: 신규 enum을 설계할 때 현재 코드만 보지 말고 이미 저장된 운영 데이터의 vocabulary까지 migration 입력으로 취급한다.

## AI Runtime의 인가·전달 경계

- 관련 커밋: `b594fc5`, `67e12d9`, `b7fd52a`
- 현상: Destination/Profile 조회가 Authorization보다 앞서면 인가되지 않은 호출이 내부 구성 존재 여부를 관찰할 수 있었다.
- 해결: `RECEIVED -> Authorization -> Profile pinning -> Retrieval` 순서를 고정하고, Controlled Delivery가
  Response Guard 통과 이후에만 응답을 공개하도록 했다. Institution binding과 delivery evidence도 실행 trace에 저장했다.
- 검증: 인가 거부 시 Profile/Retrieval/Connector 호출이 0회인지 확인하고, 응답 schema 불일치와 secret finding에서
  전달이 보류되는지 통합 테스트로 고정했다.
- 교훈: 보안 검사는 존재하는 것만으로 충분하지 않고, 부수효과보다 먼저 실행된다는 순서 증적이 필요하다.

## 멀티 레포 Docker 개발환경

- 관련 커밋: `5425490`
- 문제: BE/FE/DA/Docs 작업자가 서로 다른 로컬 실행 방식과 오래된 고정 이미지를 사용하면 main 기준 통합 상태를
  재현하기 어려웠다.
- 해결: ADP-BE Compose를 로컬 통합 진입점으로 두고 각 sibling repository의 현재 source를 build context로 사용했다.
  개발용 container와 CI/NCP 배포용 immutable image 책임을 분리하고 서비스별 health check를 추가했다.
- 교훈: 팀 공통 개발환경은 배포 image tag를 공유하는 것이 아니라 동일 compose contract로 각 레포의 최신 source와
  의존 서비스를 재현하는 방식이어야 한다.

## Pack-neutral Runtime 예외 경계

- 관련 커밋: `0fca283`, `ad5f5de`
- 현상: Common Runtime이 AI 전용 예외를 직접 알면 Digital Asset/SaaS Pack 추가 시 공통 계층이 역으로 의존한다.
- 해결: `ExecutionPackInputRejectedException`을 공통 계약으로 두고 Pack별 resolver가 자신의 입력 검증을 소유하도록 했다.
- 교훈: Pack 확장은 공통 Service의 `instanceof` 분기를 늘리는 방식이 아니라 resolver port로 격리한다.

## Idempotency Reservation과 Denied Attempt

- 관련 커밋: `11778f9`, `3b3e839`, `34ae496`
- 현상: Authorization 전에 idempotency namespace를 예약하면 공격자가 거부 요청으로 정상 key를 선점할 수 있었다.
- 해결: Authorization 성공 후 reservation을 수행하고 institution + workload + key를 namespace로 고정했다.
  같은 hash는 replay, 다른 hash는 conflict, 진행 중 요청은 in-progress로 분리했다.
- Trade-off: 인가 거부 요청은 runtime row가 없어지므로 별도 `request_attempt` evidence가 후속 과제로 남았다.
- 교훈: 멱등성은 단순 중복 방지가 아니라 namespace 소유권과 retry semantics를 포함하는 보안 계약이다.

## SENT_UNKNOWN Recovery와 Lease Race

- 관련 커밋: `79259b2`, `da411f8`, `ee3c2db`
- 현상: 전송 결과가 불확실한 요청을 즉시 재전송하면 금융 외부 작업이 중복 실행될 수 있고, lease가 만료된 worker가
  최신 상태를 덮어쓸 수 있었다.
- 해결: `SENT_UNKNOWN`은 status query/reconciliation을 먼저 수행하고 `SKIP LOCKED` claim, lease owner/expiry 조건,
  attempt limit를 적용했다. 소진 또는 모호한 결과는 `REVIEW_REQUIRED`로 원자적으로 수렴시켰다.
- 검증: stale worker terminal update 차단, ambiguity fail-closed, exhausted/manual-review 수렴을 테스트했다.
- 교훈: 외부 호출 복구에서 timeout은 실패가 아니라 결과 미확정 상태다. retry보다 reconciliation이 먼저다.

## Terminal Observability 우회

- 관련 커밋: `1838de9`, `0c8ab57`, `c0bdd24`
- 현상: 일부 finalizer와 recovery claim sweep이 공통 terminal metric/structured log를 거치지 않았다.
- 해결: 모든 terminal transition과 worker crash 후 exhausted 처리도 동일 Observability 경계를 통과시켰다.
  metric tag는 enum 기반 low-cardinality 값으로 제한했다.
- 교훈: 정상 Service 경로만 계측하면 장애 복구 경로가 가장 먼저 관측 사각지대가 된다.

## Audit Read Model의 Scope 우회

- 관련 커밋: `b5531ba`, `6226c02`, `aef52c9`, `ac0b5dd`
- 현상: workload filter를 생략하거나 execution ID만 알면 다른 workload/tenant evidence가 조회될 가능성이 있었다.
- 해결: caller 선택 filter와 무관하게 allowed workload 및 institution 조건을 SQL에 항상 적용하고,
  `audit_event.execution_id`를 Runtime execution에 FK로 연결했다. Swagger 진입 경로는 `/docs`로 단순화하되
  공개 allowlist를 문서 endpoint로만 제한했다.
- 교훈: 조회 API의 tenant scope는 결과 후처리가 아니라 SQL predicate와 FK identity에서 강제한다.

## Digital Asset 외부 상태와 불신 입력

- 관련 커밋: `2db13e1`, `e6a07e3`, `08a156b`, `a3401e5`, `455ad05`, `dada449`, `010ba41`, `3b57c2b`
- 현상: Settlement 결과와 Runtime 상태를 따로 갱신하면 부분 성공이 생기고, Provider response의 request ID나 임의 JSON key를
  신뢰하면 잘못된 거래가 결합되거나 원문 key가 evidence에 저장될 수 있었다.
- 해결: outcome finalization을 단일 transaction으로 묶고 external request ID equality를 검증했다. KYC/AML/Wallet/Amount
  context 부재는 fail-closed 처리하고, mismatch field는 allowlist taxonomy로 정규화해 격리했다.
- 교훈: 외부 Provider payload는 HTTP 200이어도 신뢰 경계 밖 입력이며 identity, schema, field vocabulary를 모두 재검증한다.

## Policy Lifecycle Tenant Identity

- 관련 커밋: `08fbdc5`, `6993758`
- 현상: ID 기반 lifecycle 조회/전이가 institution과 workload를 함께 제한하지 않으면 cross-tenant collision이 가능했다.
- 해결: 모든 조회와 transition에 institution/workload scope를 적용하고 transition evidence FK도 tenant 복합 identity로 고정했다.
- 교훈: 관리자 기능도 인증만으로 안전하지 않다. Governance object identity 자체에 tenant namespace가 포함되어야 한다.

## Default-deny Security Matcher

- 관련 커밋: `403636b`, `d2cea8f`
- 현상: 인증 Principal이 있어도 matcher에 누락된 신규 endpoint가 암묵적으로 열릴 수 있었다.
- 해결: 공개 endpoint allowlist 이후 역할별 matcher를 배치하고 마지막을 `anyRequest().denyAll()`로 고정했다.
- 교훈: endpoint 추가 시 “누가 접근하는가”뿐 아니라 matcher 누락 시 기본 결과가 무엇인지 negative test로 증명한다.
