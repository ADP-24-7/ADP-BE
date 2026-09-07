# AI-EVAL-2 Runtime Evidence Capture

## 목적

AI Evaluation 실행의 성능과 실패 원인을 DA가 모델별로 비교할 수 있도록 privacy-safe Runtime Evidence를 남긴다.
Model/Run/Dataset/Policy provenance는 AI-EVAL-0~1 계약을 그대로 사용한다.

## 수집 계약

- `measurementType`: `HTTP_FULL_RESPONSE`, `HTTP_ATTEMPT_TIMEOUT`, `NOT_ATTEMPTED`, `MOCK` 구분
- `fullResponseLatencyMillis`: 정상 또는 HTTP 오류 응답 본문 처리가 끝날 때까지의 시간
- `attemptElapsedMillis`: 응답 없이 timeout/transport failure가 확정될 때까지의 시간
- `inputTokens`, `outputTokens`, `totalTokens`: Provider가 완전한 `usage`를 반환한 경우에만 저장
- `providerStatus`: 정규화된 Connector 상태
- `errorCategory`: Credential/Connection, Transport, Provider 4xx/5xx, Response Parse 분류
- `tokenUsageStatus`: `COMPLETE`, `NOT_PROVIDED`, `INCOMPLETE`, `INVALID` 구분
- `initialRuntimeLatencyMillis`: 최초 동기 Runtime 처리 종료 시 한 번 저장되는 immutable latency
- `runtimeFinalAction`, `responseGuardStatus`, `traceReference`: 기존 Runtime Evidence와 결합해 조회

현재 요청은 `stream=false`이므로 TTFT 또는 first-response timing을 기록하지 않는다. 실제 token TTFT는 향후
streaming Provider Contract를 도입한 뒤 별도 measurement type과 컬럼으로 추가한다.

## 데이터 경계

- Prompt, RAG Context, Provider Response 원문은 저장하지 않는다.
- Credential과 Provider authorization header를 저장하지 않는다.
- Token usage는 Provider가 세 값을 모두 반환한 경우에만 저장한다.
- 부분·불일치·비정상 usage는 추정하지 않고 `null`로 유지하며 Provider 성공 상태를 변경하지 않는다.
- Trace Reference는 caller 제공 Trace ID가 아닌 server-owned Execution ID를 사용한다.
- V24 DB 제약으로 음수 latency/token, token 합계 불일치, 미등록 상태와 오류 분류를 차단한다.

## 다음 단계

AI-EVAL-3에서 이 Evidence와 immutable Evaluation Contract를 사용해
`manifest + execution-config + case-results + runtime-metrics + trace-index` Bundle을 생성한다.
