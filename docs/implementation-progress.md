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
| BE-6 | Baseline Completed | Common Egress Boundary |
| BE-7 | Completed | AI Full E2E & Policy Harness Binding |
| BE-8 | In Progress | Digital Asset Thin E2E & Recovery |
| BE-9 | Baseline Completed | Consistency, Recovery & Operations |
| BE-10 | Skeleton In Progress | Policy Lifecycle & Governance |
| BE-11 | Baseline Completed | Observability Operations |
| NCP-5 | Completed | BE NCP ContentStore & DA Handoff E2E |
| Slice 30 | Completed | Local Integration Lock, Profiles & One-command Verification |
| Slice 32 | Completed | Production Reference Architecture & Claim Boundary |

## Slice 32 Production Reference Architecture

- [x] 중앙 API Gateway와 private ingress를 기본 배치로 정의
- [x] OIDC Admin Session과 mTLS Service Identity를 설계 전용 범위로 분리
- [x] PostgreSQL/Object Storage의 Source of Truth와 HA/retention 책임 정의
- [x] Secret Manager/KMS와 Egress Proxy/Firewall 목표 경계 정의
- [x] Prometheus/Alertmanager/SIEM 책임과 low-cardinality 원칙 정의
- [x] additive Migration, immutable image, Policy/Runtime rollback 책임 분리
- [x] Local/NCP Foundation 검증과 Cloud/HA/DR 미검증 Claim 분리
- [x] Machine-readable Architecture Contract와 CI drift 검증 추가

## Slice 30 Local Integration Reproducibility

- [x] BE Compose를 네 Repository 통합 실행 Source of Truth로 유지
- [x] FE·BE·DA·Docs Commit SHA와 Flyway current를 Repository Lock으로 고정
- [x] Checkout drift, dirty tracked file, 필수 입력 누락, Migration drift 사전 차단
- [x] `local`, `demo`, `production-like` 환경 Profile 분리
- [x] Secret `.env`와 versioned non-secret Profile 책임 분리
- [x] 전체 Container health와 Runtime Profile, Flyway current 검증 명령 추가
- [x] Demo 합성 데이터 Provenance를 BE metadata와 FE UI에 표시
- [x] 고정 Commit 상태에서 전체 Docker Stack 재기동 검증

## AI Runtime Evaluation Tracking

AI-EVAL-0 상세 계약은 [NVIDIA Model Connector And Profiles](ai-eval-0-nvidia-model-profiles.md)에서 관리한다.

- [x] NVIDIA NIM Chat Completions 호환 HTTP Connector 인증
- [x] Nemotron 3.5 Lightning, Muse Glimmer 30B, Gemma 4 31B IT 서버 소유 Profile Allowlist
- [x] Runtime Request의 임의 Model ID 비수용 및 Destination/Approval 기반 선택
- [x] 동일 AI Full E2E Runtime을 통한 세 Profile local smoke test
- [x] Timeout/HTTP Error/Success의 `SENT_UNKNOWN`/`FAILED`/`ACKNOWLEDGED` 정규화
- [x] AI-EVAL-1 Evaluation Run/Case/Dataset Version 계약
  - Branch: `feature/ai-eval-1-evaluation-run-contract`
  - Migration: V23
  - Runtime request hash와 AI execution evidence에 서버 소유 평가 조건 고정
- [x] AI-EVAL-2 Runtime Timing/Token/Failure Evidence
  - Branch: `feature/ai-eval-2-runtime-evidence`
  - Migration: V24
  - HTTP response timing, provider latency, token usage, provider status/error category와 Runtime trace 결합
- [x] AI-EVAL-3 DA Evaluation Bundle Export
  - Branch: `feature/ai-eval-3-da-evaluation-bundle`
  - Migration: V25 evaluation run export index
  - Privileged, institution/workload-scoped Bundle API와 재현 가능한 content fingerprint
  - Run Catalog Case × Model 완전성 및 Run/Profile Source of Truth 재검증
  - Failure Summary, 전용 integrity reason/metric, DA parser fixture
- [x] AI Evaluation Real E2E Handoff
  - Privileged Evaluation Run Readiness API
  - DB 전체 저장 실행과 Bundle 최신 선택 실행 수 분리
  - 기존 Runtime API 기반 세 NVIDIA Profile 실행 및 Bundle Export harness
  - 이전 버전 DB의 local principal institution binding 복구
