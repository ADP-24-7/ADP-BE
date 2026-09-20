# ADP Velog 시리즈 구성안

> 상태: 원고·Evidence 보강 후 검토 단계
> 목적: 전체 저장소의 구현 범위, 시리즈 서사, 편별 근거와 이미지 계획을 함께 관리한다.
> 주의: 수치와 이미지 출처는 발행 직전에 다시 확인한다.

## 1. 결론

### 권장 시리즈 수

시리즈는 **1개**로 구성한다.

- 가제: **금융 데이터를 외부로 보내기 전에: Financial Privacy Gateway 설계와 검증**
- 권장 편수: **8편**
- 편당 분량: 제한하지 않되, 한 편에서 하나의 질문만 끝까지 해결한다.
- 저장소별 연재로 나누지 않는다. `ADP-DA → ADP-BE → ADP-FE → ADP-Infra/ADP-Docs`는 서로 다른 제품이 아니라 하나의 통제 흐름을 만드는 책임 경계이기 때문이다.
- AI와 Digital Asset도 별도 시리즈로 나누지 않는다. 두 도메인은 공통 Gateway 경계가 실제로 재사용되는지를 보여주는 두 개의 실행 Pack으로 다룬다.

시리즈를 한 개로 유지하되 내부 흐름은 다음 세 막으로 읽히게 한다.

```text
1막: 왜 필요한가, 무엇을 통제하는가       (1~2편)
2막: 어떻게 판단하고 안전하게 실행하는가  (3~6편)
3막: 어떻게 운영하고 어디까지 검증했는가  (7~8편)
```

## 2. 기존 Velog 시리즈에서 유지할 점과 바꿀 점

검토 대상:

