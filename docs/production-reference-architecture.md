# Slice 32 Production Reference Architecture

## 목적과 Claim 경계

이 문서는 Local Product에서 검증한 Gateway 구현과 실제 운영 도입 시 필요한 목표 구조를 구분한다. 현재 검증된 범위는
Local Product E2E와 NCP QA Foundation/Object Storage Handoff다. Production Cloud Runtime, HA, DR, Public Demo는 검증하지
않았으며 완료로 표현하지 않는다. 기계 판독 가능한 상태는 `config/architecture/production-reference.json`에서 관리한다.

## 배치 구조

```text
Admin Browser / Runtime Workload
  -> Central API Gateway or private ingress                 [DESIGN_ONLY]
  -> OIDC Admin Session / mTLS Service Identity             [DESIGN_ONLY]
  -> ADP Gateway Runtime                                    [IMPLEMENTED_LOCAL]
       -> HA PostgreSQL                                     [DESIGN_ONLY]
       -> Private Object Storage                            [VERIFIED_QA_FOUNDATION]
       -> Egress control -> approved external providers     [DESIGN_ONLY]
       -> Prometheus/Alertmanager -> SIEM                    [LOCAL / DESIGN_ONLY]
```

Private VPC 배치를 기본으로 하며 On-premise에서는 동일 경계를 사내 API Gateway, IdP, DB, Secret Manager, Egress Proxy,
관측 플랫폼 Adapter로 대체한다. FE는 BE의 same-origin reverse proxy만 사용하고 Provider/Object Storage Credential을 갖지 않는다.

## 책임 경계

| 영역 | 현재 구현 | Production Reference |
| --- | --- | --- |
| Ingress | Local Compose 포트와 Spring Security | 중앙 API Gateway, TLS 종료, WAF/rate limit, private route |
| Admin Auth | Session/CSRF와 BCrypt local fixture | OIDC Authorization Code, short session, MFA/IdP policy |
| Service Auth | SHA-256 API Key lookup | mTLS workload identity 또는 short-lived service credential |
| Runtime | Policy/Transform/Egress/Recovery/Audit | immutable image, 다중 instance, readiness 기반 rollout |
| Database | PostgreSQL/Flyway | private HA PostgreSQL, migration job, 최소권한 role |
| Artifact | NCP private Object Storage Handoff | versioning, retention, scoped workload identity |
| Secret | process environment | Secret Manager/KMS reference와 rotation |
| Egress | server profile, HTTPS/host/SSRF guard | private egress proxy/firewall allowlist와 DNS/IP policy |
| Monitoring | Micrometer/Prometheus rules/Read Model | private scrape, Alertmanager, SIEM/log retention |
| Recovery | lease, reconciliation-first, idempotency | provider별 SLA, runbook, on-call escalation |

SDK와 MCP는 Gateway 정책을 우회하는 별도 Runtime이 아니다. 중앙 API Gateway를 통한 HTTP API를 기본으로 하고, SDK/MCP는
동일 인증·정책·감사 계약을 호출하는 Adapter로만 추가한다. Provider별 Egress Proxy도 Connector 앞의 네트워크 통제 계층이다.

## 데이터와 Secret

- PostgreSQL은 정책·실행·복구·감사 운영 상태의 Source of Truth다.
- Object Storage는 Versioned DA Bundle과 대용량 불변 Evidence를 보관하며 DB 상태를 대체하지 않는다.
- Secret, API Key 원문, KMS plaintext key는 DB/Audit/Log/Metric에 저장하지 않는다.
- 운영 DB role은 Runtime DML, Migration DDL, Read-only Operations로 분리한다.
- Backup retention과 복구 목표는 기관 SLA 확정 후 정하며, restore drill 전에는 DR 검증 완료를 주장하지 않는다.

## 배포와 Rollback

1. SBOM, SAST, dependency/container scan을 통과한 image digest를 생성한다.
2. 별도 Migration Job이 additive Flyway migration을 적용하고 schema version을 확인한다.
3. 새 Runtime은 old/new schema 호환 구간에서 readiness를 통과한 뒤 점진 전환한다.
4. Runtime rollback은 이전 image digest로 수행한다. 이미 적용된 Flyway 파일은 수정하거나 되돌리지 않는다.
5. Policy rollback은 배포 rollback과 분리해 Current Selection과 append-only event로 수행한다.
6. `SENT_UNKNOWN`이 있으면 reconciliation을 우선하며 배포 rollback을 재전송 근거로 사용하지 않는다.

## Monitoring과 Incident

Prometheus scrape endpoint는 private management network와 전용 principal로 제한한다. Alertmanager는 Recovery backlog/age,
Runtime failure, Policy drift, stale metric을 운영 채널로 전달한다. 장기 조사에는 PostgreSQL Audit/Trace와 구조화 로그를 사용하고
Prometheus label에는 tenant, execution ID, request ID를 넣지 않는다. SIEM 연동은 raw payload가 아닌 bounded event와 digest만
전달한다.

## 검증 상태

| Claim | 상태 |
| --- | --- |
| Local Product Runtime/Operations E2E | 검증됨 |
| NCP VPC/Subnet/ACG/Object Storage Foundation | 제한적으로 검증됨 |
| NCP Production Runtime 배포 | 미검증 |
| OIDC/mTLS/KMS 연동 | 설계 전용 |
| HA PostgreSQL/failover | 미검증 |
| Backup restore/DR | 미검증 |
| Private monitoring/SIEM | 설계 전용 |

`make architecture-validate`는 필수 구성요소, Evidence 경로, 금지된 Cloud 완료 Claim과 production-like 보안 설정 drift를
검증한다. 이 검증은 실제 Cloud QA를 대체하지 않는다.
