# ADP-DA Notebook Evidence Map

검토일: 2026-09-20

블로그에 노트북 이미지를 넣기 전에 ADP-DA의 `.ipynb` 25개를 저장된 실행 상태, 오류 output, 그림 output, 근거 범위로 분류했다. 모든 노트북의 JSON 구조와 cell/output을 확인했으며, 저장된 error output은 0개였다. 다만 output이 없다는 것은 실행 성공을 뜻하지 않으므로 아래와 같이 구분한다.

| 영역 | Notebook | 저장 상태 | 블로그 사용 판단 |
| --- | --- | --- | --- |
| 산업 분석 | `01_financial_regulatory_sandbox_r.ipynb` | 실행 output·그림 4개 | 산업 배경 자료다. FPG 구현 검증으로 사용하지 않음 |
| 산업 분석 | `02_FSC_policy_r.ipynb` | 실행 output·그림 2개 | 정책 동향 자료다. Runtime 통제 효과로 사용하지 않음 |
| 산업 분석 | `03_five_major_banks_trends_r.ipynb` | 실행 output·그림 6개 | 시장 동향 자료다. 제품 Evidence로 사용하지 않음 |
| AI Evidence | `evidence_ontology/notebooks/01_evidence_review.ipynb` | 일부 cell만 실행, 그림 없음 | Evidence 검토 절차의 보조 근거. 정량 결과 이미지로 사용하지 않음 |
| AI 분석 | `01_evaluation_ai_field_utility_v1.ipynb` | 실행 output, 그림 없음 | Field별 정확값 요구 분석. 본문 배경 근거로만 사용 |
| AI 분석 | `02_evaluation_ai_workload_v1.ipynb` | 실행 output·그림 8개 | 상담 topic/action 탐색. 현재 시리즈의 핵심 Claim과 거리가 있어 미사용 |
| AI 분석 | `03_evaluation_ai_transform_tradeoff_v1.ipynb` | 실행 output·그림 7개 | 합성 관계형 금융 데이터의 Transform utility. 4편에 cell 37 사용 |
| AI 분석 | `04_evaluation_ai_legal_constraint_v1.ipynb` | 실행 output, 그림 없음 | 법률 문장 구조화 실험. 법률 판단처럼 보이지 않도록 이미지 미사용 |
| AI 평가 | `AI_EVAL_01_bundle_validation.ipynb` | 미실행 | 실행 Evidence로 사용하지 않음 |
| AI 평가 | `AI_EVAL_02_model_analysis.ipynb` | 미실행 | 실행 Evidence로 사용하지 않음 |
| AI 평가 | `AI_EVAL_03_runtime_analysis.ipynb` | 미실행 | 실행 Evidence로 사용하지 않음 |
| AI 평가 | `AI_EVAL_04_policy_effect_analysis.ipynb` | 미실행 | 실행 Evidence로 사용하지 않음 |
| DA foundation | `01_crypto_regulatory_analysis.ipynb` | 미실행 | 설계 입력. 검증 결과로 사용하지 않음 |
| DA foundation | `02_crypto_transaction_schema_analysis.ipynb` | 미실행 | 설계 입력. 검증 결과로 사용하지 않음 |
| DA foundation | `03_ethereum_transaction_schema_validation.ipynb` | 미실행 | 설계 입력. 검증 결과로 사용하지 않음 |
| DA foundation | `04_regulatory_outbound_design_analysis.ipynb` | 미실행 | 설계 입력. 검증 결과로 사용하지 않음 |
| DA foundation | `05_fpg_control_boundary_validation.ipynb` | 미실행 | 설계 입력. 검증 결과로 사용하지 않음 |
| DA Runtime | `DA_00_master_sample.ipynb` | 실행 output·그림 4개 | 73,410건 표본 구조 근거. 본문 과밀을 피하기 위해 그림 미사용 |
| DA Runtime | `DA_01_external_execution.ipynb` | 실행 output, 그림 없음 | Transaction과 execution/finality 구분의 보조 근거 |
| DA Runtime | `DA_02_exact_preservation.ipynb` | 실행 output·그림 1개 | FLOAT64 exactness 근거. 5편에 cell 16 사용 |
| DA Runtime | `DA_03_trace_binding.ipynb` | 실행 output·그림 1개 | transaction/receipt/trace binding 근거. 5편에 cell 29 사용 |
| DA Runtime | `DA_04_outbound_destination.ipynb` | 전체 code cell 실행·그림 2개 | 목적지별 Field 최소화. 4편에 cell 12 사용 |
| DA Runtime | `DA_05_approved_requested_match.ipynb` | 실행 output·그림 2개 | 승인값과 요청값 정합성. 5편의 6-case 설명에 반영 |
| DA Runtime | `DA_06_recovery_idempotency.ipynb` | 실행 output·그림 1개 | counterfactual 복구 전략 비교. 6편에 cell 9 사용 |

