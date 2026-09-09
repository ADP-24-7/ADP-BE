# Policy Shadow Approval Gate 트러블슈팅

## 범용 Transition API가 승인 Evidence Gate를 우회할 수 있다

기존 `/transitions` API는 Target과 Reason만 받으므로 `APPROVED` 전환에 Shadow Evidence를 결속할 수 없다. 여기에 optional
Evidence ID를 추가하면 승인 외 전환과 승인 계약이 섞이고, 값 누락을 허용하는 우회 경로가 남는다.

승인 전용 `/approvals` API를 분리하고 범용 API의 `APPROVED` Target은 `POLICY_SHADOW_APPROVAL_REQUIRED`로 차단했다.
승인 서비스는 Privileged Maker-Checker 검증 뒤 최신 Evidence와 server-owned 정책을 적용한다.

## Shadow Evidence revision과 현재 SHADOW revision은 같지 않다

Evidence는 Candidate가 `REPLAY`일 때 생성되고 `REPLAY -> SHADOW` 전환이 revision을 증가시킨다. 두 revision의 단순 동일
비교는 정상 Evidence까지 stale로 오판한다. 승인 시 `evidence.candidate_revision + 1 == current.revision`을 강제하고,
그 사이 추가 Lifecycle 변경이 있으면 fail-closed한다.

## Evidence 조회 후 ACTIVE가 바뀌는 TOCTOU

Evidence를 먼저 검증하고 바로 상태만 갱신하면 검증과 저장 사이에 ACTIVE baseline이 교체될 수 있다. 승인 저장 직전에
PR #34와 동일한 Lifecycle Scope advisory lock을 획득하고 Candidate revision/stage와 ACTIVE baseline identity/digest를 다시
검증한다. 전환 CAS와 Transition Evidence insert는 같은 트랜잭션에서 수행한다.

## V34 적용 전에 존재하던 APPROVED 이력

기존 승인 이벤트는 Shadow Evidence가 존재하지 않으므로 신규 NOT NULL/FK 계약을 곧바로 강제하면 Flyway upgrade가 실패한다.
과거 이력을 임의 Evidence에 연결하지 않고 `approval_gate_version = LEGACY_UNBOUND`로 backfill한다. V34 이후 애플리케이션이
생성하는 승인은 `SHADOW_EVIDENCE_V1`과 완전한 복합 FK binding을 요구한다. Previous-version upgrade 테스트로 이를 검증한다.

## DIFF 승인 의미를 Caller에게 맡기면 안 된다

현재 typed diff는 변경 위치를 나타낼 뿐 강화/완화 여부를 판정하지 않는다. Caller가 `acceptDiff=true` 같은 값을 제출하면
Governance 판단이 외부 입력으로 이동한다. 현재 server-owned policy `policy-shadow-approval/1.0.0`은
`GOLDEN_ALLOW/1.0.0 + MATCH`만 허용하고 정책 버전을 Transition Evidence에 저장한다. 향후 versioned 분류 정책이 추가되기
전까지 이 제한을 유지한다.