- [x] AI Experiment 02 BE Calibration Evidence 지원
  - Branch: `feature/ai-experiment-02-calibration-evidence`
  - Migration: V50
  - `RAW_VALUE_REFLECTION`을 Data Class, Transform Strategy, Field Treatment와 privacy-safe하게 결속
  - Privileged, Institution/Workload-scoped Calibration Evidence API와 versioned JSON Schema
  - Legacy reflection metadata 누락과 Finding aggregate 불일치를 `calibration_ready=false`로 fail closed
  - 기존 Evaluation Bundle v2, Response Guard 판정, Runtime Policy는 변경하지 않음

## Security Cross-cutting Tracking

SEC-0 상세 계약은 [SEC-0 Security Contract](sec-0-security-contract.md)에서 관리한다. Security Track은 공식 BE Phase 진행률과 분리한다.

- [x] FPG와 기존 WAF/API Gateway/IAM/Network 보안 책임 경계
- [x] 보호 자산과 Caller/DB/Policy/Connector/Provider/Recovery/Admin 신뢰 경계
- [x] Default Deny, Context Authorization, Minimum Retrieval, Destination, Integrity, Audit 불변조건
- [x] 기존 보안 테스트 증적과 잔여 위협을 연결한 Negative Test Matrix
- [x] HTTP 공개 endpoint allowlist와 default-deny 통합 테스트
- [x] Security matcher 누락 endpoint의 인증 Principal 접근도 `denyAll`로 차단
- [ ] SEC-1 Destination SSRF Negative Test와 Request Freshness
- [ ] SEC-1 Denied Attempt Evidence
- [ ] SEC-1 Caller Trace ID의 server-owned internal ID 또는 digest correlation
- [ ] SEC-2 Secret/SAST/Dependency/Container Scan과 SBOM CI Gate

## BE-8 Tracking

BE-8 상세 계약은 [BE-8 Digital Asset Thin E2E](be-8-digital-asset-thin-e2e.md), [BE-8 Digital Asset Recovery](be-8-digital-asset-recovery.md), [BE-8 Digital Asset Mismatch Recovery](be-8-digital-asset-mismatch-recovery.md)에서 관리한다.

최신 DA Handoff에 따른 책임경계 변경은 [DA-P0-1 Runtime Realignment Impact](digital-asset-runtime-realignment-impact.md)를
Source of Truth로 사용한다. 아래 완료 항목은 PR #14~#17의 역사적 baseline이며 Active Runtime은 P0-2부터 재정렬한다.

- [x] 기존 단일 Runtime API의 Digital Asset Pack 연결
- [x] Input-only 최소 Retrieval Profile과 명시적 입력 Schema
- [x] Digital Asset Policy/Approval/Destination Profile local fixture
- [x] Customer/Account Vault Tokenization과 Wallet/Asset/Amount exact treatment
- [x] Canonical Provider Request Digest와 Mock Asset Platform Connector
- [x] Transport 성공과 Settlement finality를 분리하는 Pack Outcome Handler
- [x] FPG expected/actual 비교 기반 Reconciliation과 V16/V17 상태별 nullable evidence schema
- [x] Subject Scope와 Transaction Customer ID binding
- [x] Provider external request correlation equality 검증
- [x] 외부 호출 이후 local outcome finalization transaction
- [x] 정상 Settlement `SETTLED`/`MATCH` Thin E2E
- [x] Versioned Local Profile 기반 KYC/AML/Wallet Review 및 Amount Limit Policy Gate
- [x] `SENT_UNKNOWN` Digital Asset Local Provider Status Query Adapter와 공통 Recovery 수렴
- [x] Mismatch/Critical Mismatch privacy-safe case 격리와 자동 재시도 금지 E2E
- [ ] FE Digital Asset Lab fixture 연결

## Digital Asset Runtime Realignment Tracking