## 채택한 Notebook 그림

| 블로그 파일 | 원본 | Cell | 한 문장 Claim | 필수 한계 |
| --- | --- | ---: | --- | --- |
| `01-transform-relationship-utility.png` | AI transform trade-off | 37 | 해당 합성 fixture에서는 부분 MASK가 관계의 고유성을 보존하지 못했다 | 모든 workload·MASK 규칙에 일반화하지 않음 |
| `02-destination-field-minimization.png` | DA-04 | 12 | destination profile은 공통 superset보다 외부 전달 Field를 줄였다 | 개인정보 위험 감소율이나 Provider E2E 성공률이 아님 |
| `03-recovery-strategy-risk.png` | DA-06 | 9 | 이미 제출된 거래를 실패로 간주한 즉시 재전송은 중복 효과 위험을 만든다 | 실제 timeout 발생률이 아닌 counterfactual 실험 |
| `06-digital-asset-amount-precision.png` | DA-02 | 16 | 해당 표본에서 `2^53` 초과 FLOAT64 변환은 원본 wei를 정확히 보존하지 못했다 | 모든 자산·네트워크의 손실률로 일반화하지 않음 |
| `07-zero-value-movement-evidence.png` | DA-03 | 29 | ZERO_VALUE 분석대상에서도 Token/Internal ETH 이동이 존재했다 | Ethereum 전체 거래의 가치 이동 비율로 일반화하지 않음 |

그림은 [`extract_notebook_figures.py`](../assets/charts/extract_notebook_figures.py)가 source notebook SHA-256과 cell 번호를 확인한 뒤 embedded PNG를 추출한다. Notebook이 바뀌면 자동으로 실패하므로, 발행 시점에 그림과 분석이 조용히 어긋나지 않는다.

## 별도 Artifact에서 만든 그림

- `04-ai-model-quality-latency.png`: `benchmark_model_summary.json`과 `benchmark_validation.json`을 읽어 생성한다. 합성 업무 30 case × 3회 × 3 model, 실제 호출 270회 범위에만 적용한다. Mean뿐 아니라 p50, p95, 성공률을 함께 표시한다.
- `05-digital-asset-six-case-matrix.png`: `local_product_e2e_v1`의 versioned synthetic fixture 6개를 읽어 생성한다. 예상 계약과 로컬 E2E 검증을 보여주며 실자산 실행을 의미하지 않는다.

두 이미지는 [`render_evidence_charts.py`](../assets/charts/render_evidence_charts.py)로 재생성한다. Script는 ADP-DA commit `74af1928d720d4a37addeedab1770d81b4fff8a5`와 benchmark JSON 2개, 6-case fixture 6개의 SHA-256을 먼저 확인한다. commit이나 파일 내용이 바뀌면 PNG를 조용히 덮어쓰지 않고 실패한다.

## 재현성 한계

- 이번 검토에서는 25개 notebook의 저장된 cell/output과 error output을 전수 확인했지만, 전체 notebook을 현재 macOS 환경에서 top-to-bottom 재실행하지는 않았다.
- 특히 `DA_06_recovery_idempotency.ipynb`는 입력 경로가 Windows 절대 경로로 저장돼 있어 경로를 매개변수화하기 전에는 그대로 재실행할 수 없다.
- 따라서 1~3번과 6~7번 chart는 “이번 작업에서 새로 산출한 결과”가 아니라 source SHA와 cell을 고정해 추출한 기존 실행 output이다. 본문은 이 차이를 숨기지 않고 notebook의 모집단·실험 방식·한계를 함께 적는다.
- 4~5번 chart는 commit과 SHA가 고정된 versioned JSON/fixture를 현재 환경에서 다시 읽어 생성했으며, 원천 Provider 호출이나 Product E2E를 다시 수행한 것은 아니다.
