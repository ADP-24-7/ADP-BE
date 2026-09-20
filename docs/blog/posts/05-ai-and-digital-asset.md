---
title: "5편. 같은 Gateway로 AI와 디지털 자산을 통제할 수 있을까"
series: "금융 데이터를 외부로 보내기 전에"
status: review
---

![5편 커버](../assets/covers/05-post-cover.png)

AI Provider 호출과 Digital Asset 외부 실행은 얼핏 전혀 다른 문제처럼 보인다. AI는 prompt와 model response를 다루고, Digital Asset은 승인 거래와 transaction finality를 다룬다. 둘을 하나의 추상화로 억지로 묶으면 중요한 도메인 의미가 사라질 수 있다.

반대로 인증, 최소 조회, 정책 선택, 개인정보 변환, 목적지 통제, 감사, 재시도 같은 공통 기능을 각각 구현하면 같은 보안 경계를 두 번 만들게 된다.

그래서 공통 Runtime을 하나의 spine으로 두고, 외부 실행의 입력·완료 조건·Evidence만 Pack별로 분리했다.

![공통 Gateway와 AI/Digital Asset Pack](../assets/diagrams/05-ai-digital-asset-packs.png)

## 공통화한 것은 기술이 아니라 통제 순서다

두 Pack은 모두 다음 순서를 공유한다.

```text
Authentication / Authorization
→ Minimum Retrieval
→ Canonical Context
→ Policy Snapshot / Runtime Decision
→ Transform / Vault
→ Destination Profile / Outbound Guard
→ Pack Connector
→ Response or Post-execution Guard
→ Audit / Recovery
```

공통 순서를 유지하면 새 Pack을 추가할 때 인증이나 egress 통제를 우회하는 별도 진입점이 생기는 것을 막을 수 있다. Runtime API도 `POST /v1/runtime/executions` 하나를 유지하고, 내부 resolver가 workload와 processing context를 기준으로 Pack별 port를 선택한다.

미등록 Pack이나 adapter는 일반 connector로 fallback하지 않고 fail closed한다.

## AI Pack은 실행 조건을 평가 단위로 고정한다

AI 모델 비교에서 prompt만 같다고 같은 실험이라 할 수 없다. Model profile, destination, timeout, transform scope, dataset version, evaluation case가 함께 고정되어야 한다.

AI Evaluation Run은 다음 관계를 명시한다.

- evaluation run ID와 version
- case ID와 dataset version
- 서버가 허용한 model profile
- destination profile
- policy/snapshot identity
- fixed conditions digest

Runtime request가 model ID나 임의 endpoint를 직접 전달하지 않는 이유다. 클라이언트가 모델을 바꿀 수 있으면 평가 조건과 실제 실행이 달라진다.

현재 로컬 baseline에서는 세 개의 서버 소유 NVIDIA 호환 model profile을 같은 Runtime 경로로 실행한다. Provider 호출 결과는 단순 성공/실패가 아니라 다음 Evidence로 저장한다.

- HTTP 상태와 정규화된 connector status
- end-to-end latency와 provider latency
- token usage와 수집 상태
- failure category
- response guard finding
- runtime execution, case, model의 binding

DA는 Provider를 다시 호출하지 않고 BE가 export한 evaluation bundle을 검증하고 비교할 수 있다.

## AI에서 완료는 응답 수신과 같지 않다

Provider가 200을 반환해도 응답에 원문 개인정보가 반사되거나, 기대한 schema를 만족하지 않거나, evaluation case binding이 다르면 Controlled Delivery를 허용할 수 없다.

따라서 AI Pack은 다음 상태를 분리한다.

```text
Provider transport result
≠ Response Guard result
≠ Controlled Delivery result
≠ Evaluation completeness
```

모델별 실행 수가 존재해도 case×model matrix가 완전하지 않으면 bundle readiness는 `READY`가 아니다. 결과가 있는 것과 비교 가능한 결과가 있는 것은 다르다.

## Digital Asset Pack은 실행 전후의 동일성을 증명한다

Digital Asset 실행은 “응답을 잘 받았는가”보다 “승인한 거래와 실제 외부 효과가 같은가”가 중요하다.

실행 전에는 server-owned Approved Transaction과 요청을 비교한다.

- customer와 subject binding
- asset
- network
- amount와 limit
- destination / counterparty
- approval validity

