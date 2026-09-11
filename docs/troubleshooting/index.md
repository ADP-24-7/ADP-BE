# Troubleshooting Index

- [Local Integration Reproducibility](local-integration-reproducibility.md)
- [Production Reference Architecture](production-reference-architecture.md)

ADP-BE 구현 단계에서 코드 리뷰, CI, 마이그레이션, 보안 경계 수정 과정에서 발생한 문제와 해결 근거를 정리한다.

## 문서

- [Session Authentication and Maker-Checker Closure](session-auth-maker-checker.md)
- [Admin Console 인증 컨텍스트 경계](admin-console-auth-context.md)
- [BE 리뷰 및 CI 트러블슈팅](be-review-and-ci.md)
- [BE-5 Deep Dives](be-5-deep-dives.md)
- [BE-6 이후 플랫폼 트러블슈팅](be-6-and-platform-evolution.md)
- [AI Evaluation 트러블슈팅](ai-evaluation.md)
- [Approved Transaction Trust Boundary 트러블슈팅](approved-transaction-trust-boundary.md)
- [Digital Asset Canonical Contract 트러블슈팅](digital-asset-contract-freeze.md)
- [Digital Asset Artifact Loader 트러블슈팅](digital-asset-artifact-loader.md)
- [Digital Asset Runtime Snapshot 트러블슈팅](digital-asset-runtime-snapshot.md)
- [Digital Asset PRE_EXECUTION Guard 트러블슈팅](digital-asset-pre-execution-guard.md)
- [Digital Asset POST_EXECUTION Evidence 트러블슈팅](digital-asset-post-execution-evidence.md)
- [Digital Asset Local Product 6-Case E2E 트러블슈팅](digital-asset-local-product-e2e.md)
- [Digital Asset Current State Read Model 트러블슈팅](digital-asset-current-state-read-model.md)
- [Pack-aware Operations Scope 트러블슈팅](pack-aware-operations-scope.md)
- [Review Queue Read Model 트러블슈팅](review-queue-read-model.md)
- [Security Finding Read Model 트러블슈팅](security-finding-read-model.md)
- [Admin Identity / Permission Read Model 트러블슈팅](admin-identity-permission-read-model.md)
- [Policy Replay / Shadow Evidence 트러블슈팅](policy-shadow-evidence.md)
- [Policy Shadow Approval Gate 트러블슈팅](policy-shadow-approval-gate.md)
- [Policy Current Selection / Rollback 트러블슈팅](policy-current-selection-rollback.md)
- [Security Hardening 트러블슈팅](security-hardening.md)
- [Security Negative Matrix와 Evidence Export 트러블슈팅](security-evidence-export.md)
- [NCP Artifact ContentStore 트러블슈팅](ncp-artifact-content-store.md)
- [DA Analysis to BE Contract Gap Review 트러블슈팅](da-runtime-contract-gap-review.md)
- [Recovery Operations 트러블슈팅](recovery-operations.md)
- [Observability Operations 트러블슈팅](observability-operations.md)
- [Reference Evidence Plane 트러블슈팅](reference-evidence-plane.md)
- [Policy Operations Read Model 트러블슈팅](policy-operations-read-model.md)
- [PR #1~#37 Troubleshooting Coverage Audit](pr-history-audit.md)
- [발표용 Troubleshooting Highlights](presentation-highlights.md)

## 이력 관리 기준

PR별 main squash commit과 리뷰 보완 commit은 서로 다른 Git identity다. 전체 main PR coverage와 문서 위치는
[PR Troubleshooting Coverage Audit](pr-history-audit.md)에서 관리한다. 이후 PR을 병합할 때 해당 표와 발표용 Highlight를
함께 검토한다.
