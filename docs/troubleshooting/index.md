# Troubleshooting Index

ADP-BE 구현 단계에서 코드 리뷰, CI, 마이그레이션, 보안 경계 수정 과정에서 발생한 문제와 해결 근거를 정리한다.

## 문서

- [BE 리뷰 및 CI 트러블슈팅](be-review-and-ci.md)
- [BE-5 Deep Dives](be-5-deep-dives.md)
- [BE-6 이후 플랫폼 트러블슈팅](be-6-and-platform-evolution.md)
- [AI Evaluation 트러블슈팅](ai-evaluation.md)
- [Digital Asset Canonical Contract 트러블슈팅](digital-asset-contract-freeze.md)
- [Digital Asset Artifact Loader 트러블슈팅](digital-asset-artifact-loader.md)
- [Digital Asset Runtime Snapshot 트러블슈팅](digital-asset-runtime-snapshot.md)
- [발표용 Troubleshooting Highlights](presentation-highlights.md)

## 기준 커밋

- `bfb912e` BE-0 Spring Boot service bootstrap
- `43e7c3c` BE-1 authentication/authorization
- `2cfe418` BE-2 internal data access
- `7db3734` BE-3 context detection
- `a95f500` BE-4 policy decision core
- `3822de4` BE-5 transform/vault baseline
- `4128b55` BE-5 transform/vault contract hardening
- `c87caad` BE-5 transform wiring gap close
- `b012902` BE-5 transform invariant enforcement
- `b144d36` BE-5 transform scope isolation
- `9fc4b61` BE-6 common egress boundary
- `5425490` Multi-repository Docker development stack
- `29788da` BE-7 AI Full E2E and policy harness
- `5d5dd3d` Pack-neutral runtime resolvers
- `2216e84` BE-9A idempotency core
- `49a80ae` BE-9B external interaction recovery
- `d3f3973` BE-11A observability foundation
- `08d7bcc` BE-11B audit read model
- `32fb67e` Digital Asset Thin E2E
- `f83028c` Digital Asset recovery
- `2791f55` Digital Asset policy gate
- `f948cbd` Digital Asset mismatch quarantine
- `f7286aa` BE-10 policy lifecycle skeleton
- `80912ac` SEC-0 security baseline
- `b5d8d28` AI-EVAL-0 model profiles
- `96ea184` AI-EVAL-1 run contract
- `f565503` AI-EVAL-2 runtime evidence
- `dc8338d` AI-EVAL-3 evaluation bundle initial export
- `02168c2` DA-P0-3 Digital Asset domain contracts
- `feature/da-p0-4-canonical-contract` DA-P0-4 canonical contract freeze
- `feature/da-p0-5-artifact-loader` DA-P0-5 artifact loader
- `feature/da-p0-6-runtime-snapshot` DA-P0-6 versioned runtime snapshot
