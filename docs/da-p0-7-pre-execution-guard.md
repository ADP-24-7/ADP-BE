# DA-P0-7 PRE_EXECUTION Guard

## 목적

Digital Asset 외부 호출 직전에 승인 거래, 요청, 변환 결과, 목적지 payload와 실행 Snapshot의 연결을 다시 검증한다.
초기 Context/Policy 검증을 통과했더라도 Connector 직전 상태가 달라졌거나 exact field가 변형되면 fail-closed한다.

## 실행 순서

```text
Approved Transaction + Outbound Request
-> Canonical Context / Policy / Transform
-> Common Outbound Guard
-> Provider Request 생성(미전송)
-> Destination / Policy / ACTIVE Artifact / Approved Transaction 재조회
-> DA-P0-7 6 Controls 평가 및 Evidence 저장
-> PASSED인 경우에만 Connector 호출
```

`BLOCKED` 또는 `REVIEW_REQUIRED`에서는 `runtime.connector_execution`을 만들지 않는다.

## 6 Runtime Controls

| Control | 검증 내용 | 실패 상태 |
| --- | --- | --- |
| `APPROVED_VS_REQUESTED_MATCH` | 승인 자산, 금액/한도, 목적지, 수익자, 기간과 승인 digest | `BLOCKED` |
| `REQUIRED_OUTBOUND_FIELD_PRESENCE` | Destination Profile의 required field 존재 | `BLOCKED` |
| `REQUIRED_EXACT_PRESERVATION` | Context source digest, Transform lineage, Candidate value의 exact 보존 | `BLOCKED` |
| `TRANSFORM_FIELD_SEPARATION` | exact field는 `KEEP`, pseudonymizable field는 Transform set으로 분리 | `BLOCKED` |
| `DESTINATION_SPECIFIC_PAYLOAD` | 등록된 provider field/schema/profile만 사용 | `REVIEW_REQUIRED` |
| `TRACE_BINDING` | execution, snapshot, policy, destination, outbound, provider request identity 연결 | `BLOCKED` |

`regulatoryOutboundData`는 source/allowlist/provider mapping이 고정되기 전까지 empty-only다.

## Evidence

V30의 `runtime.digital_asset_pre_execution_guard`는 execution당 한 건의 결과를 저장한다.

- 6개 Control별 상태
- privacy-safe reason code 목록
- pinned snapshot ID
- outbound candidate digest와 provider payload digest
- 평가 시각

원문 ApprovedTransaction, OutboundRequest, Provider payload는 저장하지 않는다.

## TOCTOU 방어

P0-6에서 pin한 값과 Connector 직전에 다시 조회한 현재 값을 비교한다.

- Institution x Workload ACTIVE Artifact 전체 identity
- Policy version/digest
- Destination Profile ID/version/digest
- ApprovedTransaction digest와 canonical binding

ACTIVE Artifact 교체 또는 Policy/Destination 변경이 탐지되면 Connector를 호출하지 않는다. 이미 시작된 execution을
새 버전으로 암묵적으로 승격하지 않는다.

## 검증

- 6개 Control PASS와 Evidence 저장 단위 테스트
- 승인 조건 변경, required 누락, exact 변형, transform set 혼입, destination mapping 누락 테스트
- ACTIVE Artifact 교체 TOCTOU 테스트
- 정상 Digital Asset E2E에서 6개 PASS 및 Connector 실행 검증
- V30 table/column migration 검증
