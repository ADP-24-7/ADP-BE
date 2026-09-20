from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
BASE = ROOT / "financial-privacy-gateway-series.png"
FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"

POSTS = [
    ("01", "문제와 시스템 지도", "금융 데이터는 API 호출 직전에야 위험해지는 것이 아니다"),
    ("02", "Evidence to Policy", "규정과 Runtime 정책 사이에 계약을 둔 이유"),
    ("03", "Runtime Decision", "ALLOW보다 느슨해지지 않는 Runtime Decision"),
    ("04", "Transform & Egress", "원문이 신뢰 경계를 넘지 않게 만드는 방어선"),
    ("05", "Two Execution Packs", "하나의 Gateway, 두 개의 실행 Pack"),
    ("06", "Recovery", "SENT_UNKNOWN에서는 재시도보다 조회가 먼저다"),
    ("07", "Policy Lifecycle", "Shadow와 Maker-Checker로 정책 활성화하기"),
    ("08", "Verification Boundary", "구현과 운영 검증 사이의 경계"),
]


def font(size, bold=False):
    return ImageFont.truetype(FONT_PATH, size=size, index=7 if bold else 0)


def cover(number, kicker, title):
    im = Image.open(BASE).convert("RGBA")
    w, h = im.size
    overlay = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    d.rectangle((0, 0, int(w * 0.62), h), fill=(5, 18, 36, 224))
    for x in range(int(w * 0.62), int(w * 0.8)):
        alpha = int(224 * (1 - (x - w * 0.62) / (w * 0.18)))
        d.line((x, 0, x, h), fill=(5, 18, 36, max(0, alpha)))
    im = Image.alpha_composite(im, overlay)
    d = ImageDraw.Draw(im)
    margin = int(w * 0.06)
    d.rounded_rectangle((margin, 80, margin + 108, 188), radius=26, fill="#34D3C2")
    d.text((margin + 24, 100), number, font=font(48, True), fill="#071426")
    d.text((margin, 240), kicker.upper(), font=font(30, True), fill="#6FE6D8")
    max_width = int(w * 0.5)
    words = title.split()
    lines, current = [], ""
    for word in words:
        trial = f"{current} {word}".strip()
        if d.textbbox((0, 0), trial, font=font(52, True))[2] <= max_width:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    y = 310
    for line in lines[:3]:
        d.text((margin, y), line, font=font(52, True), fill="#F5F8FD")
        y += 72
    d.line((margin, h - 125, margin + 430, h - 125), fill="#4F8CFF", width=5)
    d.text((margin, h - 95), "FINANCIAL PRIVACY GATEWAY", font=font(24, True), fill="#B2C3D8")
    im.convert("RGB").save(ROOT / f"{number}-post-cover.png", "PNG", optimize=True)


for item in POSTS:
    cover(*item)
