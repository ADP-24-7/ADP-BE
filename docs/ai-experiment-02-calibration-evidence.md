# AI Experiment 02 Calibration Evidence

## 목적

AI Experiment 01의 all-WITHHELD 결과를 단순 Finding 건수로 비교하지 않고, 어떤 Data Class가 어떤 Transform을 거친 뒤
Provider 응답에서 reflection됐는지 privacy-safe Evidence로 재현한다. 이 Read Model은 분석 입력이며 Runtime Policy를
자동으로 변경하지 않는다.

## API

`GET /api/admin/ai/evaluation-runs/{evaluationRunId}/calibration-evidence`

- 권한: `PRIVILEGED_OPERATOR`
- 범위: 인증 Principal의 Institution과 Workload allowlist
- 선택 기준: 기존 Evaluation Bundle과 동일한 Case x Model별 최신 COMPLETE 실행
- 최대 실행 수: 기존 Bundle 상한 10,000건
- Schema: `docs/contracts/ai-calibration-evidence.schema.json`

## Evidence 계약

실행별로 Response Guard/Controlled Delivery 상태, reason code, detector version, Finding count와 다음 그룹을 반환한다.

- `finding_type`
- `source_data_class`
- `transform_strategy`
- `field_treatment`
- `count`
- `evidence_digests`

원문 Provider response, exact 민감값, Outbound field path는 반환하지 않는다. Field path는 DB에도 SHA-256 digest만 저장한다.
정규식 자체로 탐지된 Finding은 Outbound field와 결속되지 않으므로 source/transform/treatment가 `null`이다.

## Legacy와 준비 상태

V49 이전 `RAW_VALUE_REFLECTION` row에는 source/transform metadata가 없다. V50은 역사적 Evidence를 임의 backfill하지 않는다.
대신 API가 `calibration_ready=false`와 `REFLECTION_METADATA_MISSING`을 반환한다. Experiment 02는 V50 적용 후 동일 Frozen
Contract로 새 실행을 생성해야 한다.

`finding_count`와 실제 Finding row 수가 다르거나 Response Guard row가 없을 때도 준비 완료로 처리하지 않는다.

## 변경 경계

- 기존 AI Evaluation Bundle v2는 변경하지 않는다.
- Response Guard의 PASS/REJECTED 판단 규칙은 변경하지 않는다.
- Candidate를 ACTIVE로 자동 승격하지 않는다.
- DA 분석에서 Runtime 후보가 생기면 기존 Replay/Shadow/Maker-Checker/Activation/Rollback 경로를 사용한다.
