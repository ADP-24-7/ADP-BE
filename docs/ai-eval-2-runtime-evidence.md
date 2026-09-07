# AI-EVAL-2 Runtime Evidence Capture

## 목적

AI Evaluation 실행의 성능과 실패 원인을 DA가 모델별로 비교할 수 있도록 privacy-safe Runtime Evidence를 남긴다.
Model/Run/Dataset/Policy provenance는 AI-EVAL-0~1 계약을 그대로 사용한다.

## 수집 계약

- `measurementType`: 실제 HTTP 또는 Local Mock 측정 구분
- `timeToFirstResponseMillis`: 현재 non-streaming Chat Completions에서 HTTP 응답을 수신할 때까지의 시간
- `providerLatencyMillis`: Provider 호출 시작부터 응답 본문 처리 완료까지의 시간
- `inputTokens`, `outputTokens`, `totalTokens`: Provider가 완전한 `usage`를 반환한 경우에만 저장
- `providerStatus`: 정규화된 Connector 상태
- `errorCategory`: Credential/Connection, Transport, Provider 4xx/5xx, Response Parse 분류
- `totalLatencyMillis`: Runtime 수신부터 terminal 상태 기록까지의 DB timestamp 기반 시간
- `runtimeDecision`, `responseGuardStatus`, `traceReference`: 기존 Runtime Evidence와 결합해 조회

`timeToFirstResponseMillis`는 streaming token 기준 TTFT가 아니다. 현재 요청이 `stream=false`이므로 HTTP 응답
가용 시점을 측정한다. 실제 token TTFT는 향후 streaming Provider Contract를 도입할 때 별도 measurement type으로
추가하며, 현재 값을 token TTFT로 오인하지 않는다.

## 데이터 경계

- Prompt, RAG Context, Provider Response 원문은 저장하지 않는다.
- Credential과 Provider authorization header를 저장하지 않는다.
- Token usage는 Provider가 세 값을 모두 반환한 경우에만 저장한다.
- 부분 usage 또는 알 수 없는 값은 추정하지 않고 `null`로 유지한다.
- V24 DB 제약으로 음수 latency/token, token 합계 불일치, 미등록 상태와 오류 분류를 차단한다.

## 다음 단계

AI-EVAL-3에서 이 Evidence와 immutable Evaluation Contract를 사용해
`manifest + execution-config + case-results + runtime-metrics + trace-index` Bundle을 생성한다.
