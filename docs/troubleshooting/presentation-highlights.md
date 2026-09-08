# 발표용 Troubleshooting Highlights

## 1. Authorization 전에 Idempotency를 예약한 문제

공격자가 거부 요청으로 정상 key를 선점할 수 있는 구조를 발견했다. Authorization 성공 이후에만 reservation하도록
순서를 변경해 namespace 소유권을 보장했다. 대신 Denied Attempt는 별도 evidence 모델이 필요하다는 trade-off를 남겼다.

## 2. Timeout을 실패로 보고 재전송할 수 있었던 문제

금융 외부 전송의 timeout은 실패가 아니라 결과 미확정이다. `SENT_UNKNOWN`과 reconciliation-first 상태 머신,
lease/attempt limit/stale worker 차단을 도입해 중복 외부 실행을 방지했다.

## 3. 가짜 HMAC과 Cross-context Linkability

단순 SHA-256을 HMAC으로 사용하던 구현을 `HmacSHA256`으로 교체하고 workload/purpose/provider/data-class scope를
message에 포함했다. 같은 원문도 업무 목적이 다르면 다른 pseudonym이 생성되도록 했다.

## 4. 기존 데이터 때문에 실패할 수 있던 Flyway Migration

fresh CI에서는 통과하지만 과거 Connector status row가 있는 DB에서는 신규 CHECK가 실패할 수 있었다. 역사적 Audit row를
억지로 재해석하지 않고 신규 authoritative table에 강한 constraint를 적용했으며 upgrade test를 추가했다.

## 5. Observability가 장애 경로를 놓친 문제

정상 Service terminal transition만 계측해 worker crash/exhausted와 일부 finalizer가 metric/log를 우회했다.
모든 terminal 경로를 공통 관측 경계로 수렴시키고 low-cardinality enum tag로 운영 가능성을 확보했다.

## 6. Provider의 임의 JSON Key가 Evidence에 남을 수 있던 문제

외부 응답의 mismatch key를 그대로 저장하면 개인정보성 key와 무제한 cardinality가 유입될 수 있었다. 허용 taxonomy로
정규화하고 알 수 없는 key는 원문 대신 bounded category로 치환했다.

## 7. Evidence 저장 실패가 성공한 Runtime을 실패로 바꾼 문제

성능 측정 저장은 부가 증적인데도 예외가 본 처리까지 전파됐다. best-effort recorder로 격리해 Runtime outcome을 보존하고
증적 저장 실패는 별도 metric으로 관측하도록 했다.

## 8. 1개 모델 결과가 3개 모델 평가 Bundle로 보이던 문제

DB에 존재하는 row만 묶어 Export해 등록된 Case × Model 완전성을 증명하지 못했다. 서버 Run Catalog의 기대 Cartesian
Product와 최신 Evidence를 비교하고 불완전 Bundle을 명시적으로 거부하도록 바꿨다.

## 9. 승인 존재만으로 Digital Asset 외부 호출이 가능했던 문제

Approval Scope와 Approved Transaction을 같은 승인으로 취급하면 유효한 reference의 존재만으로 asset/amount/destination
조건을 우회할 수 있었다. Server-owned 승인 원장을 scope-aware하게 조회하고 승인 조건 전체를 요청과 비교했다.

## 10. NCP Object 이름과 실제 bytes가 분리됐던 문제

파일명이 SHA-256 형식이어도 실제 다운로드 bytes가 그 digest와 같다는 보장은 없었다. JSON 검증 전에 raw bytes digest와
object key의 content address를 비교해 원격 저장소 경계에서 변조를 차단했다.

## 11. 최신 3-Model 평가가 과거 실행으로 대체될 수 있던 문제

Readiness가 READY여도 Bundle이 방금 요청한 execution이 아닌 과거 COMPLETE row를 선택할 수 있었다. Runtime 응답,
Readiness, Bundle의 execution ID 집합을 끝까지 비교해 이번 실행의 provenance를 보장했다.

## 12. KEEP 결과 digest를 원본 digest와 직접 비교한 문제

Transform 결과 digest와 Canonical source digest는 계산 목적이 달라 정상 exact field도 불일치한다. Source digest와
Transform lineage, output digest와 Candidate digest를 단계별로 비교하고 실제 값/strategy도 함께 검증했다.

## 발표 시 강조할 공통 원칙

- Fail closed는 예외를 던지는 것뿐 아니라 부수효과 순서, DB scope, 복구 상태까지 포함한다.
- Runtime 상태, 외부 Interaction 상태, Audit Evidence 상태를 하나의 enum으로 합치지 않는다.
- 외부 입력과 DB evidence는 authoritative server contract와 다시 비교한다.
- happy-path CI 외에 previous-version upgrade, race, malformed response, partial evidence를 검증한다.
- 개인정보 원문 대신 digest/reference를 사용하되 digest 자체의 linkability와 canonicalization도 설계한다.
