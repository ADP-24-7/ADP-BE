# Blog Asset Manifest

## Covers

- `covers/financial-privacy-gateway-series.png`: generated series key visual
- `covers/01-post-cover.png` ~ `08-post-cover.png`: key visual을 기반으로 Pillow script가 생성한 편별 thumbnail
- 재생성: `python3 docs/blog/assets/covers/render_post_covers.py`

Series key visual generation prompt:

```text
Use case: ads-marketing
Asset type: Korean technical blog series cover image, landscape 16:9
Primary request: A refined editorial illustration representing a Financial Privacy Gateway controlling data before it leaves a financial institution. Show a central luminous gateway or secure checkpoint between structured financial data streams on the left and three abstract external destinations on the right: AI computation, cloud software, and digital assets. The gateway visibly transforms raw data into privacy-safe tokens and evidence trails.
Style: premium isometric 3D editorial illustration
Palette: navy, slate, electric blue, teal, restrained amber
Constraints: no people, no logos, no readable text, no watermark, no cryptocurrency logos
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

재생성:

```bash
python3 docs/blog/assets/diagrams/render_diagrams.py
```

다이어그램은 생성형 이미지가 아니라 저장소의 계약을 그대로 표현하는 코드 기반 PNG다. 상태명과 흐름 변경 시 script와 원고를 함께 수정한다.

## Screenshots

- `screenshots/FPG_01_GatewayLab_SENT_UNKNOWN.png`
- `screenshots/FPG_02_Audit_Trace_SENT_UNKNOWN.png`

두 이미지는 local synthetic fixture를 사용하는 관리자 UI 캡처다. 실제 고객 데이터나 credential을 포함하지 않는다.

