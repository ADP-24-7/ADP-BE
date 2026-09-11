# AI Calibration Evidence 트러블슈팅

## Finding count만으로 Privacy와 Utility를 구분할 수 없던 문제

Experiment 01은 세 모델 모두 `RAW_VALUE_REFLECTION`으로 차단됐지만, 기존 Bundle에는 Guard 상태만 있고 어떤 Data Class와
Transform 결과가 reflection됐는지 없었다. Finding 수만 비교하면 더 짧은 답변을 더 안전한 모델로 오판할 수 있다.

V50부터 reflection 시점에 `source_data_class`, `transform_strategy`, `field_treatment`, field path digest를 저장한다.
Calibration API는 이 값을 그룹화해 제공하며 원문 응답과 exact value는 저장하거나 반환하지 않는다.

## 원문을 숨겨도 일반 SHA-256 digest로 개인정보를 추정할 수 있는 문제

전화번호, 이메일, 계좌번호처럼 후보 공간이 작은 개인정보는 일반 SHA-256 digest만 공개해도 사전 대입으로 원문을 추정할 수
있다. 특히 Finding type까지 함께 제공하면 공격자가 후보 범위를 더 좁힐 수 있다.

내부 Finding의 `evidence_digest`는 기존 Runtime Evidence 호환성을 위해 유지하되 Calibration API와 JSON Schema에서는 완전히
제거했다. DA Calibration에는 Finding type, Data Class, Transform Strategy, Field Treatment와 occurrence count만 제공한다.
향후 개별 fingerprint가 반드시 필요해질 때만 서버 소유 secret과 domain separation을 적용한 HMAC 계약을 별도로 설계한다.

## 생성 시각과 Source Window를 같은 값으로 취급한 문제

`generated_at`은 조회할 때마다 달라지는 응답 생성 시각이므로 deterministic content digest에서 제외한다. 반면
`execution_from`, `execution_cutoff_at`, `execution_count`는 어떤 실행 범위를 분석했는지 나타내는 provenance다. 이 값들은
실행 Evidence와 함께 canonical content에 포함해 Source Window 변조가 digest 불일치로 탐지되도록 했다.

## 과거 Finding을 임의 분류하면 재현성이 깨지는 문제

기존 row는 response offset과 evidence digest만 있어 어떤 Outbound field가 원인이었는지 역산할 수 없다. 현재 Catalog나
Transform 규칙으로 backfill하면 실행 당시 사실이 아니라 현재 설정을 과거 Evidence에 투영하게 된다.

따라서 migration은 nullable additive column만 추가하고 과거 row를 보존한다. `RAW_VALUE_REFLECTION` metadata가 누락되면
API는 `REFLECTION_METADATA_MISSING`으로 fail closed하며 V50 이후 재실행을 요구한다.

## 분석 계약이 Runtime 정책을 우회할 수 있는 문제

Calibration 결과는 분석용 Read Model이다. 이를 Response Guard threshold나 ACTIVE Policy에 직접 연결하면 DA 결과가
Maker-Checker를 우회한다. 기존 Bundle v2와 Runtime 판단을 유지하고, 변경 후보는 Version/Digest가 있는 Candidate로 만들어
Replay/Shadow/Approval을 거치도록 경계를 분리했다.
