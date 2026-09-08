# Digital Asset Artifact Loader 트러블슈팅

## Producer가 Schema까지 제출하는 신뢰 역전

초기 구현은 manifest의 `schema_reference/schema_digest`로 producer가 함께 게시한 Schema를 읽어 Artifact를
검증했다. 이 구조에서는 오류가 있는 문서와 permissive Schema를 함께 제출해도 둘의 digest만 맞으면 통과할 수 있었다.
검증 기준을 BE classpath의 role별 strict Schema로 옮겼고, manifest의 Schema 정보는 trusted reference/digest와
일치하는지 확인하는 evidence로만 사용한다.

## 파일별 Valid와 Bundle 전체 Consistent의 차이

5개 문서가 각각 Schema를 통과해도 Manifest의 destination과 Binding의 destination이 다르거나 Crosswalk에
`UNKNOWN`이 남아 있으면 하나의 Runtime Candidate로 사용할 수 없다. 별도 semantic validator에서 Binding equality,
DataClass subset, Decision/Control exact set, Pipeline stage order를 검증한 뒤에만 Lifecycle Candidate로 전이한다.

## 동시 Replay가 Conflict가 되는 Race

`find -> create`만 사용하면 같은 Artifact를 동시에 요청한 두 transaction이 모두 미존재로 판단하고 하나가 PK conflict로
끝날 수 있다. institution/artifact ID/version으로 PostgreSQL transaction advisory lock을 획득한 다음 metadata를
조회하도록 변경했다. 동일 내용은 기존 Candidate를 반환하고 다른 digest/reference만 실제 conflict로 남는다.

## Digest 검증에서 공백 변경은 tamper가 아니었던 문제

초기 negative test는 JSON 뒤에 공백을 추가해 tamper를 만들었다. 그러나 `adp-canonical-json/v1` digest는 파싱된
JSON 구조를 대상으로 하므로 표현상 공백은 의도적으로 제거된다. 테스트를 실제 field value 변경으로 바꿔 canonical
content 변경이 digest mismatch로 검출되는지 확인했다.

## Manifest self digest와 외부 expected digest 분리

Manifest 내부 digest만 확인하면 payload와 digest가 함께 바뀐 경우 신뢰 기준이 없다. Loader는 manifest의
`content_digest`를 재계산하고, 요청에 포함된 별도 `expectedContentDigest`와도 동일한지 확인한다. 이 값은 전자서명이
아니므로 운영 단계에서는 신뢰된 배포 채널 또는 Object Storage metadata와 함께 전달해야 한다.

## 파일명 검증만으로 symlink escape를 막을 수 없는 문제

문자열에서 `..`와 절대 경로만 차단해도 Store root 내부 symlink가 외부 파일을 가리킬 수 있다. Local Store는 root와
target을 `toRealPath()`로 해석한 뒤 target이 root 하위인지 다시 검사한다. 존재하지 않는 파일, directory, 크기 제한
초과도 같은 reference 오류로 닫아 내부 filesystem 정보를 노출하지 않는다.

## Lifecycle과 Ingestion metadata의 부분 저장 위험

Artifact 검증 후 Lifecycle만 `CANDIDATE`가 되고 ingestion metadata 저장이 실패하면 다음 요청에서 복구하기 어려운
중간 상태가 생긴다. Loader orchestration 전체를 하나의 Spring transaction으로 묶고 기존 Lifecycle transaction이
동일 transaction에 참여하도록 구성했다.

## DA 후보 Artifact와 BE 검증 fixture의 차이

P0-5 sample은 Loader 계약을 증명하기 위한 gap-free fixture이며 DA의 실제 분석 결과를 대체하지 않는다. DA 산출물이
manifest의 필수 role, P0-4 contract digest, institution/workload binding을 충족하고 blocking gap이 없어야 실제
ingest가 가능하다. 계약이 확정되지 않은 DA 값은 Loader를 느슨하게 만들어 통과시키지 않고 DA에서 해소한 새 version으로
게시한다.