- [x] DA-P0-1 PR #14~#17 코드/DB/Test 영향도 분류
- [x] V16~V20 비파괴 additive migration 원칙 확정
- [x] Eligibility Reason Code retain/deprecated 목록 확정
- [x] DA-P0-2 server-owned Approved Transaction 선행 경계 + KYC/AML/Wallet Active Eligibility Gate 분리
- [x] DA-P0-3 ApprovedTransaction/OutboundRequest/ExternalExecutionResult 계약
- [x] DA-P0-4 Canonical Identifier/Enum/Schema Freeze
- [x] DA-P0-5 Artifact Loader v1
- [x] DA-P0-6 Versioned Runtime Snapshot
- [x] NCP-5 BE NCP ContentStore Adapter와 fail-closed storage boundary
- [x] NCP-5 실제 QA Object Storage ingest E2E 증적
- [x] DA-P0-7 PRE_EXECUTION 6 Runtime Controls와 Connector 직전 TOCTOU 재검증
- [x] DA-P0-8 Transaction/Receipt/Finality/Transfer Resolver와 POST_EXECUTION Evidence
- [x] DA-P0-8 Approved/Requested/Executed digest re-binding과 Runtime Trace 노출
- [x] DA-P0-8 typed Provider `SENT_UNKNOWN` 및 외부 성공 후 Local 실패 Recovery 연결
- [x] Digital Asset Local Product 6-Case E2E Closure
  - Branch: `feature/digital-asset-runtime-6-case-e2e`
  - DA PR #31의 고정 JSON Fixture를 실제 `/v1/runtime/executions` 경로로 실행
  - BLOCK 2종 External Effect 0, 실패 receipt, SENT_UNKNOWN reconcile-first, duplicate replay 검증
  - independent recovered evidence와 PostgreSQL/Runtime Trace 최종 상태 연결
- [x] DA Industry Analysis to BE Runtime Contract Gap Review
  - Branch: `feature/da-runtime-contract-gap-review`
  - Decision: 현재 P0-3~8 계약 유지, semantic asset classification은 versioned Handoff 전까지 미도입
  - Guard: caller-controlled `semanticAssetClass`를 strict parser에서 fail-closed

DA-P0-2의 Active Runtime 계약과 fail-closed 증적은
[DA-P0-2 Approved Transaction Trust Boundary](da-p0-2-approved-transaction-boundary.md)에서 관리한다.
P0-3의 최종 Java 타입, strict input/result validation, canonical execution tuple은
[DA-P0-3 Digital Asset Domain Contracts](da-p0-3-domain-contracts.md)에서 관리한다.
P0-4의 versioned Schema, manifest, canonical digest와 DA/FE sample은
[DA-P0-4 Canonical Contract Freeze](da-p0-4-canonical-contract.md)에서 관리한다.
P0-5의 Bundle/Schema/digest/reference 검증, scoped metadata 저장과 Lifecycle Candidate 연결은
[DA-P0-5 Artifact Loader](da-p0-5-artifact-loader.md)에서 관리한다.
P0-6의 ACTIVE 선택, 실행별 불변 Snapshot, Replay/Recovery 재사용과 Trace/Audit 증적은
[DA-P0-6 Versioned Runtime Snapshot](da-p0-6-versioned-runtime-snapshot.md)에서 관리한다.
P0-7의 Approved/Requested 재검증, exact/transform 분리, destination payload와 trace binding은
[DA-P0-7 PRE_EXECUTION Guard](da-p0-7-pre-execution-guard.md)에서 관리한다.
NCP-5의 server-owned Object Storage 설정, content-addressed reference와 실제 ingest 절차는
[NCP-5 BE ContentStore](ncp-5-be-content-store.md)에서 관리한다.
`regulatoryOutboundData`는 source/allowlist/provider mapping이 별도로 고정될 때까지 empty-only를 유지한다.
typed Provider `SENT_UNKNOWN` recovery 연결은 P0-8에서 완료했다. 실제 Provider별 Status Query/Resolver Adapter는
Provider 연동 시 동일 Port 계약으로 추가한다.
DA 산업 분석의 Stablecoin, Deposit Token, RWA 확장성 검토와 no-change 근거는
[DA Analysis to BE Runtime Contract Gap Review](da-analysis-runtime-contract-gap-review.md)에서 관리한다.

## BE-10 Parallel Tracking

BE-10 Skeleton 계약은 [BE-10 Policy Lifecycle Skeleton](be-10-policy-lifecycle-skeleton.md)에서 관리한다.

