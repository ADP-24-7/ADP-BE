# Implementation Progress

ADP-BE 구현 단계 진행 현황을 추적한다. README는 프로젝트 개요와 실행 방법만 유지하고, 단계 완료 기준과 다음 단계 진입 기준은 이 문서에서 관리한다.

## Current Phase

| Phase | Status | Scope |
| --- | --- | --- |
| BE-0 | Completed | Runtime Contract & Local Foundation |
| BE-1 | Completed | Authentication & Authorization |
| BE-2 | Completed | Internal Data Access Core |
| BE-3 | Completed | Context Builder & Sensitive Detection |
| BE-4 | Completed | Policy & Decision Core |
| BE-5 | Baseline Completed | Transform Engine & Vault |
| BE-6 | In Progress | Common Egress Boundary |

## BE-0 Completion Criteria

- [x] Java 21
- [x] Spring Boot 3
- [x] Gradle
- [x] Modular Monolith 최상위 모듈 경계 고정
- [x] PostgreSQL + Flyway baseline migration
- [x] `request_id`, `trace_id`, `idempotency_key` Runtime Contract
- [x] Error Response Contract
- [x] Reason Code
- [x] Actuator liveness/readiness health
- [x] Docker Compose Local E2E 환경
- [x] Fake Connector
- [x] `PROJECT_PROVISIONAL` Policy Fixture
- [x] DA Artifact 수신 Port
- [x] Application Context Test
- [x] Flyway Migration Test
- [x] Trace propagation / Mock Runtime Flow Test
- [x] Mock Request -> Fake Decision -> Fake Connector -> Audit Record
- [x] Redis/Kafka 미도입

## BE-1 Completion Criteria

- [x] Spring Security 기반 stateless API 인증
- [x] `X-ADP-API-Key` 기반 Service Principal 인증
- [x] API Key 원문 미저장, SHA-256 hash 기반 lookup
- [x] Principal / Role / Workload / API Key PostgreSQL schema baseline
- [x] Local Test Harness credential은 opt-in fixture로 분리
- [x] RBAC role model
- [x] Context 권한 모델
- [x] `RUNTIME_EXECUTOR` 권한 기반 Mock Runtime 실행 인가
- [x] Purpose 검증을 Subject 검증 여부와 독립적으로 적용
- [x] Privileged Action은 `PRIVILEGED_OPERATOR` 권한으로 분리
- [x] 인증 실패 / 인가 실패 공통 `ErrorResponse` 및 reason code 분리
- [x] `/api/internal/auth/context` 인증 컨텍스트 확인 API
- [x] Runtime Service credential / Admin User credential 인증 경계 분리
- [x] Local User Header Stub은 `adp.local-user-auth.enabled=true`에서만 활성화
- [x] Actuator health와 internal info는 인증 없이 조회 허용
- [x] Local Test Harness API key fixture 제공

## BE-2 Completion Criteria

- [x] Workload Registry baseline
- [x] Retrieval Profile baseline
- [x] Dataset/Field allowlist와 Data Class metadata
- [x] Subject Scope, Dataset별 Time Window, Dataset별 Row Limit 기반 Data Access Guard
- [x] 자유 SQL 없이 Workload별 Predefined Retrieval Adapter 사용
- [x] Demo Synthetic Financial Schema
- [x] Synthetic Seed Data는 opt-in local fixture로 분리
- [x] Query/Data Access Audit Metadata 저장
- [x] Audit에는 subject 원문 대신 subject digest 저장
- [x] 조회 결과 원문은 Audit에 저장하지 않고 field/data class/row count만 기록
- [x] 허용 Field만 DB SELECT list에 포함
- [x] 허용 Field가 없는 Dataset은 조회 자체를 생략
- [x] Dataset별 Row Limit와 기간 제한 검증
- [x] 다른 Subject와 다른 Purpose 조회 차단
- [x] Retrieval Profile 없음 또는 Workload 미등록 시 임의 조회 금지

## BE-3 Completion Criteria

- [x] Canonical Context Schema 추가
- [x] Canonical Context Schema Version 고정
- [x] Retrieval 결과를 Canonical Context로 조립하는 Context Builder 추가
- [x] Context Field에 Data Class Metadata 부여
- [x] Runtime DataClass Source of Truth를 서버 Field Catalog에 고정
- [x] 선택되지 않은 원문 Field는 Context 조립 단계에서 제거
- [x] Context/API 응답에는 raw value 대신 value digest 노출
- [x] Subject 원문 대신 subject digest 유지
- [x] `SensitiveDataDetector` Port 추가
- [x] 초기 Rule/Regex Detector Adapter 추가
- [x] 이름, 전화, 계좌, 이메일, 주민번호 형식 탐지 규칙 추가
- [x] Detector Version Metadata 포함
- [x] Detector Finding에 Type, Location, Offset, Evidence Digest 포함
- [x] Unknown Data Class 처리 테스트 추가
- [x] Local 검증용 Context Preview API 추가
- [x] DA `CTRL-RUNTIME-008` / `TEST-021` 대응 Contract Fixture 검증 추가

