# SEC-1/2 Security Hardening

## 목표

Runtime 기능 테스트와 독립된 보안 경계를 만들고, 외부 요청과 거부 요청을 privacy-safe evidence로 추적한다.

## SEC-1 Runtime Security

- `X-Trace-Id`는 caller correlation 입력으로만 취급하고 서버가 authoritative trace ID를 생성한다.
- caller trace 원문은 저장하지 않으며 SHA-256 digest만 denied-attempt evidence에 연결한다.
- `POST /v1/runtime/executions`는 `X-ADP-Request-Timestamp`가 replay window 안에 있어야 한다.
- Authorization deny는 idempotency namespace를 소비하지 않고 `runtime.request_attempt`에 별도 저장한다.
- 거부 증적에는 subject 원문과 idempotency key를 저장하지 않는다.
- Institution mismatch 증적에는 caller가 주장한 institution이 아니라 인증 Principal의 institution만 저장한다.
- 외부 AI endpoint는 HTTPS, host, DNS resolution을 검증하고 loopback/private/link-local/metadata 주소를 차단한다.
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
```

로컬 통합 스택은 mock provider 때문에 freshness를 끄고 caller trace 및 private destination을 명시적으로 허용한다.
