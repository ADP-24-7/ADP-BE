# Blog Asset Manifest

## Covers

- `covers/financial-privacy-gateway-series.png`: generated series key visual
- `covers/01-post-cover.png` ~ `08-post-cover.png`: key visual을 기반으로 Pillow script가 생성한 편별 thumbnail
- 재생성: `python3 docs/blog/assets/covers/render_post_covers.py`

Series key visual generation prompt:

```text
Use case: ads-marketing
Asset type: Korean technical blog series cover image, landscape 16:9
Primary request: A refined editorial illustration representing a Financial Privacy Gateway controlling data before it leaves a financial institution. Show a central luminous gateway or secure checkpoint between structured financial data streams on the left and exactly two abstract external destinations on the right: AI computation and digital assets. The gateway visibly transforms raw data into privacy-safe tokens and evidence trails.
Style: premium isometric 3D editorial illustration
Palette: navy, slate, electric blue, teal, restrained amber
Constraints: no people, no logos, no readable text, no watermark, no cryptocurrency logos
Avoid: generic cloud-platform destinations, cloud icons, a third external destination
```

## Diagrams

- `01-four-planes-repositories.png`
- `02-evidence-lineage.png`
- `03-runtime-sequence.png`
- `04-transform-egress-boundary.png`
- `05-ai-digital-asset-packs.png`
- `06-sent-unknown-recovery.png`
- `07-policy-lifecycle.png`
- `08-verification-claims.png`
- `09-ci-security-pipeline.png`

재생성:

```bash
python3 docs/blog/assets/diagrams/render_diagrams.py
```

다이어그램은 생성형 이미지가 아니라 저장소의 계약을 그대로 표현하는 코드 기반 PNG다. 상태명과 흐름 변경 시 script와 원고를 함께 수정한다.

## Evidence charts

- `charts/01-transform-relationship-utility.png`: AI Transform notebook cell 37 embedded output
- `charts/02-destination-field-minimization.png`: DA-04 notebook cell 12 embedded output
- `charts/03-recovery-strategy-risk.png`: DA-06 notebook cell 9 embedded output
- `charts/04-ai-model-quality-latency.png`: isolated benchmark harness의 model benchmark JSON 기반 재생성 차트
- `charts/05-digital-asset-six-case-matrix.png`: local product E2E fixture 6개 기반 재생성 표
- `charts/06-digital-asset-amount-precision.png`: DA-02 notebook cell 16 embedded output
- `charts/07-zero-value-movement-evidence.png`: DA-03 notebook cell 29 embedded output

재생성:

```bash
python3 docs/blog/assets/charts/extract_notebook_figures.py
python3 docs/blog/assets/charts/render_evidence_charts.py
```

Artifact 차트를 재생성할 때는 `ADP-BE`와 `ADP-DA`를 동일한 상위 디렉터리에 두고, `ADP-DA`를 baseline commit `74af1928d720d4a37addeedab1770d81b4fff8a5`로 checkout해야 한다. Script는 이 commit과 입력 JSON/fixture의 SHA-256이 다르면 렌더링하지 않고 실패한다.

Notebook 그림 추출 script는 원본 notebook SHA-256과 cell 번호를 확인한다. Artifact chart script는 ADP-DA commit과 benchmark/validation/fixture 파일별 SHA-256을 함께 확인한다. 정량 수치와 사용 한계는 [`../sources/notebook-evidence-map.md`](../sources/notebook-evidence-map.md)에 기록한다.

## Screenshots

- `screenshots/FPG_03_Overview_AI_Control_Flow.jpg`: AI Pack의 8단계 통제 흐름 panel
- `screenshots/FPG_04_Overview_DigitalAsset_Control_Flow.jpg`: Digital Asset Pack의 6단계 완료 흐름 panel
- `screenshots/FPG_05_Reference_Evidence_Lineage.jpg`: Reference Evidence의 Source·Analysis·Claim·Digest 상세
- `screenshots/FPG_07_GatewayLab_Field_Treatment.jpg`: 승인 참조, 외부 실행 대상, 필드별 처리 방식
- `screenshots/FPG_08_Recovery_Incidents.jpg`: 복구 대기·수동 검토·처리 지연 운영 현황
- `screenshots/FPG_09_Policy_Lifecycle_Current_Selection.jpg`: Current Selection과 Runtime Evidence
- `screenshots/FPG_11_Security_Monitoring_Findings.jpg`: AI 응답의 민감정보 탐지 현황
- `screenshots/FPG_12_Data_Access_Field_Allowlist.jpg`: AI 조회 계약의 Field Allowlist, Data Class별 범위, Context Digest

여덟 이미지는 전용 Chrome 세션에서 local synthetic fixture와 실제 BE API를 사용해 캡처했다. 포인터와 브라우저 chrome을 제외하고, 카드·표·단계의 경계가 잘리지 않도록 한 주장에 필요한 영역만 남겼다. 실제 고객 데이터·credential·secret·실자산을 포함하지 않으며, 캡처 시점의 수치는 고정 성능 지표가 아니라 로컬 운영 상태의 예시다.
