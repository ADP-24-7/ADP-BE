# SEC-1/2 Security Hardening

## 목표

Runtime 기능 테스트와 독립된 보안 경계를 만들고, 외부 요청과 거부 요청을 privacy-safe evidence로 추적한다.

## SEC-1 Runtime Security

- `X-Trace-Id`는 caller correlation 입력으로만 취급하고 서버가 authoritative trace ID를 생성한다.
- caller trace 원문은 저장하지 않으며 SHA-256 digest만 denied-attempt evidence에 연결한다.
- `POST /v1/runtime/executions`는 `X-ADP-Request-Timestamp`가 replay window 안에 있어야 한다.
- Timestamp는 오래된 요청의 허용 시간을 제한하고, window 안의 중복 실행은 기존 Idempotency 경계가 통제한다.
- Authorization deny는 idempotency namespace를 소비하지 않고 `runtime.request_attempt`에 별도 저장한다.
- 거부 증적에는 subject 원문과 idempotency key를 저장하지 않는다.
- Institution mismatch 증적에는 caller가 주장한 institution이 아니라 인증 Principal의 institution만 저장한다.
- 외부 AI endpoint는 등록된 Provider host allowlist, HTTPS, DNS resolution을 검증하고 redirect와
  loopback/private/link-local/metadata 주소를 차단한다.
- 로컬 Docker의 HTTP/private endpoint 허용은 운영 기본값과 분리된 explicit override다.

## SEC-2 CI Security Gate

`.github/workflows/security.yml`에서 다음 검사를 별도 required check 후보로 제공한다.

| Check | 역할 |
| --- | --- |
| Secret Scan | Git history의 credential 노출 차단 |
| SAST | Java/Kotlin CodeQL 분석 및 Security 결과 업로드 |
| Dependency Container SBOM | dependency/filesystem 및 release image 취약점 차단, CycloneDX SBOM 생성 |

GitHub branch protection에서는 `Secret Scan`, `SAST`, `Dependency Container SBOM`을 required checks로 지정한다.

## 운영 기본값

```yaml
ADP_REQUEST_FRESHNESS_ENABLED=true
ADP_REQUEST_REPLAY_WINDOW=5m
ADP_REQUEST_FUTURE_SKEW=30s
ADP_ACCEPT_CALLER_TRACE_ID=false
ADP_EGRESS_ALLOW_PRIVATE_DESTINATIONS=false
ADP_EGRESS_ALLOWED_HOSTS=integrate.api.nvidia.com
```

로컬 통합 스택은 mock provider 때문에 freshness를 끄고 caller trace 및 private destination을 명시적으로 허용한다.

## SSRF 책임 경계

애플리케이션 검증과 실제 HTTP 연결 사이의 DNS 재조회까지 동일 IP로 pinning하지는 않는다. 운영에서는 등록된 Provider
host:443만 허용하는 NCP VPC/ACG/egress firewall 정책을 최종 통제로 사용해야 한다. HTTP redirect는 애플리케이션에서
명시적으로 비활성화한다.
