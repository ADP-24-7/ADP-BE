# Production Reference Architecture 트러블슈팅

## 설계 완료와 운영 검증 완료가 섞이는 문제

Local Compose에서 Runtime, PostgreSQL, Prometheus rule이 동작해도 HA, OIDC, mTLS, KMS, SIEM, DR이 검증된 것은 아니다.
문서 서술만으로 상태를 관리하면 미래 수정에서 설계 항목이 운영 완료로 바뀔 수 있다. 이를 방지하기 위해 구성요소별 상태와
Cloud 완료 Claim을 `production-reference.json`에 고정한다. Validator는 허용 enum만 보는 것이 아니라 v1의 전체 Claim과
component별 정확한 상태를 비교한다. 상태 승격은 Contract, Validator, 실제 Evidence를 같은 PR에서 의도적으로 변경해야 한다.

## Production-like Profile을 운영 배포로 오인하는 문제

`production-like`는 local fixture, mock, public Prometheus, private destination 예외를 끈 설정 검증용 Profile이다. 실제
API Gateway, private network, Secret Manager, HA DB를 제공하지 않는다. Validator는 보안 토글의 fail-closed 값을 확인하지만
Cloud infrastructure 존재를 주장하지 않는다.

## 애플리케이션 Guard와 Network Egress를 같은 통제로 보는 문제

BE의 HTTPS/host/DNS/SSRF 검증은 요청 의미를 통제하지만 DNS rebinding과 network route 자체를 완전히 통제하지 않는다.
Production Reference에서는 private egress proxy 또는 firewall allowlist를 최종 통제로 두고, Connector가 caller URL을 받지
않는 기존 계약과 함께 적용한다.

## Runtime Rollback과 Migration Rollback을 결합하는 문제

이전 image로 되돌리는 작업이 이미 적용된 DB migration을 자동으로 취소해서는 안 된다. Migration은 additive forward-only로
운영하고 old/new Runtime이 함께 읽을 수 있는 호환 구간을 먼저 만든다. Policy Current Selection rollback과 외부 요청
Recovery도 배포 rollback과 별도 상태 기계로 유지한다.