- [x] Lifecycle 상태 enum과 단방향 Transition Validation
- [x] Institution·Workload scope 기반 Lifecycle metadata
- [x] OPERATOR/PRIVILEGED_OPERATOR/AUDITOR 권한 분리
- [x] APPROVED/ACTIVE Maker-Checker 강제
- [x] V21 Artifact current state와 append-only Transition Evidence
- [x] Optimistic revision 기반 동시 전이 차단
- [x] Raw-free server-defined Transition Reason Code
- [x] DA Artifact Bundle Loader와 Schema Validation
- [x] Replay/Shadow Decision 실행 및 Diff Evidence
  - [x] Replay-stage Candidate와 단일 ACTIVE baseline의 server-owned Shadow 평가
  - [x] 동일 Evaluation Case Version/Input Digest 검증과 raw-free typed Diff Evidence
  - [x] Shadow 경로 Connector/External Action 비호출 및 evaluator 미구성 fail-closed
  - [x] Shadow Evidence Gate와 Lifecycle `SHADOW -> APPROVED` 연결
    - Branch: `feature/be-10-shadow-approval-gate`
    - Migration: V34
    - 최신 MATCH Evidence, Privileged Maker-Checker, Candidate/Baseline 재검증과 Transition Evidence binding
- [x] Active Policy Runtime Selection과 Rollback Propagation

## BE-11 Parallel Tracking

BE-11A 상세 계약은 [BE-11A Observability Foundation](be-11a-observability-foundation.md)에서 관리한다.

- [x] Prometheus registry와 `/actuator/prometheus` scrape endpoint
- [x] `METRICS_SCRAPER` 전용 Service Principal을 통한 전역 Metric 접근 분리
- [x] Runtime terminal transition metric과 기본 HTTP request metric
- [x] Idempotency resolution metric
- [x] Recovery processing outcome metric
- [x] Enum 기반 low-cardinality outcome
- [x] Logstash JSON console log와 MDC `request_id`/`trace_id`
- [x] 원문 Payload·Subject·Secret metric/log 비노출 규칙
- [x] BE-11B Institution-scoped Runtime/Audit 검색 Read Model
- [x] BE-11B Privileged Evidence Export와 조회 시점 content fingerprint
- [x] BE-11B raw payload·subject·idempotency key·provider correlation key 비노출
- [x] BE-11B V14 검색 인덱스와 권한/검색/export 통합 테스트
- [x] Artifact Lifecycle·Activation·Rollback Evidence Read Model
- [x] Policy Lifecycle·Current Selection·Rollback low-cardinality metric
- [x] Post-commit Metric 장애와 영속 업무 결과 격리
- [x] Recovery backlog depth·oldest age·manual review·exhausted gauge
- [x] Stale Recovery Operation count·oldest age gauge와 Alert
- [x] Security freshness·authorization·destination rejection metric
- [x] Institution/Workload scoped Monitoring Summary API
- [x] Recovery backlog·exhausted·policy drift·rollback spike·runtime failure Alert Rule
- [ ] NCP scrape 인증·network policy·retention

## BE-9A Parallel Tracking

BE-9A 상세 계약과 후속 범위는 [BE-9A Idempotency Core](be-9a-idempotency-core.md)에서 관리한다.

- [x] Institution + Workload + Idempotency Key namespace
- [x] 인증된 Principal Institution 기반 namespace ownership
- [x] Authorization 성공 이후 idempotency reservation
- [x] Canonical request hash와 동일 요청 replay
- [x] 다른 request hash conflict 및 동시 실행 방지
- [x] BE-9B terminal/transient/SENT_UNKNOWN retry·reconciliation core 상태 계약
- [x] Terminal 기준 Idempotency key retention, expiry, lazy namespace archive
- [x] BE-11 Denied Attempt Evidence persistence
- [ ] Controlled delivery 결과 재조회 또는 단기 암호화 보관 계약

## BE-9B Parallel Tracking

BE-9B 상세 계약은 [BE-9B External Interaction Recovery Core](be-9b-external-interaction-recovery.md)에서 관리한다.

