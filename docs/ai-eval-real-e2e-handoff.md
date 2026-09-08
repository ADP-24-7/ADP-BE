# AI Evaluation Real E2E Handoff

## 목적

등록된 Evaluation Run과 실제 Provider 실행을 구분하고, BE Runtime DB의 Case x Model Evidence가 DA Bundle
Export 조건을 충족하는지 운영자가 확인할 수 있게 한다. 실행은 기존 Runtime API를 사용하며 관리자 API가 Runtime
인증, Policy, Transform, Egress 경계를 우회해 Provider를 직접 호출하지 않는다.

## 로컬 실행 경계

- 통합 Stack 진입점: ADP-BE `docker-compose.yml`
- Host BE URL: `http://127.0.0.1:8080`
- Container BE URL: `http://adp-be:8080`
- Runtime 인증: `X-ADP-API-Key`, 로컬 fixture는 `local-dev-api-key`
- 관리자 조회: `X-ADP-User-Id`와 `X-ADP-User-Roles: PRIVILEGED_OPERATOR`
- 실제 NVIDIA Credential: ADP-BE `.env`의 `NVIDIA_API_KEY`
- 로컬 평가 Read Timeout: `ADP_AI_CONNECTOR_READ_TIMEOUT=60s`

로컬 User Header 인증은 `ADP_LOCAL_USER_AUTH_ENABLED=true`에서만 활성화한다. NCP 환경에서는 이 Header를 관리자
인증으로 사용하지 않고 배포 환경의 IdP/JWT 또는 동등한 운영 인증으로 교체한다.

## 실행

ADP-BE `.env`에 유효한 `NVIDIA_API_KEY`를 주입하고 로컬 평가용
`ADP_AI_CONNECTOR_READ_TIMEOUT=60s`를 확인한 뒤 다음을 실행한다. 애플리케이션의 기본 5초 timeout은 운영 경계이며,
`.env.example`의 60초는 세 모델 latency를 수집하기 위한 로컬 평가 설정이다.

```bash
make docker-up
make ai-eval-e2e
```

`make ai-eval-e2e`는 다음 순서를 수행한다.

1. BE readiness 확인
2. `POST /v1/runtime/executions`로 등록된 세 Model Profile 실행
3. 각 실행의 server-owned Execution ID 확인
4. Evaluation Run Readiness 조회
5. Case 1 x Model 3의 COMPLETE Evidence 확인
6. DA Evaluation Bundle JSON Export

실행별 Runtime 응답은 임시 디렉터리에서만 사용하고 제거한다. DA 전달물은 원문 Prompt/Response를 포함하지 않는
`readiness.json`과 `bundle.json`이며 기본 경로는 `build/ai-evaluation-e2e/{run-suffix}/`다.

실제 Provider가 실패해도 Connector 결과와 AI Execution Evidence가 COMPLETE로 기록되면 Bundle은 생성될 수 있다.
이는 실패율과 오류 분류도 DA 분석 대상이기 때문이다. HTTP 호출 자체가 Runtime 계약에 도달하지 못했거나 Evidence가
PARTIAL이면 Readiness는 READY가 되지 않는다.

## Readiness API

`GET /api/admin/ai/evaluation-runs/{evaluationRunId}/readiness`

- 권한: `PRIVILEGED_OPERATOR`
- Institution/Workload: 인증 Principal scope를 SQL에 적용
- 상태: `NOT_STARTED`, `INCOMPLETE`, `PROVENANCE_MISMATCH`, `MODEL_MISMATCH`, `READY`
- 반환: 기대 수, DB 전체 저장 실행 수, Bundle이 선택한 최신 관측 수, COMPLETE/누락/예상 외 실행 수와
  Case x Model별 Execution/Evidence 상태
- 비노출: Prompt, RAG Context, Provider Response, Subject, Idempotency Key, Credential

`bundle_available=true`는 현재 선택된 최신 Case x Model Evidence가 Bundle Export 검증을 통과할 수 있음을 뜻한다.
실제 Bundle API는 동일한 provenance와 model identity를 다시 검증하는 authoritative export boundary다.
`stored_execution_count`는 재실행을 포함한 전체 Evidence 수이고 `observed_execution_count`는 Bundle이 Case x Model별
최신 실행을 선택한 뒤의 수다.

## DA 전달

DA에는 다음 파일 또는 동일 Bundle API 접근 권한만 전달한다.

```text
build/ai-evaluation-e2e/{run-suffix}/readiness.json
build/ai-evaluation-e2e/{run-suffix}/bundle.json
```

DA는 `manifest.bundle_id + manifest.content_digest`를 분석 결과에 함께 저장하고, JSON Schema, content digest,
Case x Model Cartesian Product, Execution identity, failure summary를 독립적으로 재검증한다.
