# Slice 25 Review Queue Read Model

## 목적

운영자가 `executionId`를 미리 알지 못해도 현재 권한 범위의 `REVIEW_REQUIRED` 실행을 목록에서 찾고,
판단 근거와 다음 조사 경로를 확인할 수 있게 한다.

## API

- `GET /api/admin/review-queue`
- `GET /api/admin/review-queue/{executionId}`

목록은 `executionPack`, `workloadId`, `page`, `size`를 지원한다. Runtime 상태는 서버가 항상
`REVIEW_REQUIRED`로 제한하며 caller가 다른 상태를 Review Queue에 섞을 수 없다.

## Scope와 권한

- 조회 역할: `OPERATOR`, `PRIVILEGED_OPERATOR`, `AUDITOR`
- Institution과 허용 Workload는 모든 목록·상세 SQL에서 강제한다.
- 명시적인 `workloadId`가 Principal 범위 밖이면 `403`으로 거부한다.
- 다른 Institution 또는 Workload의 상세 요청은 존재 여부를 노출하지 않고 `404`로 처리한다.

## Projection

목록과 상세는 Runtime, Pack Policy, Recovery, Digital Asset Post-execution Evidence를 read-only로 조합한다.
새로운 상태 복제 테이블은 만들지 않는다.

- `POLICY`: Policy/Detection 판단으로 Review 전환
- `RECOVERY`: `MANUAL_REVIEW` 또는 `EXHAUSTED` Recovery
- `POST_EXECUTION`: Digital Asset 결과 Evidence 검토 필요

응답에는 reason code, digest, typed status와 Trace/Evidence API 경로만 포함하며 prompt, subject, provider
payload 같은 원문은 포함하지 않는다.