- [x] External interaction retry disposition 계약
- [x] `SENT_UNKNOWN` reconciliation-first 강제
- [x] Recovery job persistence와 attempt limit
- [x] PostgreSQL claim/lease 및 `SKIP LOCKED` worker 경계
- [x] Provider status query Port와 미구성 fail-closed adapter
- [x] Provider-visible correlation key 전송 및 Recovery binding
- [x] Recovery/Connector/Runtime 상태의 원자적 reconciliation convergence
- [x] Status Query 최종 상태·시각·Evidence Digest 저장
- [x] Status Query Adapter ambiguity fail-closed
- [x] `MANUAL_REVIEW`/`EXHAUSTED`와 Runtime `REVIEW_REQUIRED` 원자적 수렴
- [x] Lease 만료 시 stale worker terminal update 차단
- [x] Connector별 status query/retry Port와 Local Digital Asset Adapter
- [x] 상태 조회에서 `NOT_SENT`가 확인된 경우에만 실행되는 안전한 재전송 executor
- [x] 지수 backoff, bounded batch, opt-in 운영 scheduler
- [x] Institution/Workload scoped Incident List/Detail API
- [x] Privileged Reconcile/Retry/Review API와 operation idempotency evidence
- [x] 수동 명령과 scheduler의 공통 claim/lease/stale worker 경계
- [ ] 실제 Provider별 Status/Retry Adapter와 versioned Recovery Policy
- [x] Evidence 삭제 없는 Idempotency retention과 lazy namespace archive

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

## BE-7 Tracking

BE-7 상세 구현 기준은 [BE-7 AI Full E2E](be-7-ai-full-e2e.md)에서 관리한다.

- [x] BE-7 개발 브랜치 `feature/be-7-ai-full-e2e` 분리
- [x] ADP-DA 최신 PolicyEvaluation Handoff 계약과 책임 경계 확인
- [x] ADP-FE 최신 Runtime Execution/Gateway Lab 계약 확인
- [x] AI 고객상담 Workload 요청 계약에 Institution/Approval Reference 추가
- [x] Prompt와 RAG Context를 동일 Canonical Context로 합성
- [x] Prompt 허용 키 검증 및 민감 Prompt Fail Closed
- [x] Approval Scope Port와 local fixture adapter 추가
- [x] Institution/Role/Workload/Purpose/Processing Context/Destination scope 비교
- [x] `REUSE_ALLOWED`/`TRANSFORM_REQUIRED`/`REVIEW_REQUIRED`/`BLOCKED` 계산
- [x] Institution Policy/Workload Policy/Destination Profile Layer 고정
- [x] requested/retrieved/transformed/released Field lineage 저장
- [x] AI Destination의 Tenant/Region/Retention/Training Use 고정
- [x] AI External Schema Mapper와 실제 Provider Request Canonical JSON Digest 저장
- [x] HTTP AI Connector와 ACKNOWLEDGED/FAILED/SENT_UNKNOWN 정규화
- [x] Mock AI HTTP Provider Docker service 추가
- [x] Response Leakage Detector 및 Finding metadata persistence 추가
- [x] 실제 Provider Response Digest와 Response Guard 결과 저장
- [x] AI Lab용 privacy-safe Runtime Trace Read Model 확장
- [x] V9 Migration 및 Flyway 검증 추가
- [x] 정상/승인 실패/민감 Prompt/Scope mismatch/Timeout/Response leakage 테스트 추가
- [ ] Production DB-backed Approval Scope Adapter
- [ ] DA Versioned Artifact/Workload Binding/Crosswalk file loader
- [ ] 실제 Provider credential과 Secret Manager 연동

## Parallel Foundation Tracking

DA Artifact 또는 Digital Asset 정책값에 의존하지 않는 공통 기반은 Phase 완료 상태와 분리해 추적한다.

### Pack Runtime Resolver

상세 설계와 확장 규칙은 [Pack Runtime Resolver](pack-runtime-resolvers.md)에서 관리한다.

- [x] `ExecutionPackType`별 Context Builder Resolver
- [x] `ExecutionPackType`별 External Schema Mapper Resolver
- [x] Pack 전용 Connector 우선 및 `COMMON` fallback Resolver
- [x] Pack 전용 Response Guard 우선 및 `COMMON` fallback Resolver
- [x] 중복 Adapter 등록 시 Application Context 시작 실패
- [x] 필수 Pack Adapter 미등록 시 Connector 호출 전 `FAILED` 처리
- [x] 기존 AI Full E2E 회귀 테스트 유지
- [ ] BE-8 Digital Asset Adapter 연결

