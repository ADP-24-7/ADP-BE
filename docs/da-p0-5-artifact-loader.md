# DA-P0-5 Artifact Loader v1

## 목적

DA가 생성한 Digital Asset Artifact Bundle을 BE가 신뢰 경계 안으로 가져오기 전에 구조, 무결성, 참조,
P0-4 Canonical Contract 결속, 기관·워크로드 범위를 검증한다. 검증에 성공한 Bundle만 기존 BE-10 Policy
Lifecycle의 `CANDIDATE`로 등록한다.

## 처리 흐름

```text
인가 확인
-> Manifest 로드 및 Schema 검증
-> Manifest expected/self digest 검증
-> P0-4 Canonical Contract version/digest 검증
-> 필수 5개 Artifact와 Schema reference 로드
-> Artifact/Schema digest 및 JSON Schema 검증
-> Blocking gap 탐지
-> Institution/Workload binding 검증
-> DRAFT -> VALIDATED -> CANDIDATE
-> Ingestion metadata 저장
```

필수 Artifact role은 `OUTBOUND_REQUIREMENT_MATRIX`, `POLICY_EVALUATION`, `BINDING`,
`RUNTIME_DATA_CROSSWALK`, `RUNTIME_PIPELINE`이다. role 또는 reference 중복과 누락은 거부한다.

## API와 설정

| 항목 | 값 |
| --- | --- |
| Ingest | `POST /api/admin/digital-assets/artifacts/ingestions` |
| 조회 | `GET /api/admin/digital-assets/artifacts/{artifactId}/versions/{artifactVersion}` |
| 쓰기 권한 | `OPERATOR` |
| 조회 권한 | `OPERATOR`, `PRIVILEGED_OPERATOR`, `AUDITOR` |
| Store 선택 | `ADP_DIGITAL_ASSET_ARTIFACT_STORE_TYPE=local` |
| Local root | `ADP_DIGITAL_ASSET_ARTIFACT_STORE_LOCAL_ROOT=/workspace` |

Ingest 요청은 `manifestReference`와 호출자가 별도 채널에서 전달받은 `expectedContentDigest`를 함께 받는다.
Store가 비활성화된 기본 구성은 503으로 fail closed한다.

## 저장 범위

V27 `policy.digital_asset_artifact_ingestion`에는 artifact identity/digest, manifest reference, Canonical Contract
version/digest, 기관·워크로드·목적지 binding, file count와 Lifecycle 상태만 저장한다. DA Artifact 원문과 대용량
dataset은 DB에 저장하지 않는다. 같은 기관의 동일 artifact ID/version/digest/reference 재요청은 기존 결과를 반환하고,
동일 ID/version의 다른 digest 또는 reference는 충돌로 거부한다.

## Fail-closed 계약

- Manifest/Artifact/Schema JSON 또는 JSON Schema 불일치
- expected digest, manifest self digest, file/schema digest 불일치
- P0-4 artifact ID/version/digest 불일치
- 필수 role 누락, 중복 role/reference, dangling reference
- `UNMAPPED`, `TBD`, `CONTRACT_GAP`이 남은 Artifact
- 다른 institution 또는 허용되지 않은 workload binding
- 절대 경로, 상위 경로 이동, 역슬래시, symlink root escape, 크기 제한 초과

검증과 Lifecycle 전이는 하나의 트랜잭션에서 수행한다. 저장 실패 시 `CANDIDATE`만 남는 중간 상태를 만들지 않는다.

## DA Handoff

`docs/contracts/artifacts/p0-5-sample/manifest.json`은 Loader 계약 검증용 BE fixture다. 실제 DA 결과물은 같은
manifest shape, canonical digest 규칙, 필수 role 집합을 사용해 Store에 게시해야 한다. 현재 DA의 후보 문서에
`CONTRACT_GAP` 등이 남아 있으면 정상적으로 거부되며, 이를 BE enum으로 추정 치환해서는 안 된다.

P0-5는 검증된 Artifact를 Lifecycle Candidate로 등록하는 단계까지다. Candidate를 Runtime에 고정하는 Versioned
Snapshot과 ACTIVE 선택은 P0-6 이후 책임이다.

## 검증

- valid Bundle ingest와 동일 identity replay
- tamper, missing/dangling reference, Schema failure, blocking gap 거부
- traversal 차단
- 인가 실패 시 Store 접근 없음
- cross-institution/cross-workload binding 차단
- 동일 ID/version의 다른 digest 충돌
- V27 schema/constraint 및 전체 회귀 테스트