## BE-4 Tracking

BE-4 상세 구현 기준은 [BE-4 Policy & Decision Core](be-4-policy-decision-core.md)에서 관리한다.

- [x] `PolicyEvaluation` Contract 추가
- [x] `RuntimeDecision` Contract 추가
- [x] Policy Snapshot 모델 추가
- [x] DA Source PolicyEvaluation Artifact identity와 BE PolicySnapshot identity 분리
- [x] Policy evaluation 시점에 선택된 `policy_version + snapshot_digest + effective_at`을 Decision 동안 고정
- [x] `RuntimePolicyContext`와 Applicability Evaluator 경계 추가
- [x] `snapshot_digest + runtime_context_digest` 기반 Decision Identity 고정
- [x] `PolicyAction`과 `FinalAction` 코드 타입 분리
- [x] DA typed reference `ref_id/ref_type/version` 보존
- [x] `processing_contexts[]` 복수 모델 반영
- [x] DA handoff `applicability`, `analysis_status`, `runtime_binding`, `regulatory_data_categories` 보존
- [x] Applicability Evaluator에서 workload/purpose/processing/runtime data class binding 비교
- [x] Runtime processing context 미입력 시 `INCOMPLETE` 판정
- [x] RuntimeDecision/Audit에 DA source artifact version/digest 보존
- [x] `PolicySelectionContext` 기반 scope-aware snapshot selection 경계 추가
- [x] `/v1/runtime/executions` Runtime Execution API 추가
- [x] Runtime Execution -> Retrieval -> Canonical Context -> RuntimeDecision 경로 연결
- [x] Runtime Execution / Policy Evaluation / Runtime Decision 최소 persistence 추가
- [x] Runtime / Governance schema 분리 persistence 적용
- [x] `/v1/runtime/executions` Controller/Service mock flag 의존 제거
- [x] Runtime request input canonical SHA-256 `input_digest` 저장 및 runtime context digest 반영
- [x] Provisional Policy Snapshot fixture scope lookup과 고정 `effective_at` 적용
- [x] Runtime Execution `FAILED` 상태 추가
- [x] `/trace` stage event 응답 분리
- [x] Runtime Execution GET/trace workload object-level authorization 적용
- [x] `(workload_id, idempotency_key)` unique constraint로 중복 실행 차단
- [x] Runtime request validation size를 DB varchar contract와 정렬
- [x] DA Handoff Disposition과 BE `PolicyAction` 분리 Normalizer 경계 추가
- [x] DA Handoff Validator 추가
- [x] Versioned RuntimeDataClass Crosswalk Port와 provisional adapter 추가
- [x] 완화 금지 규칙 강제
- [x] Rule 미매칭, 충돌, Unknown Data Class의 default allow 방지
- [x] Decision Audit Metadata 확장
- [x] 동일 Snapshot + 동일 Canonical Runtime Context 재현성 테스트
- [ ] DA 실제 PolicyEvaluation Artifact 파일 ingest endpoint/loader 추가
- [ ] DA Workload/Purpose Binding Contract 파일 loader 추가
- [ ] DA Crosswalk Contract 파일 loader 추가
- [ ] Policy 교체 시 code path 변경 없이 fixture/snapshot 교체 검증
- [ ] Request ingress 시점 policy catalog revision pinning은 Policy Lifecycle 단계에서 구현

## BE-5 Tracking

BE-5 상세 구현 기준은 [BE-5 Transform Engine & Vault](be-5-transform-vault.md)에서 관리한다.

