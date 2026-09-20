# Blog Claim Register

시스템을 어디까지 실행했는지와 분석 수치가 어디에서 왔는지는 별도 축으로 관리한다.

- 시스템 검증: `IMPLEMENTED`, `LOCAL_E2E`, `QA_LIMITED`, `DESIGN_ONLY`, `UNVERIFIED`
- Evidence provenance: `SAVED_OUTPUT_HASHED`, `VERSIONED_ARTIFACT_REGENERATED`, `LOCAL_E2E`, `NCP_QA`, `N/A`

| Claim | 시스템 검증 | Evidence provenance | 근거 | 주의할 표현 |
| --- | --- | --- | --- | --- |
| Spring Boot Runtime에서 정책·변환·egress·audit 경계를 구현 | `IMPLEMENTED` | `N/A` | BE main source와 tests | Production 운영이라고 쓰지 않음 |
| 6개 container의 통합 개발 stack 기동 | `LOCAL_E2E` | `LOCAL_E2E` | `make docker-up`, `make docker-ps` | 상용 환경과 구분 |
| AI Evaluation Run과 Bundle export/DA consume | `LOCAL_E2E` | `LOCAL_E2E` | AI-EVAL 문서·테스트·local verification | 모델 우열을 일반화하지 않음 |
| Digital Asset 고정 6-case | `LOCAL_E2E` | `VERSIONED_ARTIFACT_REGENERATED` | `DigitalAssetLocalProductE2ETests`, pinned fixture 6개 | 실자산 거래라고 쓰지 않음 |
| Transform 관계 utility | `N/A` | `SAVED_OUTPUT_HASHED` | AI Transform notebook, synthetic relational fixture | 다른 workload·변환 규칙에 일반화하지 않음 |
| 목적지별 Field 감소 | `N/A` | `SAVED_OUTPUT_HASHED` | DA-04 notebook, current contract | 개인정보 위험 감소율·Provider 성공률로 바꾸지 않음 |
| Amount FLOAT64 정밀도 손실 | `N/A` | `SAVED_OUTPUT_HASHED` | DA-02 notebook, BigQuery 원본 매칭 73,266건 | 모든 자산·네트워크의 손실률로 일반화하지 않음 |
| ZERO_VALUE의 별도 가치 이동 | `N/A` | `SAVED_OUTPUT_HASHED` | DA-03 notebook, 2026-01 분석대상 3,452건 | Ethereum 전체 거래 비율로 일반화하지 않음 |
| AI 3-model benchmark | `N/A` | `VERSIONED_ARTIFACT_REGENERATED` | pinned benchmark/validation JSON, isolated harness actual provider calls 270회, Production Runtime 미호출 | 보편적 모델 순위·운영 승인으로 표현하지 않음 |
| Recovery 98.41% vs 0% | `N/A` | `SAVED_OUTPUT_HASHED` | DA-06 counterfactual notebook output | 실제 timeout·중복률 또는 성능 개선율로 표현하지 않음 |
| `SENT_UNKNOWN` reconciliation-first | `LOCAL_E2E` | `LOCAL_E2E` | recovery tests, local fake adapter | 실제 Provider adapter와 구분 |
| Policy Shadow/Maker-Checker/Selection rollback | `LOCAL_E2E` | `LOCAL_E2E` | policy E2E/concurrency tests | 실제 조직 승인 완료라고 쓰지 않음 |
| NCP VPC/Subnet/ACG/Object Storage foundation | `QA_LIMITED` | `NCP_QA` | ADP-Infra state/docs, NCP ingest evidence | 전체 NCP 배포라고 쓰지 않음 |
| OIDC/mTLS/KMS, HA DB, DR, private SIEM | `DESIGN_ONLY` 또는 `UNVERIFIED` | `N/A` | Production Reference Architecture | 구현·운영 완료라고 쓰지 않음 |
| Reference Evidence가 Runtime 허용을 결정 | 사용 금지 | `N/A` | 실제 계약과 반대 | `REFERENCE_ONLY`임을 명시 |
| 법률 적용 여부를 자동 판정 | 사용 금지 | `N/A` | 범위 밖 | 법률 자문 대체 표현 금지 |
| 전체 repository lock과 CI contract pin이 동일 | 사용 금지 | `N/A` | 목적과 scope가 다름 | DA CI 외부 fork 참조도 명시 |

## 공개 금지 데이터

- API key, Cloud credential, password, private endpoint
- 실제 고객·계좌·Wallet 원문
- raw prompt와 token mapping
- Provider secret과 Private Key
- 내부 사용자 식별정보
