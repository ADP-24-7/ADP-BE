# Blog Claim Register

| Claim | 등급 | 근거 | 주의할 표현 |
| --- | --- | --- | --- |
| Spring Boot Runtime에서 정책·변환·egress·audit 경계를 구현 | `IMPLEMENTED` | BE main source와 tests | Production 운영이라고 쓰지 않음 |
| 6개 container의 통합 개발 stack 기동 | `LOCAL_VERIFIED` | `make docker-up`, `make docker-ps` | 상용 환경과 구분 |
| AI Evaluation Run과 Bundle export/DA consume | `LOCAL_VERIFIED` | AI-EVAL 문서·테스트·local verification | 모델 우열을 일반화하지 않음 |
| Digital Asset 고정 6-case | `LOCAL_VERIFIED` | `DigitalAssetLocalProductE2ETests` | 실자산 거래라고 쓰지 않음 |
| `SENT_UNKNOWN` reconciliation-first | `LOCAL_VERIFIED` | recovery tests, local fake adapter | 실제 Provider adapter와 구분 |
| Policy Shadow/Maker-Checker/Selection rollback | `LOCAL_VERIFIED` | policy E2E/concurrency tests | 실제 조직 승인 완료라고 쓰지 않음 |
| NCP VPC/Subnet/ACG/Object Storage foundation | `QA_LIMITED` | ADP-Infra state/docs, NCP ingest evidence | 전체 NCP 배포라고 쓰지 않음 |
| OIDC/mTLS/KMS, HA DB, DR, private SIEM | `DESIGN_ONLY` 또는 `UNVERIFIED` | Production Reference Architecture | 구현·운영 완료라고 쓰지 않음 |
| Reference Evidence가 Runtime 허용을 결정 | 사용 금지 | 실제 계약과 반대 | `REFERENCE_ONLY`임을 명시 |
| 법률 적용 여부를 자동 판정 | 사용 금지 | 범위 밖 | 법률 자문 대체 표현 금지 |

## 공개 금지 데이터

- API key, Cloud credential, password, private endpoint
- 실제 고객·계좌·Wallet 원문
- raw prompt와 token mapping
- Provider secret과 Private Key
- 내부 사용자 식별정보