- [x] BE-5 개발 브랜치 `feature/be-5-transform-vault` 분리
- [x] ADP-DA 최신 `main` 코드와 handoff 문서 확인
- [x] ADP-FE 최신 `main` Runtime Execution 연동 경계 확인
- [x] `MASK`, `HMAC-PSEUDO`, `VAULT-TOKEN`, `REMOVE`, `KEEP`, `GENERALIZE`, `FIELD-SEPARATION` strategy 타입 추가
- [x] DA final mapping 미확정 상태를 고려한 context-aware `TransformStrategyResolver` Port 추가
- [x] 기본 환경 mapping 미구성 시 fail-closed resolver 적용
- [x] `HMAC_PSEUDO`를 local-only stable versioned key provider 기반 HMAC-SHA256으로 수정
- [x] Transform instruction invariant validation 추가
- [x] Transform instruction digest에 TTL 포함
- [x] `TransformEngine` baseline 추가
- [x] `vault.token_mapping` baseline schema와 TTL/key/mapping version 반영
- [x] Vault token 만료 시 과거 row를 덮어쓰지 않는 lifecycle status와 replacement lineage 반영
- [x] `runtime.transform_execution` / `runtime.transform_field` persistence 추가
- [x] Transform field에 strategy/key/mapping version과 instruction digest 저장
- [x] Transform persistence transaction 경계 추가
- [x] Runtime Execution 응답에 raw/source/field-level transformed digest 없는 `privacySafeOutput` 추가
- [x] Transform 성공 후 Runtime Execution status `TRANSFORMED` 반영
- [x] Runtime trace에 Transform stage 반영
- [x] Connector 실행 전 Transform 결과 전달
- [x] Transform/Vault metric baseline 추가
- [x] Strategy failure metric에 low-cardinality error category 추가
- [x] Local fixture에서 `TRANSFORM` final action 경로 검증
- [x] Flyway migration test에 Transform/Vault schema 검증 추가
- [x] Strategy unit test 추가
- [x] Vault same-scope/different-scope/expired-token/concurrent-token test 추가
- [x] Vault failure 시 Runtime FAILED 및 Connector 미실행 test 추가
- [x] Vault/HMAC transform scope isolation 추가
- [x] Policy/Snapshot provenance와 token namespace 분리
- [x] Expired token concurrent replacement lineage test 추가
- [x] Default/local fixture transform wiring test 추가
- [ ] Privileged Re-map API
- [ ] Vault 장애 fallback 정책
- [ ] DA 실제 transform policy artifact loader 연동
- [x] Connector boundary를 BE-6 `OutboundCandidatePayload`/Outbound Guard로 분리
- [ ] 실제 provider payload canonical digest는 Pack별 schema mapper 단계에서 구현
- [ ] Reversible vault가 필요해질 경우 KMS 기반 encrypted mapping 저장소 추가

## BE-6 Tracking

BE-6 상세 구현 기준은 [BE-6 Common Egress Boundary](be-6-common-egress-boundary.md)에서 관리한다.

- [x] BE-6 개발 브랜치 `feature/be-6-common-egress-boundary` 분리
- [x] Notion Backend Phase 상태와 main 코드 상태 비교
- [x] BE-5 Baseline 완료와 BE-6 진행 상태 문서 동기화
- [x] Execution Pack Type baseline 추가
- [x] Destination Profile baseline Port 추가
- [x] Field Obligation / Treatment baseline 추가
- [x] Transform 결과를 `OutboundCandidatePayload`로 조립하는 Builder 추가
- [x] Connector Port 입력을 `TransformResult`에서 `OutboundCandidatePayload`로 변경
- [x] Outbound Guard Chain baseline 추가
- [x] Response Guard Port baseline 추가
- [x] `destination_profile`, `outbound_candidate`, `connector_execution`, `response_guard_result` persistence 추가
- [x] Runtime trace에 Outbound Guard / Connector / Response Guard stage 반영
- [x] Guard 거부 및 Connector 우회 방지 테스트 추가
- [x] ALLOW 경로에서 Canonical Context 기반 OutboundCandidate 조립
- [x] Field Obligation Source of Truth를 Destination Field Contract로 이동
- [x] Versioned Destination Profile identity 추가
- [x] Runtime request 단위 Destination Profile id/version/digest pinning 추가
- [x] `providerProfileId` 요청 입력을 `destinationProfileId`로 정리
- [x] Destination Profile `effectiveAt`/`expiresAt` enforcement 추가
- [x] Outbound Guard reject 결과 persistence 후 BLOCK 처리
- [x] Authorization 이후 Destination Profile load/pin 순서 보장
- [x] Runtime status와 Connector/Response Guard external status 분리
- [x] Connector `FAILED`는 Runtime `FAILED`로 귀결되도록 상태 의미 분리
- [x] Connector external status enum 및 DB check constraint 추가
- [x] Response Guard가 response metadata 없이 `PASSED`를 기록하지 않도록 수정
- [x] No-op Response Guard와 local fixture Response Guard 분리
- [x] Outbound Sensitive Finding Detector baseline 추가
- [x] Secret Guard가 detector finding을 소비하도록 경계 분리
- [x] BE-6 Guard/Destination/Connector/Response Guard metric baseline 추가
- [ ] Pack별 외부 schema mapper 분리
- [ ] Destination Profile DB-backed adapter
- [ ] Response leakage detector adapter
- [ ] Provider별 connector status 정규화
- [ ] 실제 provider payload canonical JSON digest 저장
- [ ] 실제 provider response digest 저장
- [ ] Pack별 실제 response leakage detector 연결

## Troubleshooting

리뷰 및 검증 과정에서 반복적으로 확인해야 했던 이슈는 [Troubleshooting Index](troubleshooting/index.md)에서 별도로 관리한다.
