# Policy Current Selection / Rollback 트러블슈팅

## 범용 ACTIVE → REVIEW가 Current Selection을 stale하게 만들 수 있다

Lifecycle Validator만 보면 `ACTIVE → REVIEW`가 유효했지만 범용 `/transitions`는 Current Selection pointer와 Selection
Event를 갱신하지 않는다. 일반 OPERATOR가 선택된 ACTIVE를 REVIEW로 보내면 pointer는 그대로인데 Lifecycle만 바뀌어 신규
Runtime이 `POLICY_CURRENT_SELECTION_STALE`로 중단되고, REVIEW에서는 전용 rollback 조건도 만족하지 못한다.

`REVIEW`를 ACTIVE/SUPERSEDED/ROLLED_BACK과 같은 selection transition으로 분류해 범용 API에서 차단하고 privileged
target에도 포함했다. Review가 필요해지면 Lifecycle, Current Selection, Selection Event, Runtime propagation을 한
트랜잭션에서 처리하는 전용 governance command로 추가해야 한다. 회귀 테스트는 거부 후 Lifecycle과 Current Selection이 모두
기존 ACTIVE를 유지하는지 확인한다.

## Artifact revision만으로는 동시 활성화를 막을 수 없다

서로 다른 두 `APPROVED` Artifact는 각각 올바른 Artifact revision을 가진다. Scope lock만 사용하면 두 요청이 순차적으로 모두
성공해 마지막 선택이 호출 순서에 의존한다. 활성화와 롤백 모두 `expectedSelectionRevision`을 요구하고, lock 획득 뒤
authoritative Current Selection revision을 비교해 뒤 요청을 `POLICY_LIFECYCLE_CONCURRENT_MODIFICATION`으로 차단했다.

## Lifecycle Scope lock과 Current Selection Scope가 다르다

기존 Shadow lock은 Policy Layer를 포함하지만 Current Selection PK는 Institution, Pack, Workload, Purpose다. Layer를 lock에
포함하면 서로 다른 Layer 후보가 같은 선택 행을 동시에 변경할 수 있다. Current Selection 전용 advisory lock은 DB PK와 같은
scope로 분리하고, 내부 Lifecycle 전환 lock과 함께 같은 트랜잭션에서 유지한다.

## Rollback 대상 version을 caller 주장만으로 신뢰할 수 없다

Artifact ID/Version만 받아 `ACTIVE`로 만들면 승인되지 않았거나 다른 Scope인 버전을 복원할 수 있다. Institution/Workload SQL
scope, digest, target revision, `SUPERSEDED` 상태, 과거 `APPROVED` 및 `ACTIVE` Transition Evidence를 모두 재검증한 뒤에만
복원한다. 현재 선택도 identity/digest/artifact revision과 lifecycle `ACTIVE` 상태가 일치하지 않으면 fail-closed한다.

## 승인 후 활성화 전 Baseline이 바뀔 수 있다

Approval Gate에서 Baseline을 검증했더라도 activation까지 시간이 존재한다. 그 사이 다른 Candidate가 ACTIVE가 되면 이전
Baseline을 근거로 한 승인은 stale하다. Current Selection lock을 획득한 뒤 Candidate의 `SHADOW_EVIDENCE_V1` 승인 이벤트가
가리키는 Baseline ID/Version/Digest를 현재 ACTIVE와 비교하고, 다르면 `POLICY_CURRENT_SELECTION_APPROVAL_STALE`로
활성화를 중단한다.

## 기존 Runtime Snapshot을 Current Selection 변경으로 갱신하면 안 된다

롤백은 앞으로 시작할 실행의 선택만 바꿔야 한다. 이미 시작된 실행의 정책을 다시 조회해 덮어쓰면 같은 execution의 판단 근거가
변한다. Runtime은 시작 시 resolve한 PolicySnapshot을 계속 사용하고, source Artifact와 selection/artifact revision을
`runtime.policy_evaluation`에 immutable Evidence로 저장한다.

## V34의 ACTIVE row를 V35에서 자동 선택하지 않은 이유

과거 DB는 같은 Runtime Scope에서 여러 Layer의 `ACTIVE`가 존재할 수 있다. 최신 시각이나 문자열 정렬로 하나를 고르면 역사적
의미를 임의 변경한다. V35는 기존 ACTIVE를 보존하고 Current Selection은 비워 둔다. 이후 명시적 activation이 선택을 만들며,
기존 ACTIVE가 둘 이상이면 운영자가 정리하기 전까지 `POLICY_CURRENT_SELECTION_AMBIGUOUS`로 차단한다. V34→V35 upgrade
테스트는 중복 ACTIVE가 있어도 Migration이 성공하고 임의 selection이 생성되지 않음을 검증한다.

## 통합 테스트의 Current Selection이 다른 Runtime 테스트를 오염시켰다

초기 구현에서는 Lifecycle API 테스트가 공용 `customer_summary` Scope에 관리형 선택을 남겼다. 이후 Runtime 테스트가 로컬
fixture 대신 해당 선택을 resolve해 예상 Action이 달라졌다. 테스트가 만든 Current Selection과 Event를 `@AfterEach`에서
제거하고, 동시성/롤백 테스트는 UUID 기반 전용 Workload를 사용해 DB 상태 격리를 보장했다.
