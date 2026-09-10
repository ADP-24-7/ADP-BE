# Slice 25 Security Finding Read Model

## 목적

운영자가 `findingId`나 `executionId`를 미리 알지 못해도 Provider 응답에서 탐지된 민감정보 Finding을
목록에서 찾고, 원문 노출 없이 Runtime Trace와 Audit Evidence로 이동할 수 있게 한다.

## API

- `GET /api/admin/security-findings`
- `GET /api/admin/security-findings/{findingId}`

목록은 `executionPack`, `workloadId`, `findingType`, `from`, `to`, `page`, `size`를 지원한다.
정렬은 `created_at DESC, id DESC`로 고정한다.

## Scope와 권한

- 조회 역할: `OPERATOR`, `PRIVILEGED_OPERATOR`, `AUDITOR`
- Institution과 허용 Workload는 목록·상세 SQL에서 항상 강제한다.
- Principal 범위 밖의 명시적 `workloadId` 검색은 `403`으로 거부한다.
- 다른 Institution 또는 Workload의 상세 요청은 존재 여부를 노출하지 않고 `404`로 처리한다.
- Pack은 요청값을 신뢰하지 않고 Runtime 시작 시 고정된 `runtime_execution.execution_pack`으로 필터링한다.

## Privacy-safe Projection

Read Model은 기존 `runtime.response_sensitive_finding`, `runtime.runtime_execution`,
`runtime.response_guard_result`를 읽기 전용으로 조합한다. 별도 상태 복제 테이블은 만들지 않는다.

노출 필드는 Finding type, JSON location, offset, detector version, evidence digest, 실행·응답 Guard 상태다.
Provider 응답, 탐지 문자열, Prompt, Subject, Outbound Payload 원문은 반환하지 않는다.

상세 응답의 `tracePath`와 `evidencePath`는 기존 Runtime Trace와 Audit Evidence API를 가리킨다.
