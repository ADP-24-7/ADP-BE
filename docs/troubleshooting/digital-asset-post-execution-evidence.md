# Digital Asset POST_EXECUTION Evidence 트러블슈팅

## HTTP 성공과 transaction hash를 terminal success로 보면 안 된다

기존 `ExternalExecutionResult.isFinalSuccess()`는 typed 응답의 상태 조합을 검사했지만, 실제 판단 책임이 하나의 Provider
response 객체에 집중되어 있었다. Provider가 `SETTLED`와 transaction hash를 반환했다는 사실만으로 Receipt, Finality,
Token Transfer가 독립적으로 확인됐다고 간주하면 외부 응답을 그대로 신뢰하는 구조가 된다.

P0-8에서는 Transaction Detail, Receipt/Finality, Token Transfer, Internal Trace, Exact Amount를 별도 Port로 분리했다.
그러나 Port 분리만으로 Evidence source가 독립되지는 않는다. 모든 Port가 같은 Provider response를 읽으면 Provider가 만든
오류나 허위 상태를 다시 포장할 뿐이다.

Resolver에 source provenance를 추가하고 `INDEPENDENT_EXTERNAL`인 경우에만 `VERIFIED -> COMPLETED`를 허용했다. 기본
Provider-response Adapter는 provisional evidence만 만들며 항상 fail-closed한다. 로컬 Fake 환경도 Connector response와
별도 Platform State Store를 사용하고, reconciliation actual projection은 이 독립 관측값으로 재구성한다. DB constraint도
`PROVIDER_RESPONSE + VERIFIED` 조합과 mismatch가 남은 VERIFIED row를 거부한다.

## Token 전송에서 transaction value 0은 실행 금액 0이 아니다

Contract call 기반 Token 전송은 native transaction value가 0이어도 Transfer Log에서 Token amount가 확인될 수 있다.
Native Asset은 `TRANSACTION_VALUE`, Token/NFT는 `TOKEN_TRANSFER`를 exact amount source로 고정했다. Token Transfer
Evidence가 없으면 response의 `executedAmount`만으로 완료하지 않고 `REVIEW_REQUIRED`로 보낸다.

## typed SENT_UNKNOWN이 공통 Recovery를 우회했다

기존 Recovery 예약은 Connector transport status가 `SENT_UNKNOWN`일 때만 동작했다. HTTP 응답은 정상이라 Connector가
`ACKNOWLEDGED`여도 Provider payload의 `externalStatus=SENT_UNKNOWN`이면 Outcome은 미확정인데 Recovery row가 없을 수 있었다.

Outcome Handler가 typed external status도 확인해 동일 execution에 `RECONCILE_FIRST` Recovery를 예약하도록 수정했다.
`on conflict (execution_id) do nothing`으로 transport와 typed 경로가 동시에 감지되어도 Recovery job은 하나만 유지한다.

## 외부 성공 후 Local Evidence 저장 실패

Connector 호출은 DB transaction 밖에서 이미 끝날 수 있다. 이후 POST_EXECUTION Evidence나 Controlled Delivery 저장이
실패했을 때 Runtime을 단순 `FAILED`로 끝내면 동일 idempotency key 재시도 또는 운영자 재전송이 중복 외부 Action을 만들 수 있다.

Outcome finalization 예외 시 Connector가 `ACKNOWLEDGED`, `COMPLETED`, `SENT_UNKNOWN`이면 외부 결과를 미확정 사실로 보존하고
Recovery를 예약한다. Status Query/Reconciliation 전에는 재전송하지 않는다.

## 여러 Resolver Port를 한 Adapter가 구현할 때 Java 메서드 충돌

초기 구현은 모든 Port에 `resolve(ExternalExecutionResult)`를 사용하면서 반환형만 다르게 정의했다. Java는 반환형만으로
오버로드할 수 없어 하나의 Adapter가 여러 Port를 구현할 때 컴파일이 실패했다.

Port 메서드를 `resolveTransaction`, `resolveReceiptFinality`, `resolveTokenTransfer`, `resolveAmount`로 명시해 충돌을
제거하고 호출 지점에서도 Resolver 책임이 드러나도록 변경했다.

## Evidence에 원문을 저장하지 않는 이유

Recovery와 Audit에는 원문 지갑, 금액, Transfer Log가 아니라 resolver별 digest와 mismatch field enum이 필요하다.
V31에는 digest/status만 저장하고 Runtime Trace에도 동일 metadata만 노출해 Evidence 재현성과 개인정보 최소화를 함께 유지한다.
