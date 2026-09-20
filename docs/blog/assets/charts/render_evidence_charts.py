import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
WORKSPACE = OUT.parents[4]
W, H = 1600, 900
BG = "#081426"
PANEL = "#11243D"
LINE = "#294766"
TEXT = "#F3F7FC"
MUTED = "#9DB0C7"
BLUE = "#4F8CFF"
TEAL = "#34D3C2"
AMBER = "#F4B860"
RED = "#FF7285"


def font(size: int, bold: bool = False):
    paths = [
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    ]
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(
                path,
                size=size,
                index=7 if bold and "AppleSD" in path else 0,
            )
    return ImageFont.load_default()


F_TITLE = font(44, True)
F_SUB = font(23)
F_HEAD = font(25, True)
F_BODY = font(21)
F_SMALL = font(17)


def base(title: str, subtitle: str):
    image = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(image)
    draw.text((72, 50), title, font=F_TITLE, fill=TEXT)
    draw.text((74, 112), subtitle, font=F_SUB, fill=MUTED)
    draw.line((72, 162, W - 72, 162), fill=LINE, width=2)
    return image, draw


def ai_tradeoff():
    source = WORKSPACE / "ADP-DA/02_ai/artifacts/model_benchmark/benchmark_model_summary.json"
    validation_source = WORKSPACE / "ADP-DA/02_ai/artifacts/model_benchmark/benchmark_validation.json"
    models = json.loads(source.read_text(encoding="utf-8"))["models"]
    validation = json.loads(validation_source.read_text(encoding="utf-8"))

    image, draw = base(
        "합성 금융 업무에서 확인한 모델별 품질–지연시간 Trade-off",
        "30 cases × 3 repetitions = 모델별 90회 · 비용은 공식 단가 미확인으로 비교에서 제외",
    )
    left, top, right, bottom = 150, 230, 1450, 720
    draw.rounded_rectangle((85, 195, 1515, 785), radius=24, fill=PANEL, outline=LINE, width=2)
    draw.line((left, bottom, right, bottom), fill=MUTED, width=2)
    draw.line((left, top, left, bottom), fill=MUTED, width=2)

    min_latency = min(model["latency_mean_ms"] for model in models) * 0.8
    max_latency = max(model["latency_mean_ms"] for model in models) * 1.08
    min_quality, max_quality = 75.0, 92.0

    def px(latency):
        return left + (latency - min_latency) / (max_latency - min_latency) * (right - left)

    def py(quality):
        return bottom - (quality - min_quality) / (max_quality - min_quality) * (bottom - top)

    for value in [80, 85, 90]:
        y = py(value)
        draw.line((left, y, right, y), fill=LINE, width=1)
        draw.text((92, y - 12), str(value), font=F_SMALL, fill=MUTED)

    draw.text((150, 198), "평균 품질 ↑", font=F_BODY, fill=TEXT)
    for value in [10000, 15000, 20000]:
        x = px(value)
        draw.line((x, bottom, x, bottom + 8), fill=MUTED, width=2)
        draw.text((x - 18, bottom + 12), f"{value // 1000}s", font=F_SMALL, fill=MUTED)

    colors = [AMBER, TEAL, BLUE]
    offsets = [(-30, -60), (20, -55), (18, 22)]
    for model, color, offset in zip(models, colors, offsets):
        x = px(model["latency_mean_ms"])
        y = py(model["quality_mean"])
        draw.ellipse((x - 16, y - 16, x + 16, y + 16), fill=color, outline=TEXT, width=2)
        label = model["model"]
        draw.text((x + offset[0], y + offset[1]), label, font=F_HEAD, fill=TEXT)
        draw.text(
            (x + offset[0], y + offset[1] + 34),
            f"quality {model['quality_mean']:.1f} · {model['latency_mean_ms'] / 1000:.1f}s",
            font=F_SMALL,
            fill=MUTED,
        )

    draw.text((620, 742), "평균 응답 지연시간 (짧을수록 왼쪽)", font=F_BODY, fill=TEXT)
    draw.text(
        (92, 820),
        f"검증 상태: {validation['status']} · 실제 모델 호출 {validation['actual_executions']}회 · synthetic/public data only",
        font=F_SMALL,
        fill=MUTED,
    )
    image.save(OUT / "04-ai-model-quality-latency.png", "PNG", optimize=True)


def digital_asset_cases():
    fixture_dir = WORKSPACE / "ADP-DA/03_digital_asset/artifacts/local_product_e2e_v1"
    order = [
        "golden_pass.json",
        "block_amount.json",
        "block_destination.json",
        "execution_failed.json",
        "sent_unknown_recovered.json",
        "duplicate_request.json",
    ]
    rows = [json.loads((fixture_dir / name).read_text(encoding="utf-8")) for name in order]

    image, draw = base(
        "Digital Asset 6-case가 고정한 완료 조건",
        "API status가 아니라 최종 상태 · 외부 효과 수 · blind resend 허용 여부를 함께 검증한다",
    )
    x = [80, 470, 1090, 1300, 1515]
    headers = ["Fixture", "Expected final state", "External effect", "Blind resend"]
    for index, header in enumerate(headers):
        draw.rounded_rectangle((x[index], 205, x[index + 1] - 10, 265), radius=12, fill="#173250")
        draw.text((x[index] + 18, 220), header, font=F_HEAD, fill=TEXT)

    for row_index, row in enumerate(rows):
        y1 = 285 + row_index * 82
        y2 = y1 + 64
        fill = PANEL if row_index % 2 == 0 else "#0D1D32"
        draw.rounded_rectangle((80, y1, 1505, y2), radius=12, fill=fill, outline=LINE, width=1)
        reconciliation = row["expected_reconciliation"]
        values = [
            row["fixture_id"],
            row["expected_final_state"],
            str(reconciliation["expected_external_effect_count"]),
            "YES" if reconciliation["blind_resend_allowed"] else "NO",
        ]
        colors = [TEXT, TEAL if "BLOCK" not in row["expected_final_state"] else AMBER, TEXT, RED]
        for index, value in enumerate(values):
            draw.text((x[index] + 18, y1 + 18), value, font=F_BODY, fill=colors[index])

    draw.text(
        (86, 820),
        "Local integration fixture · synthetic data · actual BE runtime contract · duplicate case는 기존 execution을 replay",
        font=F_SMALL,
        fill=MUTED,
    )
    image.save(OUT / "05-digital-asset-six-case-matrix.png", "PNG", optimize=True)


ai_tradeoff()
digital_asset_cases()
