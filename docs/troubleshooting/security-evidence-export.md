# Security Negative Matrix와 Evidence Export 트러블슈팅

## 기존 보안 통제를 다시 구현할 위험

Slice 27-A의 목표는 새로운 보안 엔진 추가가 아니라 이미 구현된 Gateway 신뢰 경계를 한 번에 검증하는 것이다. 개별 테스트가
각 패키지에 흩어져 있으면 CI가 성공해도 여섯 통제가 함께 유지되는지 바로 확인하기 어렵다. 따라서
`scripts/run-security-negative-matrix.sh`에서 다음 실패 계약을 하나의 명시적 CI Gate로 묶었다.

| 통제 | Negative 입력 | 반드시 유지할 결과 |
| --- | --- | --- |
| Context Authorization | 다른 Institution·Workload | Retrieval과 외부 호출 전 차단 |
| Minimum Egress | Secret·미승인 민감 Field | Outbound Guard 차단, Connector 미호출 |
| Destination | 미등록·Private·Loopback Endpoint | 외부 호출 0회 |
| Request Integrity | 같은 Key와 다른 Payload·중복 Burst | 409 또는 기존 결과, 외부 효과 1회 |
| External State | 응답 Leakage·`SENT_UNKNOWN` | Controlled Delivery 분리, Blind Retry 금지 |
| Audit | BLOCK·REVIEW·Recovery·Export | Reason·Finding·Trace·Event로 재현 |

Digital Asset 6-case fixture는 기존 DA 산출물을 읽기만 하며 이 브랜치에서 DA 코드는 변경하지 않는다.

## 일반 조회 DTO를 그대로 파일로 직렬화할 위험

관리자 조회 응답을 통째로 CSV/PDF로 변환하면 향후 조회 DTO에 원문 필드가 추가될 때 Export에도 조용히 포함될 수 있다.
Export 생성기는 별도의 고정 Field Allowlist를 사용하고, Raw Prompt·Context·Provider Response·고객 식별자·Secret은 입력으로도
받지 않는다. CSV cell은 `=`, `+`, `-`, `@`로 시작하면 앞에 apostrophe를 붙여 Formula Injection을 차단한다.

## 요청 시점 권한만 확인하는 TOCTOU 문제

Export는 요청 뒤 비동기로 생성되고 이후 다시 다운로드된다. 요청 시점 권한만 저장하면 그 사이 Role이나 Workload Grant가
회수되어도 파일이 만들어질 수 있다. 요청 범위는 서버가 조회한 Institution·Workload·Execution Pack·Execution ID로
고정하고, 승인 시 현재 승인자 Scope, 생성 시 요청자의 DB Grant, 다운로드 시 현재 Principal Scope를 각각 다시 검사한다.

## 생성자와 승인자가 같은 고위험 반출

모든 Evidence Export를 고위험 반출로 간주해 `REQUESTED → APPROVED` 사이에 maker-checker를 강제했다. 요청자와 승인자가
같으면 DB constraint와 애플리케이션 검증이 모두 거부한다. 동일 Idempotency Key와 동일 Scope는 기존 Job을 반환하고,
다른 Scope로 재사용하면 409로 실패한다.

## Worker 경합과 중단 후 복구

Job claim은 PostgreSQL `FOR UPDATE SKIP LOCKED`를 사용한다. `GENERATING` lease가 만료되면 `APPROVED`로 되돌려 다른
Worker가 회수할 수 있고, 완료 update는 `lease_owner`가 일치할 때만 허용한다. 생성 결과는 제한 크기, SHA-256 digest,
MIME type, 서버 파일명을 함께 저장한다.

## 만료가 파일 삭제와 분리될 때 생기는 잔존 데이터

READY 파일의 TTL이 지나면 조회 또는 다운로드 시 `EXPIRED`로 전이하면서 DB의 content를 즉시 NULL로 지운다.
`EXPIRED`와 `DELETED` Event를 모두 남겨 상태 만료와 실제 바이트 제거를 구분한다. Local MVP는 인증된 BE Streaming과
bounded DB bytea를 사용하며, Object Storage Signed URL은 Production Reference 범위로 남긴다.

## 전체 Compose 실행 중 테스트 DB를 공유하는 문제

기동 중인 `postgres` 개발 DB를 그대로 전체 테스트에 사용하면 화면 확인용 Fixture와 테스트 전이가 섞여 결과가 순서에 따라
달라질 수 있다. 또한 여러 `@SpringBootTest` Context가 기본 Hikari Pool을 각각 만들면 마지막 Context에서
`FATAL: sorry, too many clients already`가 발생할 수 있다. 이는 기능 실패와 구분해야 한다.

전체 서비스는 기존 `postgres`를 유지하고, 검증은 `postgres-test` Profile의 빈 DB를 사용한다. 컨테이너 내부 테스트에는
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3`, `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0`을 적용한다. 이 방식으로
개발 데이터는 보존하면서 512개 전체 테스트와 Security Negative Matrix를 독립적으로 검증할 수 있다.

CI에서도 전체 `gradle test`와 Negative Matrix를 같은 PostgreSQL에서 연속 실행하면 deterministic Digital Asset
Idempotency fixture가 두 번째 JVM에서 DB replay되고 process-local 외부 효과 카운터와 어긋난다. 따라서 두 검증은 각각
fresh PostgreSQL service를 갖는 독립 Job으로 실행하고, 두 Job이 모두 성공한 뒤에만 Package Job을 실행한다.

## 승인 대기 중 Evidence 변경 가능성

현재 Job은 요청 시점의 서버 Scope를 고정하지만 파일 내용은 생성 직전에 최신 privacy-safe Evidence를 다시 조회한다.
승인 대기 중 Recovery 상태가 바뀌면 고정 Allowlist 범위는 유지되지만 요청 당시 화면과 파일의 상태 값은 달라질 수 있다.
향후 외부 규제기관 제출처럼 snapshot 일치가 필요한 Report Type을 추가할 때는 요청 시 `sourceEvidenceDigest`를 저장하고,
생성 시 digest가 달라지면 재승인을 요구해야 한다.
