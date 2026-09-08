# Digital Asset Artifact Loader 트러블슈팅

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
