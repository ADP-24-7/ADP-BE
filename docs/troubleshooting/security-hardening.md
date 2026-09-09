# Security Hardening 트러블슈팅

## Caller Trace를 서버 Trace로 신뢰한 문제

기존 `TraceContextFilter`는 caller가 보낸 `X-Trace-Id`를 그대로 Runtime/Audit의 authoritative trace로 사용했다. 공격자가
상관관계 namespace를 선택하거나 기존 trace와 혼동시킬 수 있으므로 서버 trace를 매 요청마다 새로 생성하도록 변경했다.
caller trace는 원문 대신 digest만 별도 evidence에 남긴다.

## Authorization 이전 reservation과 Denied Evidence의 충돌

인가 전에 Runtime row를 만들면 거부 요청이 idempotency key를 선점한다. 인가 이후 reservation 원칙은 유지하면서,
`runtime_execution`과 독립된 `runtime.request_attempt`를 추가해 거부 시도만 기록했다. 이 테이블에는 subject 원문,
idempotency key, 요청 payload를 저장하지 않는다.

## 설정 기반 Provider URL의 SSRF 위험

Provider URL이 환경변수에서 직접 `RestClient.baseUrl()`로 연결되면 오설정 또는 설정 변조가 내부망과 metadata endpoint
호출로 이어질 수 있다. scheme/authority/DNS 결과를 커넥터 직전에 검증하고 private, loopback, link-local,
metadata 주소를 기본 차단했다. 로컬 mock provider는 별도 override 없이는 사용할 수 없다.

DNS 검증과 HTTP 연결은 서로 다른 DNS 조회가 될 수 있으므로 application check만으로 rebinding을 완전히 해결했다고
주장하지 않는다. 운영 기본값은 사전 등록된 Provider host만 허용하고 redirect를 비활성화하며, 실제 연결 대상은 NCP
egress firewall allowlist로 한 번 더 제한한다.

## Timestamp만으로 Replay를 완전히 막는다고 표현한 문제

Freshness 검증은 replay window 밖의 오래된 요청을 거부하지만 window 안의 동일 요청을 단독으로 식별하지 않는다. 따라서
문서 계약을 `Replay Window Enforcement`로 바로잡고, 동일 요청의 중복 실행은 Institution + Workload + Idempotency Key와
canonical request hash 경계가 담당하도록 책임을 분리했다.

## 보안 검사를 일반 CI에 섞을 때의 문제

기능 테스트와 보안 도구를 한 job에 넣으면 실패 원인과 required gate 소유권이 불명확해진다. 별도 `Security Gate`
workflow로 분리하고 Secret, SAST, dependency/container, SBOM 결과를 독립적으로 확인하도록 구성했다.

초기 Secret Scan은 조직 repository에서 별도 라이선스를 요구하는 Gitleaks Action을 사용해 scan 시작 전 실패했다.
라이선스와 무관한 공식 OSS container `v8.30.1`을 직접 실행하고 full Git history를 checkout해, 실제 leak 발견 여부만
job의 성공과 실패를 결정하도록 수정했다.

최초 OSS scan에서는 과거 테스트의 synthetic AWS 패턴, idempotency fixture, 로컬 API 문서 예제가 검출됐다. Git 이력을
숨기는 path-wide allowlist 대신 검토가 끝난 9개 commit fingerprint만 `.gitleaksignore`에 등록했다. 같은 파일이나 규칙에서
새로운 값이 추가되면 fingerprint가 달라지므로 Security Gate가 다시 실패한다.

## Security Gate가 실제 Release Image 취약점을 차단한 문제

최초 Container Scan은 애플리케이션 JAR의 Tomcat, PostgreSQL, Netty, Apache HttpCore와 Temurin base image의
`pebble`에서 수정 가능한 HIGH/CRITICAL 취약점을 검출했다. Spring Boot `3.5.16`이 최신 3.5.x여도 BOM이 모든
upstream security fix를 즉시 포함한다는 보장은 없으므로, Gate 추가 자체를 완료로 보지 않고 최종 image contents를
기준으로 보완했다.

Tomcat과 PostgreSQL은 수정 버전으로 dependency-management property를 올렸다. 반면 AWS S3 client는
`UrlConnectionHttpClient`를 명시적으로 사용하므로 transitive Apache5/Netty HTTP client를 버전 override하지 않고
패키징에서 제외했다. 사용하지 않는 라이브러리의 버전을 올리는 것보다 release image의 공격 표면을 줄이는 선택이다.

floating `eclipse-temurin:21-jre`는 패치된 Temurin 21 UBI minimal multi-architecture image digest로 고정했다. 이미지에
배포판별 user 관리 명령을 추가하지 않고 numeric non-root UID로 실행해 minimal image와 non-root 원칙을 함께 유지한다.
또한 Container Scan 전에 CycloneDX SBOM을 생성하도록 순서를 바꿔, 취약점으로 Gate가 실패하더라도 분석할 dependency
evidence를 보존한다.
