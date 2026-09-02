# BE-7 AI Full E2E

## 목표

고객상담 AI 요청의 Prompt와 서버가 조회한 RAG Context를 동일한 Canonical Context, Policy Decision, Transform, Outbound Guard 경로로 처리한다. 기관 정책, 승인 범위, Destination Profile은 요청 단위로 고정하며 외부 응답도 전달 전에 재검사한다.

DA `PolicyEvaluation Artifact`는 검증된 분석 handoff이며 활성 Runtime Policy가 아니다. BE는 기존 `PolicySnapshotPort` 경계에서 이를 정규화하고, Runtime Decision을 더 느슨하게 만들지 않는다.

## Runtime 계약

`POST /v1/runtime/executions`의 AI 요청은 다음 값을 필수로 전달한다.

- `institutionId`
- `approvalReference`
- `workloadId`, `purposeCode`, `subjectScope`
- `destinationProfileId`
- `processingContexts`
- `input.prompt`

AI 입력은 `prompt` 이외의 임의 키를 허용하지 않는다. Prompt에서 민감정보가 탐지되면 `UNKNOWN` Data Class로 승격해 Policy Decision이 `REVIEW_REQUIRED`로 귀결되며 Provider Request를 생성하지 않는다.

## 실행 순서

1. AuthN/AuthZ
2. Approval Scope 및 Destination Profile 로드·고정
3. 최소 RAG Context 조회
4. Prompt와 RAG Context의 Canonical Context 합성
5. Policy Snapshot 및 Runtime Decision
6. Transform
7. Approval Scope와 requested/retrieved/transformed/released Field 비교
8. Outbound Guard
9. AI Provider Schema Mapping 및 Canonical JSON Digest 저장
10. AI Connector
11. Response Leakage Guard 및 Controlled Delivery

Policy, Approval, Destination 로딩 실패는 조회 또는 외부 전송 없이 Fail Closed한다. Guard에서 거부된 candidate field는 `released` Field로 기록하지 않는다.

## Policy Harness Binding

승인 재사용 상태는 다음 값으로 고정한다.

- `REUSE_ALLOWED`: 승인 범위와 동일하고 변환이 필요하지 않음
- `TRANSFORM_REQUIRED`: 승인 범위 안에서 정책 변환 후 전송 가능
- `REVIEW_REQUIRED`: 요청 또는 조회 Field가 승인 범위를 벗어나거나 정책 판단이 불완전함
- `BLOCKED`: 기관, 역할, 목적, Destination, 만료 또는 released Field 범위가 불일치함

Trace에는 원문 대신 Institution/Approval/Policy Layer/Destination Version과 각 Field 집합의 식별자, 개수, Digest를 제공한다.

## Destination 및 Provider 증적

AI Destination Profile은 Provider와 함께 Tenant, Region, Retention, Training Use를 고정한다. External Schema Mapper가 실제 Provider Request JSON의 Canonical Digest를 만들며 DB에는 Payload 원문을 저장하지 않는다.

Docker 개발 환경은 `mock-ai` HTTP Provider를 사용한다. HTTP Connector는 상태를 다음과 같이 정규화한다.

- 정상 응답: `ACKNOWLEDGED`
- Provider HTTP 오류: `FAILED`
- 전송 결과를 확정할 수 없는 Timeout: `SENT_UNKNOWN`

Response Guard는 실제 응답 Payload를 메모리에서 검사하고 응답 Digest, Detector Version, Finding Type/Offset/Evidence Digest만 저장한다. 응답에 개인정보 또는 Outbound Raw Value가 재생성되면 `REJECTED`로 차단한다.

## Migration

`V9__add_ai_policy_harness_evidence.sql`은 다음 증적을 추가한다.

- `runtime.policy_harness_binding`
- `runtime.provider_request`
- `runtime.response_sensitive_finding`
- Runtime Execution의 Approval, Policy Layer, Field Lineage, Provider Request/Response Digest
- Destination의 Tenant, Region, Retention, Training Use

기존 V1~V8 데이터에는 nullable column만 추가하며 기존 이력을 변경하지 않는다.

## 검증 범위

- 안전한 Prompt + RAG Context Full E2E
- 민감 Prompt의 `REVIEW_REQUIRED` 및 Provider 미호출
- Approval 미존재와 Destination 미존재 Fail Closed
- 승인 범위 밖 released Field 차단
- Response PII 및 Raw Value Reflection 탐지
- HTTP Provider 성공, 오류, Timeout 상태 정규화
- V1~V9 Flyway 및 기존 V8 upgrade safety 회귀
- API/Trace에서 Raw Prompt, Context, Response 비노출

## 후속 범위

- Production DB-backed Approval Scope Adapter
- DA Versioned Artifact Loader 및 다중 Institution Policy Pack 배포
- 실제 Provider 인증 정보와 Secret Manager 연동
- Response Masking 정책이 필요한 Provider별 Controlled Delivery Adapter