Policy Gate에서 금액이나 목적지가 다르면 connector 이전에 차단한다. 이 경우 provider request, connector row, post-execution evidence가 없어야 한다. 차단됐는데 외부 효과가 한 건이라도 생겼다면 실패다.

## PRE_EXECUTION Guard를 connector 바로 앞에 다시 둔 이유

Policy Decision을 통과한 뒤에도 payload mapping 과정에서 값이 바뀔 수 있다. Digital Asset Pack은 connector 직전에 여섯 개 Runtime Control을 다시 평가하고 requested tuple과 approved tuple을 digest로 묶는다.

이 검사는 도메인 rule을 중복 구현하려는 것이 아니다. decision 시점과 실제 external call 시점 사이의 변조를 막는 TOCTOU 방어다.

## transaction hash가 있어도 성공은 아니다

외부 시스템이 transaction hash를 반환했다고 해서 settlement가 완료됐다고 단정할 수 없다. Post-execution 단계는 서로 독립적인 resolver를 통해 다음 Evidence를 수집한다.

- transaction identity
- receipt status
- finality status
- transfer evidence
- approved/requested/executed tuple의 re-binding

receipt가 실패했거나 finality가 미확정이면 `COMPLETED`로 전환하지 않는다. amount, asset, network, destination이 다르면 mismatch로 격리하고 Controlled Delivery를 보류한다.

## 같은 상태 이름도 Pack마다 완료 조건이 다르다

| 경계 | AI Pack | Digital Asset Pack |
| --- | --- | --- |
| 실행 조건 | Model/Evaluation contract | Approved Transaction/Artifact snapshot |
| 외부 입력 | privacy-safe prompt/payload | canonical outbound transaction |
| 실행 전 통제 | provider·transform binding | 6개 Runtime Control |
| 실행 결과 | response, token, latency, finding | transaction, receipt, finality, transfer |
| 완료 조건 | response guard와 delivery | independent evidence와 re-binding |
| 불확실 상태 | timeout/HTTP ambiguity | `SENT_UNKNOWN`과 reconciliation |

공통 Runtime status가 같더라도 Pack outcome handler는 서로 다른 Evidence를 요구한다. 이 차이를 숨기지 않는 것이 추상화의 핵심이었다.

## 여섯 개의 Digital Asset 고정 Case

현재 로컬 제품 E2E는 DA 저장소의 versioned fixture 여섯 개를 실제 Runtime API에 통과시킨다.

| Case | 기대 결과 | 외부 효과 |
| --- | --- | --- |
| `GOLDEN_PASS` | 독립 Evidence 검증 후 `COMPLETED` | 1 |
| `BLOCK_AMOUNT` | 승인 금액 초과로 `BLOCKED` | 0 |
| `BLOCK_DESTINATION` | 목적지 불일치로 `BLOCKED` | 0 |
| `EXECUTION_FAILED` | 실패 receipt로 `FAILED` | 1 |
| `SENT_UNKNOWN_RECOVERED` | 상태 조회 후 `EXTERNALLY_RECONCILED` | 1 |
| `DUPLICATE_REQUEST` | 기존 execution replay | 1 |

각 case는 API status만 보지 않는다. PostgreSQL의 execution, provider request, connector evidence, recovery, audit lineage와 external effect count를 함께 검증한다.

공통 Gateway를 재사용한다는 것은 두 도메인을 같은 것으로 취급한다는 뜻이 아니다. 공통화할 것은 신뢰 경계와 운영 불변조건이고, 분리할 것은 완료의 의미다.

다음 글에서는 그 차이가 가장 크게 드러나는 `SENT_UNKNOWN`을 다룬다. 외부 전송 결과를 모를 때 왜 실패 처리나 즉시 재시도 모두 위험한지 장애 타임라인으로 살펴본다.

## 확인한 범위

- 공통 Runtime API와 Pack resolver
- AI Evaluation Run/Bundle의 case×model 완전성
- Digital Asset의 pre/post execution Evidence
- 고정된 합성 fixture 6-case 로컬 E2E

## 아직 검증하지 않은 범위

- 상용 AI Provider 전체 조합의 지속적 benchmark
- 실자산·실제 Wallet·Private Key를 사용하는 거래
- 실제 Blockchain finality provider와 custody 연동
