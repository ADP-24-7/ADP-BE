# DA Analysis to BE Contract Gap Review 트러블슈팅

## 산업 분석 용어를 Runtime Enum으로 바로 추가하는 문제

DA 산업 분석에는 Stablecoin, Deposit Token, RWA, Token Security가 등장하지만 최신 변경은 Runtime Handoff가 아니다.
분석 용어를 곧바로 `DigitalAssetKind`에 추가하면 기술 형태와 법적·정책 의미가 섞이고, enum 값만 존재한 채 실제 통제 차이는
정의되지 않는다. DA의 scope limitation과 BE P0-3~8 계약을 함께 비교해 현재는 no-change로 결정했다.

## Technical Kind와 Semantic Class를 같은 축으로 보는 문제

`FUNGIBLE_TOKEN`은 contract 실행 방식이고 Stablecoin/Deposit Token/RWA는 정책상 의미가 될 수 있다. 하나의 enum으로 합치면
동일 자산이 기술 종류와 정책 종류를 동시에 표현하지 못하고 기존 canonical digest도 불필요하게 깨진다. 향후 필요 시
technical kind는 유지하고 server-owned semantic classification을 별도 versioned contract로 추가해야 한다.

## Caller가 분석 분류를 자기신고하는 문제

Caller가 `semanticAssetClass=STABLECOIN`을 보내 정책을 선택할 수 있으면 더 엄격한 통제를 우회할 수 있다. 현재 strict parser가
미승인 field를 거부하는 동작을 명시적 테스트로 고정했다. 후속 분류가 도입되더라도 검증된 DA Artifact와 server-owned
Approved Transaction을 통해 resolve하고 Runtime Snapshot에 pinning해야 한다.

## 미병합 선행 PR 위에 후속 작업을 계속 쌓는 문제

BE-11 PR은 BE-9 커밋 위에 쌓여 있어 base 정리가 필요하다. Contract Gap Review는 두 기능의 구현에 의존하지 않으므로 최신
`origin/main`에서 독립 브랜치를 생성했다. 이렇게 해야 Recovery/Observability 리뷰 경계와 이번 no-change 결정이 섞이지 않는다.