- [금융 이벤트 정합성 시스템](https://velog.io/@dlsrnjs125/series/%EA%B8%88%EC%9C%B5-%EC%9D%B4%EB%B2%A4%ED%8A%B8-%EC%A0%95%ED%95%A9%EC%84%B1-%EC%8B%9C%EC%8A%A4%ED%85%9C): 16편
- [실시간 이상거래 탐지와 운영 검증](https://velog.io/@dlsrnjs125/series/%EC%8B%A4%EC%8B%9C%EA%B0%84-%EC%9D%B4%EC%83%81%EA%B1%B0%EB%9E%98-%ED%83%90%EC%A7%80%EC%99%80-%EC%9A%B4%EC%98%81-%EA%B2%80%EC%A6%9D): 12편

### 유지할 강점

- 기술명보다 문제와 판단을 제목 전면에 둔다.
- 첫 문단에서 장애 또는 모호한 운영 상황을 제시한다.
- “왜 더 익숙한 선택을 하지 않았는가”를 설명한다.
- HTTP 성공 여부보다 DB 결과, 처리 경계, 운영 Evidence 같은 실제 완료 조건을 강조한다.
- 한 편 안에 설계 이유, 실패 가능성, 검증 기준, 한계를 함께 둔다.
- 구현을 과장하지 않고 실제 금융망·운영 환경에서 추가로 필요한 조건을 명시한다.

### 이번 시리즈에서 바꿀 점

- 이전 시리즈처럼 모든 글을 비슷한 문제-설계-검증 템플릿으로 반복하지 않는다.
- 현재 프로젝트는 범위가 넓으므로 12~16편으로 세분화하지 않고 8편으로 압축한다.
- 글마다 대표 표현 방식을 다르게 둔다: 문제 지도, 요청 추적, 상태 머신, 비교 실험, 장애 타임라인, 운영 화면, 검증 매트릭스.
- 코드 조각만으로 설명하지 않는다. 저장소 사이의 계약과 실제 운영 UI를 함께 보여준다.
- 모든 편에 동일한 아키텍처 전체 그림을 반복하지 않는다. 전체 그림은 1편에서 제시하고, 이후에는 해당 편의 경계만 확대한다.

## 3. 전체 저장소에서 확인한 제품 구조

### 저장소별 책임

| 저장소 | 현재 책임 | 블로그에서의 역할 |
| --- | --- | --- |
| `ADP-DA` | 규제·산업 Evidence 수집, Offline 분석, AI/Transform 평가, Digital Asset 계약 및 versioned artifact 생성 | 정책과 통제 후보가 어디서 왔는지 설명하는 출발점 |
| `ADP-BE` | Java 21 / Spring Boot 기반 정책 판단, 최소 조회, 변환, 외부 실행, 감사, 복구의 Source of Truth | 시리즈의 중심 Runtime |
| `ADP-FE` | React 관리자 콘솔, 정책·분석·관제·감사·Gateway Lab | 운영자가 시스템을 어떻게 해석하고 조작하는지 보여주는 화면 |
| `ADP-Infra` | NCP QA의 VPC, subnet, ACG, Object Storage를 Terraform으로 관리 | 검증된 Cloud foundation과 아직 설계뿐인 Production 영역을 구분 |
| `ADP-Docs` | 공통 개념, 아키텍처, 보안, 운영 문서 포털 | 저장소를 가로지르는 용어와 공개 가능한 설명의 기준 |

### 핵심 제품 문장

ADP는 **AI와 Digital Asset 외부 시스템으로 금융 데이터나 실행 요청이 나가기 전에, 근거가 있는 정책으로 최소 조회·변환·목적지·실행을 통제하고 그 판단과 결과를 복구 가능한 Evidence로 남기는 Financial Privacy Gateway**다.

### 공통 실행 흐름

```text
공식 근거 / 분석 실험
  → Versioned Evidence와 Artifact
  → Policy Candidate / Shadow / Approval / Active Selection
  → 요청 인증·인가
  → 최소 데이터 조회와 Canonical Context
  → Policy Snapshot 고정과 Runtime Decision
  → Transform / Vault
  → Outbound Guard / 서버 소유 Destination
  → AI 또는 Digital Asset Connector
  → Response Guard / Controlled Delivery
  → Audit / Monitoring / Recovery / Reconciliation
```

### 현재 구현의 대표 수치

수치는 원고 작성 시점의 commit 기준으로 다시 산출한다.

- `ADP-BE`: Java main source 585개, test source 116개, Flyway migration 57개
- `ADP-BE`: 테스트 메서드 약 539개
- `ADP-DA`: AI Python 파일 49개, Digital Asset Python 파일 24개, notebook 25개
- `ADP-FE`: 11개 page 디렉터리와 20개 domain feature 디렉터리
- 로컬 통합 실행: BE, FE, DA, Docs, PostgreSQL, Mock AI Provider

이 숫자 자체를 성과로 내세우기보다, “어떤 불변조건을 어느 테스트와 Evidence로 증명했는가”를 설명할 때만 보조 자료로 사용한다.

## 4. 시리즈 편성표

## 1편. 금융 데이터는 API 호출 직전에야 위험해지는 것이 아니었다

### 핵심 질문

금융사가 AI와 Digital Asset 외부 실행을 연결할 때, 왜 단순 DLP나 API Gateway만으로는 충분하지 않은가?

### 이 편의 역할

문제 정의와 제품 전체 지도를 제공한다. 구현 세부보다 독자가 이후 7편을 읽을 좌표를 잡게 한다.

### 고유 구조: 문제 지도형

1. 외부 전송 직전에는 이미 결정돼 있어야 하는 것들
2. 규제 근거, 데이터 분류, 업무 목적, 외부 목적지가 분리되면 생기는 빈틈
3. 네 개 Plane: Governance, Runtime, Evaluation Evidence, Audit Operations
4. 왜 Modular Monolith + 독립 Python Evaluation + Web UI로 시작했는가
5. 저장소 다섯 개의 책임과 연결 지점
6. 이 시리즈가 증명할 것과 증명하지 않을 것

### 반드시 포함할 근거

- `ADP-Docs/docs/overview/why-financial-privacy-gateway.mdx`
- `ADP-Docs/docs/architecture/overview.mdx`
- `ADP-Docs/docs/architecture/control-and-data-plane.mdx`
- 각 저장소 README
- `ADP-BE/docs/production-reference-architecture.md`

### 추천 이미지

- 대표 이미지: “외부 연결 전 통제”를 표현하는 일러스트 또는 단순 도식
- 본문 1: 4-Plane 전체 아키텍처 다이어그램
- 본문 2: 5개 저장소 책임 지도
- 본문 3: 현재 검증 범위와 설계 전용 범위를 색으로 분리한 경계 표

## 2편. 규정 문장을 Runtime 정책으로 바로 실행하지 않은 이유

### 핵심 질문

공식 규정과 분석 결과를 왜 곧바로 `ALLOW/BLOCK` 코드로 바꾸지 않고 Evidence와 Policy 사이에 계약을 두었는가?

### 이 편의 역할

ADP-DA에서 ADP-BE로 넘어오는 Evidence chain과 책임 분리를 설명한다.

### 고유 구조: 추적성 계보형

1. 공식 Source 한 문장에서 시작한다.
2. Evidence → Requirement → Control → Test → Policy Candidate 계보를 따라간다.
3. `REFERENCE_ONLY`, 분석 disposition, Runtime `PolicyAction`을 구분한다.
4. JSON Schema, canonical JSON, digest로 변경과 위변조를 감지한다.
5. Artifact가 저장됐다고 활성 정책이 되지 않는 이유를 설명한다.
6. 현재 구현된 regulatory refresh lineage와 남은 법률 해석 한계를 적는다.

### 반드시 포함할 근거

- `ADP-DA/02_ai/evidence_ontology/`
- `ADP-DA/02_ai/gateway_rules/`
- `ADP-DA/02_ai/docs/REFERENCE_EVIDENCE_HANDOFF.md`
- `ADP-BE/docs/policy-regulation-evidence-plane.md`
- `ADP-BE/docs/regulatory-refresh-api.md`
- `ADP-FE/docs/regulatory-evidence-lineage.md`

### 추천 이미지

- Evidence lineage Sankey 또는 단계형 계보도
- 동일 Evidence의 ID/version/digest가 DA→BE→FE로 이어지는 샘플 카드
- FE Policy Governance의 regulatory lineage 실제 화면

## 3편. `ALLOW`보다 느슨해지지 않는 결정은 어떻게 만들어지는가

### 핵심 질문

요청마다 달라지는 사용자, 기관, 업무, 목적, 데이터 종류를 어떻게 하나의 재현 가능한 결정으로 묶는가?

### 이 편의 역할

Gateway의 핵심인 Policy Snapshot과 Runtime Decision을 한 요청의 추적으로 설명한다.

### 고유 구조: 한 요청 추적형

1. 합성 요청 하나를 고정한다.
2. AuthN/AuthZ 이후에만 idempotency reservation과 데이터 조회가 시작되는 순서를 따라간다.
3. 자유 SQL 대신 Retrieval Profile과 field allowlist를 사용하는 이유를 설명한다.
4. 원문 대신 Canonical Context와 digest를 만드는 과정을 보여준다.
5. `policy_action`과 `final_action`을 분리한다.
6. monotonic decision table로 정책이 Runtime에서 완화되지 않음을 보여준다.
7. 실행 중 정책이 바뀌어도 pinned snapshot이 유지되는 이유로 끝낸다.

### 반드시 포함할 근거

- `ADP-BE/docs/be-4-policy-decision-core.md`
- `ADP-BE/docs/sec-0-security-contract.md`
- `ADP-BE/src/main/java/com/adp/gateway/runtime/application/RuntimeExecutionService.java`
- Authorization, Retrieval, Context, Decision 관련 테스트

### 추천 이미지

- 한 요청의 sequence diagram
- `PolicyAction × FinalAction` 단조성 표
- Runtime trace stage를 실제 FE 화면과 나란히 놓은 이미지

## 4편. 개인정보를 가리는 것보다 중요한 것은 원문이 경계를 넘지 않게 하는 일이었다

### 핵심 질문

MASK, HMAC, Vault Token 같은 기법을 어떻게 업무 목적과 외부 Provider 계약에 맞게 선택하고, 변환 전 원문 유출을 막는가?

### 이 편의 역할

Transform과 Egress를 “알고리즘 소개”가 아니라 데이터 경계 설계로 설명한다.

### 고유 구조: 위협 모델 + 방어선 해부형

1. 원문이 샐 수 있는 위치를 공격 경로로 그린다.
2. 최소 조회와 Canonical Context가 첫 번째 방어선임을 보인다.
3. `MASK`, `HMAC_PSEUDO`, `VAULT_TOKEN`, `REMOVE`, `KEEP`, `GENERALIZE`, `FIELD_SEPARATION`의 사용 목적을 비교한다.
4. Transform 결과와 원문을 어떻게 digest로 추적하는지 설명한다.
5. caller가 URL을 고르지 못하고 서버 소유 Destination Profile만 쓰게 한 이유를 다룬다.
6. Outbound Guard와 Response Guard가 각각 무엇을 막는지 구분한다.
7. FE의 Blur가 보안 기능이 아닌 이유로 브라우저 경계를 연결한다.

### 반드시 포함할 근거

- `ADP-BE/docs/be-5-transform-vault.md`
- `ADP-BE/docs/be-6-common-egress-boundary.md`
- `ADP-BE/docs/sec-0-security-contract.md`
- `ADP-DA/02_ai/docs/EXPERIMENT_03_WORKLOAD_TRANSFORM_UTILITY_VALIDATION.md`
- `ADP-FE/docs/ui-state-policy.md`

### 추천 이미지

- 원문→Canonical Context→Transform→Outbound 후보의 필드 변화 표
- 공격 경로와 Guard 위치를 겹친 데이터 흐름도
- Transform 방식별 Privacy/Utility/운영 복잡도 비교 차트
- DA-04 목적지별 externalizable superset과 payload Field 비교 차트

## 5편. 같은 Gateway로 AI와 디지털 자산을 통제할 수 있을까

### 핵심 질문

성격이 전혀 다른 AI 호출과 Digital Asset 외부 실행에서 무엇을 공통화하고 무엇을 Pack별로 분리해야 하는가?

### 이 편의 역할

공통 Runtime의 재사용성과 Pack별 안전 조건을 비교해 프로젝트 범위를 구체화한다.

### 고유 구조: 좌우 비교형

| 공통 Gateway 경계 | AI Pack | Digital Asset Pack |
| --- | --- | --- |
| Snapshot | Model/Evaluation 조건 고정 | Artifact/Policy/Destination/Control/Crosswalk 고정 |
| Pre-execution | Provider allowlist, transform scope | 승인 거래, 금액, 자산, 네트워크, 목적지 재검증 |
| External result | latency/token/failure/response finding | transaction/receipt/finality/transfer evidence |
| Completion | response guard와 controlled delivery | 독립 Evidence와 re-binding 완료 |
| Failure | timeout/HTTP error 분류 | `SENT_UNKNOWN`, mismatch, reconciliation |

본문은 이 비교표를 확장하는 방식으로 구성한다.

### 반드시 포함할 근거

- `ADP-BE/docs/be-7-ai-full-e2e.md`
- `ADP-BE/docs/ai-eval-0-nvidia-model-profiles.md`부터 `ai-eval-3-da-evaluation-bundle.md`
- `ADP-BE/docs/da-p0-2-approved-transaction-boundary.md`부터 `da-p0-8-post-execution-evidence.md`
- `ADP-BE/docs/pack-runtime-resolvers.md`
- `ADP-DA/02_ai/docs/EXPERIMENT_02_REGULATORY_RUNTIME_VALIDATION.md`
- `ADP-DA/03_digital_asset/docs/handoff/DA_P0_LOCAL_PRODUCT_E2E.md`

### 추천 이미지

- 공통 spine과 두 Pack branch를 보여주는 아키텍처
- AI 3-model evaluation 결과 차트는 검증된 실제 수치가 있을 때만 사용
- Digital Asset 6-case 결과 매트릭스
- AI Operations Overview와 Digital Asset Operations Overview 실제 화면을 같은 비율로 배치

## 6편. 외부 전송 결과를 모를 때 재시도부터 하지 않은 이유

### 핵심 질문

요청이 Provider에 도달했는지 알 수 없는 `SENT_UNKNOWN` 상태에서 어떻게 중복 외부 효과를 막고 최종 상태로 수렴하는가?

### 이 편의 역할

정합성, idempotency, recovery를 하나의 장애 시나리오로 보여주는 시리즈의 기술적 절정으로 둔다.

### 고유 구조: 장애 타임라인형

```text
T0 요청 수신과 idempotency reservation
T1 Outbound 전 검증 통과
T2 Provider 전송
T3 응답 유실 → SENT_UNKNOWN
T4 Recovery queue claim/lease
T5 status query와 독립 Evidence 조회
T6 VERIFIED일 때만 Runtime/Connector/Recovery 원자적 수렴
T7 기존 요청 replay, additional external effect 0 확인
```

각 시점마다 “알고 있는 사실 / 아직 모르는 사실 / 허용되는 다음 행동”을 표로 정리한다.

### 반드시 포함할 근거

- `ADP-BE/docs/be-9a-idempotency-core.md`
- `ADP-BE/docs/be-9b-external-interaction-recovery.md`
- `ADP-BE/docs/be-9-recovery-operations.md`
- `ADP-BE/docs/digital-asset-local-product-e2e.md`
- `ExternalInteractionRecoveryServiceTests`
- `DigitalAssetLocalProductE2ETests`

### 추천 이미지

- `SENT_UNKNOWN` 장애·복구 timeline
- `NEW / REPLAY / CONFLICT / IN_PROGRESS` idempotency resolution 표
- `assets/screenshots/FPG_08_Recovery_Incidents.jpg`
- DA-06 counterfactual 복구 전략 비교 차트(실제 장애 발생률로 오해하지 않게 한계 병기)

## 7편. 정책 배포를 코드 배포와 분리하자 상태 머신이 필요해졌다

### 핵심 질문

분석 Artifact를 안전하게 활성화하고 문제가 생기면 어떻게 코드나 DB를 되돌리지 않고 정책만 롤백하는가?

### 이 편의 역할

Governance 운영 모델과 사람의 승인 책임을 설명한다.

### 고유 구조: 상태 머신 + 운영자 의사결정형

1. `DRAFT → VALIDATED → CANDIDATE → REPLAY → SHADOW → APPROVED → ACTIVE` 상태 머신
2. Candidate와 ACTIVE를 같은 case/input digest로 비교하는 shadow evaluation
3. typed diff와 raw-free Evidence
4. Maker-Checker와 왜 생성자가 승인할 수 없는가
5. optimistic revision이 막는 동시 활성화 문제
6. Current Selection과 append-only transition event
7. Policy rollback과 application rollback이 다른 이유

### 반드시 포함할 근거

- `ADP-BE/docs/be-10-policy-lifecycle-skeleton.md`
- `ADP-BE/docs/be-10-policy-shadow-evidence.md`
- `ADP-BE/docs/be-10-policy-shadow-approval-gate.md`
- `ADP-BE/docs/be-10-policy-current-selection.md`
- `ADP-FE/docs/local-api-verification.md`
- Policy lifecycle 관련 E2E/concurrency test

### 추천 이미지

- 상태 전이도
- Maker/Checker swimlane
- ACTIVE 교체와 rollback 시 selection revision 변화 그림
- FE 정책 현황의 Current Selection과 Runtime Evidence panel

## 8편. 테스트 개수보다 중요한 것은 어디까지 검증했다고 말할 수 있는가였다

### 핵심 질문

로컬 E2E, 운영 Evidence, NCP QA foundation, Production 설계 사이의 경계를 어떻게 정직하게 설명할 것인가?

### 이 편의 역할

프로젝트 회고이자 기술 검증 보고서다. 기능 나열이 아니라 claim boundary로 끝낸다.

### 고유 구조: 검증 매트릭스 + 회고형

1. Repository Lock과 profile로 다중 저장소 실행을 재현한 방법
2. unit/integration/E2E/negative test/architecture validation의 역할 구분
3. Prometheus, Audit, Review Queue, Security Finding, Evidence Export가 보여주는 운영 가능성
4. 실제로 검증된 것: local product E2E, AI evaluation handoff, Digital Asset 6-case, NCP Object Storage ingest 등
5. 제한적으로 검증된 것: NCP VPC/Subnet/ACG/Object Storage foundation
6. 아직 미검증인 것: Production Runtime 배포, HA PostgreSQL/failover, backup restore/DR, private monitoring/SIEM 등
7. 규모가 커질 때 분리할 후보와 현재 Modular Monolith를 유지할 이유
8. 다음 단계 우선순위

### 반드시 포함할 근거

- `ADP-BE/docs/implementation-progress.md`
- `ADP-BE/docs/slice-30-local-integration-reproducibility.md`
- `ADP-BE/docs/production-reference-architecture.md`
- `ADP-Infra/docs/production-reference-architecture.md`
- `ADP-FE/docs/local-api-verification.md`
- 각 저장소의 `Makefile`과 CI workflow

### 추천 이미지

- 검증 수준별 matrix: 구현 / 자동 테스트 / 로컬 E2E / Cloud QA / Production 미검증
- 전체 로컬 Docker stack 구성도
- Monitoring Overview 실제 화면
- 마지막 이미지로 “현재 경계와 다음 단계” roadmap

## 5. 편별 구조가 반복되지 않도록 하는 규칙

| 편 | 중심 서술 방식 | 대표 시각 자료 | 피해야 할 반복 |
| --- | --- | --- | --- |
| 1 | 문제 지도 | 전체 아키텍처 | 세부 코드 |
| 2 | Evidence 계보 추적 | lineage diagram | Runtime 전체 흐름 재설명 |
| 3 | 요청 한 건 추적 | sequence + decision table | 규제 수집 과정 반복 |
| 4 | 위협 모델 해부 | data boundary | 전략 enum 나열만 하기 |
| 5 | AI/자산 비교 | comparison matrix | 두 도메인을 별개 제품처럼 설명 |
| 6 | 장애 타임라인 | state timeline | 일반적인 retry 이론 장문 설명 |
| 7 | 운영자 상태 머신 | lifecycle + swimlane | Runtime request flow 반복 |
| 8 | 검증 보고와 회고 | claim matrix | 기능 목록을 다시 전부 나열 |

각 편은 아래 공통 요소만 유지한다.

- 첫 3~5문장 안에 해결하려는 문제를 명확히 쓴다.
- 구현 선택을 설명할 때 대안과 포기한 이점을 함께 적는다.
- 저장소의 실제 타입, API, 상태, 테스트 이름을 근거로 쓴다.
- 글 마지막에 `검증한 것 / 아직 검증하지 않은 것`을 짧게 분리한다.
- 다음 편 예고는 현재 편의 결론에서 자연스럽게 생기는 질문 한 문장으로만 둔다.

## 6. 이미지 전략

### 이미지 종류

1. **구조 다이어그램**: 저장소 관계, 4-Plane, Runtime sequence, lifecycle, recovery timeline
2. **실제 제품 화면**: Gateway Lab, Policy Current Selection, AI/Digital Asset Overview, Recovery, Monitoring
3. **검증 결과**: 6-case matrix, evaluation comparison, claim boundary matrix
4. **코드/계약 확대**: 짧은 record/schema/enum 조각만 사용하고 IDE 전체 화면은 피한다.

### 제작 우선순위

| ID | 이미지 | 사용 편 | 방식 |
| --- | --- | --- | --- |
| IMG-01 | 4-Plane + 5-repository map | 1 | Pillow 기반 PNG |
| IMG-02 | Evidence lineage | 2 | Pillow 기반 PNG |
| IMG-03 | 한 요청 Runtime sequence | 3 | Pillow 기반 PNG |
| IMG-04 | Policy/Final action monotonic table | 3 | 문서 표 또는 SVG |
| IMG-05 | Transform/Egress data boundary | 4 | Pillow 기반 PNG |
| IMG-06 | Transform 관계 보존 + 목적지별 Field 감소 | 4 | Notebook embedded chart |
| IMG-07 | Common Gateway + AI/DA Pack split | 5 | Pillow 기반 PNG |
| IMG-08 | AI 품질-지연시간 + Digital Asset 6-case | 5 | Versioned artifact 기반 PNG |
| IMG-09 | `SENT_UNKNOWN` recovery timeline | 6 | Pillow 기반 PNG |
| IMG-12 | Policy lifecycle state machine | 7 | Pillow 기반 PNG |
| IMG-13 | Recovery 전략별 중복 효과 위험 | 6 | Notebook embedded chart |
| IMG-14 | CI + Security Gate pipeline | 8 | workflow 기반 Pillow PNG |
| IMG-15 | Verification claim matrix | 8 | Pillow 기반 PNG |
| IMG-16 | AI Pack Overview 통제 흐름 | 1 | 실제 로컬 관리자 UI crop |
| IMG-17 | Reference Evidence 계보 상세 | 2 | 실제 로컬 관리자 UI crop |
| IMG-19 | Gateway Lab 필드 처리 | 4 | 실제 로컬 관리자 UI crop |
| IMG-20 | Digital Asset Overview 6단계 흐름 | 5 | 실제 로컬 관리자 UI crop |
| IMG-21 | Recovery incident 현황 | 6 | 실제 로컬 관리자 UI crop |
| IMG-22 | Policy Current Selection | 7 | 실제 로컬 관리자 UI crop |
| IMG-24 | Security Monitoring 탐지 현황 | 8 | 실제 로컬 관리자 UI crop |
| IMG-25 | Amount FLOAT64 정밀도 손실 | 5 | DA-02 Notebook embedded chart |
| IMG-26 | ZERO_VALUE 실제 가치이동 | 5 | DA-03 Notebook embedded chart |

실제 관리자 화면은 여덟 장만 사용한다. 모두 local synthetic fixture와 실제 BE API를 사용하는 전용 Chrome 세션에서 다시 확보했고, 포인터·브라우저 chrome·잘린 카드가 없도록 주장에 필요한 영역만 crop했다. 실행 결과를 보여주지 못하는 입력 대기 화면과 현재 편의 주장과 직접 연결되지 않는 승인 화면은 제외했다. 재현 가능한 상태를 고정하지 못한 임시 화면이나 mock fallback 화면은 발행 이미지로 사용하지 않는다.

### 캡처 원칙

- 합성 데이터 또는 privacy-safe digest만 보이게 한다.
- API key, access key, secret, 실제 endpoint, 개인 식별값은 포함하지 않는다.
- UUID와 digest는 글의 논지에 필요한 경우 앞뒤 일부만 보이거나 명시적으로 합성 값임을 표시한다.
- 같은 UI 전체 화면을 여러 편에서 반복하지 않고 논점이 있는 영역만 crop한다.
- 코드 캡처보다 복사 가능한 code block을 우선한다.
- 차트는 원본 데이터와 생성 스크립트 경로를 함께 관리한다.

## 7. 사실성 및 보안 검수 규칙

### 시스템 검증 등급

| 등급 | 의미 | 문장 표현 |
| --- | --- | --- |
| `IMPLEMENTED` | main 코드와 테스트가 존재 | “구현했다” |
| `LOCAL_E2E` | 고정된 로컬 통합 환경에서 E2E 확인 | “로컬 통합 환경에서 검증했다” |
| `QA_LIMITED` | 특정 NCP QA 자원 또는 경로만 검증 | “NCP QA의 해당 범위에서 제한적으로 검증했다” |
| `DESIGN_ONLY` | 문서·계약만 존재 | “목표 구조로 설계했다” |
| `UNVERIFIED` | 운영 환경 검증 없음 | “아직 검증하지 않았다” |

### 분석 Evidence provenance

| Provenance | 의미 | 사용할 수 있는 표현 |
| --- | --- | --- |
| `SAVED_OUTPUT_HASHED` | 저장된 Notebook output을 source SHA와 cell로 고정해 추출 | “저장된 실행 output에서 확인했다” |
| `VERSIONED_ARTIFACT_REGENERATED` | 고정 commit과 SHA를 통과한 JSON/fixture로 현재 차트를 재생성 | “버전이 고정된 Artifact에서 재생성했다” |
| `LOCAL_E2E` | BE·FE·DB 통합 실행에서 직접 확인 | “로컬 통합 환경에서 확인했다” |
| `NCP_QA` | NCP QA의 제한된 자원 또는 경로에서 확인 | “NCP QA의 해당 범위에서 확인했다” |

시스템 기능의 검증 강도와 분석 수치의 출처는 별도 축으로 기록한다. 저장된 Notebook output에 SHA가 있다고 해서 시스템 기능을 로컬 E2E로 실행한 것은 아니다.

### 금지 표현

- Production 배포가 없는데 “운영 중”, “운영 검증 완료”라고 쓰지 않는다.
- Local fixture 결과를 실제 금융사 데이터 결과처럼 표현하지 않는다.
- Reference Evidence를 법률 판단 또는 Runtime 허용 근거로 단정하지 않는다.
- AI 모델 비교에서 실행되지 않은 모델이나 `NOT_EVALUABLE` 결과를 성능 순위에 포함하지 않는다.
- Digital Asset local mock connector를 실자산 전송으로 표현하지 않는다.
- NCP Object Storage 검증을 전체 Production architecture 검증으로 확대하지 않는다.

### 편별 발행 전 확인

1. 기준 commit SHA를 기록한다.
2. 인용할 문서, 코드, migration, test가 현재 main에 존재하는지 확인한다.
3. 표의 수치와 상태명을 자동 또는 명령으로 재산출한다.
4. 실제 화면을 새로 캡처하고 합성 데이터 provenance를 확인한다.
5. 시스템 검증 등급과 분석 Evidence provenance를 구분한다.
6. 공개하면 안 되는 credential, 내부 endpoint, 원문 데이터가 없는지 확인한다.

## 8. `docs/blog` 권장 구조

본문 작성 단계에서 아래 구조로 확장한다.

```text
docs/blog/
├── series-plan.md                  # 현재 문서: 시리즈 구조와 검수 기준
├── README.md                       # 작성 순서, 기준 commit, 진행 현황
├── sources/
│   ├── repository-evidence-map.md  # 편별 코드·문서·테스트 근거
│   └── claim-register.md           # 공개 문장과 Claim 등급
├── posts/
│   ├── 01-problem-and-system-map.md
│   ├── 02-evidence-to-policy.md
│   ├── 03-runtime-decision.md
│   ├── 04-transform-and-egress.md
│   ├── 05-ai-and-digital-asset.md
│   ├── 06-sent-unknown-recovery.md
│   ├── 07-policy-lifecycle.md
│   └── 08-verification-boundary.md
└── assets/
    ├── diagrams/                   # Mermaid 원본과 export
    ├── charts/                     # 데이터, 생성 스크립트, export
    └── screenshots/                # privacy-safe 실제 화면
```

현재 `posts/` 8편 원고와 시각 자료가 생성되어 있으며 검토·발행 전 보강 단계다.

## 9. 권장 작성 순서

발행 순서와 작성 순서를 다르게 둔다.

1. **6편**: 기존 6-case E2E와 캡처가 있어 가장 구체적인 기술 서사를 먼저 고정한다.
2. **3편**: 공통 Runtime 흐름과 용어를 확정한다.
3. **2편**: DA→BE Evidence/Policy 계약을 정확히 연결한다.
4. **7편**: Policy lifecycle과 운영 책임을 연결한다.
5. **5편**: 공통 경계가 고정된 뒤 AI/DA 비교를 쓴다.
6. **4편**: Transform 실험 결과와 Egress 위협 모델을 결합한다.
7. **8편**: 모든 편의 검증 범위를 집계한다.
8. **1편**: 전체 시리즈가 완성된 뒤 실제 내용을 반영해 입문 글을 마지막에 쓴다.

발행은 1편부터 8편 순서로 진행한다.

## 10. 발행 전 최종 작업

1. AI·Digital Asset 2축과 어긋나는 과거 범위 표현이 없는지 확인한다.
2. 시스템 검증 등급과 분석 Evidence provenance가 섞이지 않았는지 확인한다.
3. Notebook, benchmark JSON, validation JSON, fixture의 source SHA와 ADP-DA commit pin을 검증한다.
4. 이미지의 글자 잘림, 포인터, 브라우저 chrome, 실제 식별정보 노출 여부를 확인한다.
5. Markdown 상대 경로를 Velog 업로드 URL로 변환하고 본문과 이미지 순서를 최종 검수한다.
