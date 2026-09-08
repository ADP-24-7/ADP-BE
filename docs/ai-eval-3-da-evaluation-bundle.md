# AI-EVAL-3 DA Evaluation Bundle Export

## 목적

BE Runtime DB에 직접 접근하지 않고도 DA가 Evaluation Run별 모델·케이스 결과와 실행 증적을
사후 평가할 수 있도록 privacy-safe Bundle을 제공한다.

## API

`GET /api/admin/ai/evaluation-runs/{evaluationRunId}/bundle`

- 권한: `PRIVILEGED_OPERATOR`
- 범위: 인증 Principal의 `institutionId`와 허용 `workloadIds`를 SQL에서 항상 적용
- 결과 없음 또는 접근 불가: `404 AI_EVALUATION_BUNDLE_NOT_FOUND`
- Schema: `adp-ai-evaluation-bundle/v1`

실제 실행 전후의 Case x Model 저장·완전성은
`GET /api/admin/ai/evaluation-runs/{evaluationRunId}/readiness`에서 확인한다. 이 API는 재실행을 포함한 DB 전체 저장
건수와 Bundle이 선택하는 Pair별 최신 실행 건수를 구분하며 동일한 `PRIVILEGED_OPERATOR`, Institution, Workload
scope를 적용한다. 로컬 세 모델 실행과 Export 절차는
[`AI Evaluation Real E2E Handoff`](ai-eval-real-e2e-handoff.md)를 따른다.

## Bundle 구성

- `manifest`: Bundle ID/version, Schema/Run version, content digest, 생성 시각, 실행·케이스·모델 수, Evidence 기간
- `execution_config`: Dataset/Policy/Evaluation Contract provenance와 모델별 고정 설정
- `case_results`: Runtime/Policy/Response Guard/Delivery 결과와 input digest 일치 증적
- `runtime_metrics`: full-response 또는 attempt latency, token usage 상태, Provider 상태와 오류 분류
- `failure_summary`: 전체 평가 Execution 수, failed/sent-unknown/not-attempted 및 error category별 집계
- `trace_index`: server-owned Execution/Decision/Connector reference와 request/response digest

DA Artifact 관례에 맞춰 Bundle JSON field는 `snake_case`를 사용한다. 상세 계약은
[`contracts/ai-evaluation-bundle.schema.json`](contracts/ai-evaluation-bundle.schema.json)에서 관리한다.
성공 Bundle Schema는 authoritative provenance digest를 non-null로 요구하고 Runtime/Provider/Evidence 상태를
Producer enum으로 제한한다. `evidence_status`는 `COMPLETE`만 허용한다.

`manifest.bundle_id`는 동일 Evaluation Run/version을 나타내는 logical identifier다. 최신 Case x Model 실행이
바뀌어도 이 값은 유지된다. `manifest.content_digest`는 해당 응답에 포함된 immutable Bundle snapshot의
identifier이므로 DA는 평가 결과에 두 값을 모두 저장해야 한다.

`manifest.content_digest`는 manifest를 제외한 `schema_version`, `execution_config`, `case_results`,
`runtime_metrics`, `failure_summary`, `trace_index`를 key 오름차순, null 포함, ISO-8601 date-time,
compact UTF-8 JSON으로 canonicalize한 뒤 SHA-256으로 계산한다. 전자서명이나 외부 anchoring을 의미하지 않는다.
`manifest.generated_at`은 실제 Export 응답 생성 시각이며 digest 대상이 아니다. `manifest.execution_from`과
`manifest.execution_cutoff_at`은 선택된 Runtime Execution의 `created_at` 최솟값과 `updated_at` 최댓값이다.
Evidence 테이블의 기록 시각이 아니라 선택된 실행의 처리 기간을 나타낸다.

## 완전성 및 신뢰 경계

- `AiEvaluationRunCatalog`의 등록 Case × Model Profile Cartesian Product를 기대 집합으로 사용한다.
- 각 Pair는 최신 `created_at`, `execution_id` 기준 Execution 1건만 선택해 재실행 횟수에 따른 평가 왜곡을 막는다.
- Port 반환 순서와 무관하게 Case ID, Model Profile ID, Execution ID 순으로 정렬해 digest 결정성을 보장한다.
- 기대 Pair 누락, 최신 Evidence의 `PARTIAL`, 중복 projection은 `AI_EVALUATION_BUNDLE_INCOMPLETE`로 거부한다.
- 따라서 성공한 Bundle에는 PARTIAL Evidence가 없으며 `failure_summary`에도 상수인 partial count를 제공하지 않는다.
- Run/Dataset/Policy/Case input provenance는 `AiEvaluationRunCatalog`와 다시 비교한다.
- Model/Sampling/Destination provenance는 `AiModelProfileCatalog`와 다시 비교한다.
- 상충 시 각각 `AI_EVALUATION_BUNDLE_PROVENANCE_MISMATCH`, `AI_EVALUATION_BUNDLE_MODEL_MISMATCH`로 거부한다.

## 데이터 경계

- Prompt, RAG Context, Provider Response 원문을 Export하지 않는다.
- Subject, Idempotency Key, Provider Credential을 Export하지 않는다.
- caller-provided Request ID와 Trace ID 대신 server-owned Execution ID를 Trace 기준으로 사용한다.
- Bundle 사본이나 Export 상태를 별도 저장하지 않고 V22~V24의 실행 시점 Evidence를 조회한다.
- DA 소비자는 섹션별 Execution ID 집합, manifest 집계, Case×Model 유일성, input digest 일치를 재검증한다.
- DA 소비자는 manifest의 `content_digest`, Case×Model Cartesian Product, failure summary도 payload에서 재계산한다.
- 서로 다른 Run/Dataset/Policy provenance 또는 동일 Profile의 상충 설정이 섞이면 fail closed한다.

## 운영 한계

현재 Bundle은 동기 JSON 응답이며 등록 Case × Model 수가 10,000건을 넘으면
`AI_EVALUATION_BUNDLE_SIZE_LIMIT_EXCEEDED`로 거부한다. Dataset 확장으로 상한을 넘는 Bundle은 NCP Object
Storage 비동기 Export로 전환한다. 전환 전에는 전체 실행 이력을 메모리에 적재하는 API로 확장하지 않는다.
크기 검증은 Institution/Workload scope 조회에서 접근 가능한 Evidence가 확인된 뒤 수행해 접근 불가 Run의
존재 여부나 규모를 오류 코드로 노출하지 않는다.

## DB

V25는 `evaluation_run_id, eval_case_id, profile_id, execution_id` 부분 인덱스만 추가한다.
평가 원문 또는 별도 Bundle payload 테이블은 추가하지 않는다.

동일 Case x Model 재실행 누적 시 latest-selection query 비용은 후속 운영 검증에서
`EXPLAIN (ANALYZE, BUFFERS)`로 측정한다. 현재 동기 Export 상한 내에서는 별도 인덱스를 추정으로 추가하지 않는다.

## 현재 버전 해석 한계

현재 `AiEvaluationRunCatalog`와 `AiModelProfileCatalog`는 static baseline의 현재 버전만 resolve한다. 다중
Run/Profile 버전을 운영하기 전에는 `(runId, runVersion)`, `(profileId, profileVersion)` 기반 immutable persisted
catalog로 전환해야 한다. 그렇지 않으면 현재 Profile 갱신 후 과거 Evidence Export가 mismatch로 거부될 수 있다.
