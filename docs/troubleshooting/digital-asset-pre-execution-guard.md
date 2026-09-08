# Digital Asset PRE_EXECUTION Guard 트러블슈팅

## Connector 직전 재검증이 필요한 이유

초기 Policy Gate의 PASS와 Connector 호출 사이에는 Transform, Outbound 조립, Provider mapping이 존재한다. 이 구간에서
ACTIVE Artifact나 Destination/Policy가 교체되면 실행 시작 시 승인된 identity와 실제 전송 조건이 달라질 수 있다.

해결은 P0-6 Snapshot을 다시 만드는 것이 아니라, 기존 Snapshot과 현재 authoritative selection을 비교하는 것이다.
불일치 시 새 버전을 자동 채택하지 않고 `TRACE_BINDING`을 `BLOCKED`로 기록한다.

## KEEP digest를 원본 digest와 직접 비교하면 정상 요청이 차단된다

첫 구현은 Candidate의 `valueDigest`와 Canonical Context의 `valueDigest`를 직접 비교했다. 하지만 Transform Engine은
`KEEP`도 `path + strategy + value`로 결과 digest를 새로 계산하므로 두 digest의 계산 목적이 다르다. 이 비교 때문에
정상 Digital Asset E2E 8건이 `REQUIRED_EXACT_PRESERVATION`으로 차단됐다.

수정 후에는 다음 lineage를 검증한다.

```text
CanonicalContext.valueDigest
== TransformFieldResult.sourceValueDigest

TransformFieldResult.transformedValueDigest
== OutboundCandidateField.valueDigest

Transform strategy == KEEP
Canonical value == Candidate value
```

서로 다른 의미의 digest를 같다고 가정하지 않으면서도 원본에서 Candidate까지의 exact 보존을 증명한다.

## Provider Request를 언제 저장할 것인가

Destination-specific payload 검증에는 실제 mapper가 만든 field key와 schema/profile identity가 필요하다. 따라서 Provider
Request를 생성하고 Evidence로 저장한 뒤 Guard를 평가하되, Guard PASS 전에는 Connector를 호출하지 않는다. 생성은 전송이
아니며, BLOCK/REVIEW 결과에서도 어떤 payload digest가 거부됐는지 남길 수 있다.

## Evidence에 원문을 저장하지 않는 이유

운영 분석에는 Control 상태, reason code, snapshot ID, 두 payload digest만 필요하다. 승인 거래나 지갑 주소 등 원문을
Guard table에 복제하면 Audit surface가 불필요하게 커지므로 V30에는 privacy-safe metadata만 저장한다.