### BE-9A Idempotency Core

상세 계약은 [BE-9A Idempotency Core](be-9a-idempotency-core.md)에서 관리한다. 이 선행 Slice는 BE-9 전체 완료와 분리한다.

- [x] Institution + Workload + Idempotency Key namespace
- [x] Canonical request SHA-256 hash 저장
- [x] 동일 Key·동일 hash의 기존 execution replay
- [x] 동일 Key·상이한 hash의 409 conflict
- [x] 진행 중 동시 요청의 재실행 차단
- [x] Replay 전 호출자 인가 재검증
- [x] V12 legacy backfill과 upgrade test
- [x] BE-9B SENT_UNKNOWN Recovery Framework

## SEC-1/2 Security Hardening Tracking

상세 계약은 [SEC-1/2 Security Hardening](sec-1-2-security-hardening.md)에서 관리한다.

- [x] Destination SSRF negative validation
- [x] Request freshness와 replay-window validation
- [x] Idempotency reservation과 독립된 denied-attempt evidence
- [x] Caller trace digest와 server-owned trace 분리
- [x] Secret Scan / SAST / Dependency Scan / Container Scan
- [x] CycloneDX SBOM generation
- [x] Fixable HIGH/CRITICAL dependency 제거와 patched runtime image digest pinning
- [x] Container Scan 실패 전 SBOM evidence 생성
- [x] 보안 검사를 별도 GitHub Actions gate로 분리

## Troubleshooting

리뷰 및 검증 과정에서 반복적으로 확인해야 했던 이슈는 [Troubleshooting Index](troubleshooting/index.md)에서 별도로 관리한다.

## Slice 27 Auth & Maker-Checker Product Closure

상세 원인과 해결 과정은 [Session Authentication and Maker-Checker Closure](troubleshooting/session-auth-maker-checker.md)에서 관리한다.

- [x] Runtime API Key stateless chain과 Browser Admin session chain 분리
- [x] BCrypt credential persistence와 로그인 실패 잠금 기반 추가
- [x] `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`, `/api/auth/csrf`
- [x] 관리자 mutation CSRF 보호
- [x] Docker 기본 User Header 인증 비활성화
- [x] FE 로그인 Gate, 로그아웃, returnTo 복귀
- [x] 로그인/로그아웃 시 Query cache 격리
- [x] Auditor Session 요청 → Logout → 별도 Checker Session 승인 → 생성/다운로드 통합 E2E
- [x] Local Header Test Harness 사용 시에도 Admin Session CSRF 보호 유지
- [x] 5회 로그인 실패 잠금과 만료 후 복구 검증

## Reference Evidence — Admin Trace Support

상세 계약은 [Reference Evidence — Admin Trace Support](policy-regulation-evidence-plane.md)에서 관리한다.

- [x] DA `adp-reference-evidence-bundle/v1` Schema/Handoff Freeze
- [x] 개별 Evidence와 Bundle canonical SHA-256 재검증
- [x] V40 최종 Admin Trace Registry와 Workload 관련성 persistence
- [x] Privileged idempotent Bundle ingestion API
- [x] Institution/Workload scoped 목록·검색·상세 API
- [x] Policy Artifact mapping과 Evidence lifecycle을 v1 범위에서 제거
- [x] Digest tampering, replay, 권한, cross-tenant, Runtime/Current Selection no-change 통합 테스트
- [ ] FE 관리자 Source/Analysis/Workload drill-down 연결

## Slice 25 Policy Operations Read Model

상세 계약은 [Slice 25 Policy Operations Read Model](slice-25-policy-operations-read-model.md)에서 관리한다.

- [x] Policy Artifact Institution/Workload scoped 목록·검색
- [x] Execution Pack·Lifecycle Stage·Attention 필터
- [x] Stable sort와 offset pagination
- [x] Current Selection 표시
- [x] Transition·Shadow Evidence History 조회
- [x] FE 목록→상세→Governance Command 연결
- [x] Review Queue Institution/Workload scoped 목록·상세와 typed next action
- [x] Security Finding Institution/Workload scoped 목록·상세와 Execution/Trace 연결
- [ ] Admin Identity·Role·Permission 조회
- [ ] Workload Registry·Data Access Decision History 조회
