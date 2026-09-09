# ADP-BE PR Troubleshooting Coverage Audit

## 감사 범위와 방법

2026-09-09 기준 `main`의 first-parent 이력과 로컬에 남아 있는 각 feature branch의 `feat/fix/test/docs` 커밋을
대조했다. `main`은 PR별 squash commit을 사용하므로, 아래 표의 main commit은 최종 병합 결과를 가리키고 상세 원인은
각 troubleshooting 문서에서 리뷰 보완 commit과 함께 설명한다.

GitHub CLI는 인증되지 않은 환경에서도 재현할 수 있도록 감사의 Source of Truth로 사용하지 않았다. 검증 기준은
로컬 Git object에 남은 PR #1~#37의 merge commit, feature branch commit, 변경 파일과 테스트다.

## PR별 Coverage

| PR | Main commit | 핵심 Troubleshooting | 문서 |
| --- | --- | --- | --- |
| #1 | `bfb912e` | Runtime contract와 모듈 경계 고정 | `be-review-and-ci.md` |
| #2 | `43e7c3c` | API key hash, subject grant, local auth 격리 | `be-review-and-ci.md` |
| #3 | `2cfe418` | 자유 SQL 차단, 최소 field retrieval, invalid profile fail-closed | `be-review-and-ci.md` |
| #4 | `7db3734` | Runtime DataClass Source of Truth와 DA 분석 언어 분리 | `be-review-and-ci.md` |
| #5 | `a95f500` | Policy/Final Action, DA Artifact/BE Snapshot, applicability 분리 | `be-review-and-ci.md` |
| #6 | `6df4cab` | 실제 HMAC, Transform wiring, Vault race와 scope isolation | `be-5-deep-dives.md`, `be-review-and-ci.md` |
| #7 | `9fc4b61` | Connector 상태 분리와 legacy migration safety | `be-6-and-platform-evolution.md` |
| - | `5425490` | 최신 sibling source 기반 멀티 레포 Docker 개발환경 | `be-6-and-platform-evolution.md` |
| #8 | `29788da` | Authorization 순서, controlled delivery, response guard | `be-6-and-platform-evolution.md` |
| #9 | `5d5dd3d` | Common Runtime의 Pack 전용 예외 의존 제거 | `be-6-and-platform-evolution.md` |
| #10 | `2216e84` | 인가 전 idempotency 선점과 replay 의미 | `be-6-and-platform-evolution.md` |
| #11 | `49a80ae` | `SENT_UNKNOWN`, lease, stale worker와 reconciliation-first | `be-6-and-platform-evolution.md` |
| #12 | `d3f3973` | terminal/recovery 관측 우회와 low-cardinality metric | `be-6-and-platform-evolution.md` |
| #13 | `08d7bcc` | Audit SQL tenant/workload scope와 Evidence FK | `be-6-and-platform-evolution.md` |
| #14 | `32fb67e` | Settlement 원자성, request correlation, terminal observability | `be-6-and-platform-evolution.md` |
| #15 | `f83028c` | Mock이 결과를 미리 아는 문제와 상태 질의 기반 복구 | `be-6-and-platform-evolution.md` |
| #16 | `2791f55` | 외부 Compliance 입력 불신과 미구성 fail-closed | `be-6-and-platform-evolution.md` |
| #17 | `f948cbd` | Provider 임의 key의 Evidence 유입과 mismatch 격리 | `be-6-and-platform-evolution.md`, `presentation-highlights.md` |
| #18 | `f7286aa` | Lifecycle tenant identity, optimistic revision, state/history 원자성 | `be-6-and-platform-evolution.md` |
| #19 | `80912ac` | unmatched endpoint의 default deny | `be-6-and-platform-evolution.md` |
| #20 | `b5d8d28` | Provider profile이 Credential/Model 선택권을 소유 | `ai-evaluation.md` |
| #21 | `96ea184` | immutable Evaluation Run과 replay contract | `ai-evaluation.md` |
| #22 | `f565503` | Evidence 실패 격리와 malformed HTTP 200 분류 | `ai-evaluation.md` |
| #23 | `5d5f999` | Case x Model 완전성, authoritative digest와 canonicalization | `ai-evaluation.md` |
| #24 | `b03b5d4` | Eligibility 제거 전 Approved Transaction 선행 순서 | `approved-transaction-trust-boundary.md` |
| #25 | `b9bfb71` | 과거 COMPLETE 결과가 현재 3-model 실행을 대체하는 문제 | `ai-evaluation.md` |
| #26 | `a714d57` | Approval 존재가 아닌 scope/terms 결속과 trusted metadata 제한 | `approved-transaction-trust-boundary.md` |
| #27 | `02168c2` | Server-owned field와 Optional/Conditional wire contract | `approved-transaction-trust-boundary.md`, `digital-asset-contract-freeze.md` |
| #28 | `cf28a45` | Cross-language canonical digest와 Schema/parser drift | `digital-asset-contract-freeze.md` |
| #29 | `969aa14` | Producer Schema 신뢰 역전, symlink escape, ingest transaction | `digital-asset-artifact-loader.md` |
| #30 | `31ae5f1` | ACTIVE selection, atomic replacement와 pinned snapshot | `digital-asset-runtime-snapshot.md` |
| #31 | `062ab8a` | NCP object key와 다운로드 bytes의 content-address 결속 | `ncp-artifact-content-store.md` |
| #32 | `5a7c3c0` | Connector 직전 snapshot 재검증과 exact field digest lineage | `digital-asset-pre-execution-guard.md` |
| #33 | `b5897a5` | Provider 응답과 독립된 사후 증적, typed unknown recovery | `digital-asset-post-execution-evidence.md` |
| #34 | `80027c0` | Shadow 평가 중 Candidate/ACTIVE 변경에 대한 stale evidence 차단 | `policy-shadow-evidence.md` |
| #35 | `b7b3a8e` | 범용 승인 우회 차단과 Shadow Evidence 기반 maker-checker 승인 | `policy-shadow-approval-gate.md` |
| #36 | `b99752f` | 단일 Current Selection, 원자적 교체/롤백과 stale approval 차단 | `policy-current-selection-rollback.md` |
| #37 | `5561577` | Server-owned trace, denied-attempt evidence, SSRF/request freshness와 Security CI Gate | `security-hardening.md` |
| #39 | `0699485` | Technical Kind와 Semantic Class 분리, caller self-asserted classification 차단 | `da-runtime-contract-gap-review.md` |

## 현재 브랜치

`feature/be-11-observability-operations`는 아직 `main`에 병합되지 않았으며 미병합 BE-9 Recovery Operations 커밋 위에서
분기한 stacked branch다. BE-9는 `recovery-operations.md`, BE-11은 `observability-operations.md`에서 각각 추적한다.

## 확인 결과

- PR #1~#37의 main merge commit을 모두 문서에 연결했다.
- feature branch commit hash와 main squash commit을 같은 identity로 취급하지 않는다.
- 구현 설명만 있던 Approved Transaction, NCP ContentStore 이슈를 troubleshooting 문서로 승격했다.
- AI real E2E의 fresh execution identity와 Lifecycle optimistic locking/transaction 문제를 기존 문서에 보강했다.
- 이후 PR은 merge 시 이 표에 main commit과 troubleshooting 문서 위치를 추가한다.
