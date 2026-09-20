from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
W, H = 1600, 900
BG = "#081426"
PANEL = "#11243D"
PANEL_2 = "#173250"
BLUE = "#4F8CFF"
TEAL = "#34D3C2"
AMBER = "#F4B860"
RED = "#FF7285"
TEXT = "#F3F7FC"
MUTED = "#9DB0C7"
LINE = "#294766"


def font(size: int, bold: bool = False):
    paths = [
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    ]
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size, index=7 if bold and "AppleSD" in path else 0)
    return ImageFont.load_default()


F_TITLE = font(46, True)
F_SUB = font(24)
F_HEAD = font(28, True)
F_BODY = font(22)
F_SMALL = font(18)


def canvas(title, subtitle):
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    d.text((72, 54), title, font=F_TITLE, fill=TEXT)
    d.text((74, 118), subtitle, font=F_SUB, fill=MUTED)
    d.line((72, 168, W - 72, 168), fill=LINE, width=2)
    return im, d


def box(d, xy, title, body=None, accent=BLUE, fill=PANEL, radius=22, center=False):
    x1, y1, x2, y2 = xy
    d.rounded_rectangle(xy, radius=radius, fill=fill, outline=LINE, width=2)
    d.rounded_rectangle((x1, y1, x1 + 9, y2), radius=4, fill=accent)
    if center:
        bb = d.textbbox((0, 0), title, font=F_HEAD)
        d.text(((x1 + x2 - (bb[2] - bb[0])) / 2, y1 + 24), title, font=F_HEAD, fill=TEXT)
    else:
        d.text((x1 + 28, y1 + 22), title, font=F_HEAD, fill=TEXT)
    if body:
        lines = body if isinstance(body, list) else [body]
        y = y1 + 70
        for line in lines:
            d.text((x1 + 28, y), line, font=F_BODY, fill=MUTED)
            y += 34


def arrow(d, start, end, color=BLUE, width=5):
    d.line((*start, *end), fill=color, width=width)
    x2, y2 = end
    if abs(end[0] - start[0]) >= abs(end[1] - start[1]):
        s = 1 if end[0] > start[0] else -1
        pts = [(x2, y2), (x2 - 16 * s, y2 - 10), (x2 - 16 * s, y2 + 10)]
    else:
        s = 1 if end[1] > start[1] else -1
        pts = [(x2, y2), (x2 - 10, y2 - 16 * s), (x2 + 10, y2 - 16 * s)]
    d.polygon(pts, fill=color)


def footer(d, text="ADP · Financial Privacy Gateway"):
    d.text((72, 850), text, font=F_SMALL, fill="#607A98")


def save(im, name):
    im.save(OUT / name, "PNG", optimize=True)


def architecture():
    im, d = canvas("외부 연결 전 통제하는 네 개의 Plane", "Evidence에서 실행과 복구까지 — 다섯 저장소가 나눠 맡는 하나의 흐름")
    planes = [
        ("Governance Control Plane", "Evidence · Policy · Approval · Lifecycle", BLUE),
        ("Runtime Data Plane", "Minimum Retrieval · Decision · Transform · Egress", TEAL),
        ("Evaluation Evidence Plane", "Dataset · Experiment · Metric · Artifact", AMBER),
        ("Audit Operations Plane", "Trace · Monitoring · Incident · Recovery", RED),
    ]
    y = 220
    for title, body, color in planes:
        box(d, (150, y, 1010, y + 120), title, body, color)
        y += 145
    repos = [
        ("ADP-DA", "Evaluation", AMBER),
        ("ADP-BE", "Runtime", BLUE),
        ("ADP-FE", "Operations UI", TEAL),
        ("ADP-Infra", "Deployment", RED),
        ("ADP-Docs", "Knowledge", "#A78BFA"),
    ]
    y = 220
    for name, role, color in repos:
        box(d, (1120, y, 1450, y + 90), name, role, accent=color)
        y += 112
    d.text((1045, 790), "Repository ownership", font=F_SMALL, fill=MUTED)
    footer(d)
    save(im, "01-four-planes-repositories.png")


def lineage():
    im, d = canvas("규정 문장에서 실행 정책까지", "근거를 곧바로 ALLOW/BLOCK으로 바꾸지 않고 추적 가능한 계약으로 연결한다")
    steps = [
        ("Official Source", "법령 · 감독규정 · 가이드", "#8AA4C5"),
        ("Evidence", "원문 위치 · 버전 · digest", AMBER),
        ("Requirement", "적용 조건과 의무", BLUE),
        ("Control / Test", "검증 가능한 통제", TEAL),
        ("Policy Candidate", "아직 실행되지 않는 후보", "#A78BFA"),
        ("Active Snapshot", "승인·선택된 Runtime 기준", RED),
    ]
    x = 70
    for i, (title, body, color) in enumerate(steps):
        box(d, (x, 315, x + 220, 500), title, [body], color)
        if i < len(steps) - 1:
            arrow(d, (x + 220, 408), (x + 250, 408), color)
        x += 250
    d.rounded_rectangle((160, 590, 1440, 725), radius=22, fill="#0D1D32", outline=LINE, width=2)
    d.text((200, 620), "경계", font=F_HEAD, fill=AMBER)
    d.text((310, 622), "REFERENCE_ONLY ≠ Runtime 허용 근거", font=F_HEAD, fill=TEXT)
    d.text((310, 670), "Schema · identity · version · canonical digest 불일치 시 fail closed", font=F_BODY, fill=MUTED)
    footer(d)
    save(im, "02-evidence-lineage.png")


def runtime_sequence():
    im, d = canvas("요청 한 건이 외부로 나가기까지", "권한 확인 전 조회하지 않고, 실행 시작 시점의 정책과 목적지를 고정한다")
    steps = [
        ("01", "AuthN / AuthZ", "기관 · 역할 · 업무 · 목적", BLUE),
        ("02", "Minimum Retrieval", "Profile · field allowlist", TEAL),
        ("03", "Canonical Context", "원문 대신 metadata · digest", AMBER),
        ("04", "Snapshot Decision", "policy_action → final_action", "#A78BFA"),
        ("05", "Transform / Vault", "privacy-safe payload", TEAL),
        ("06", "Outbound Guard", "destination · schema · secret", RED),
        ("07", "Connector", "AI / Digital Asset", BLUE),
        ("08", "Audit / Recovery", "evidence · reconciliation", AMBER),
    ]
    for i, (n, title, body, color) in enumerate(steps):
        row = i // 4
        col = i % 4 if row == 0 else 3 - (i % 4)
        x = 80 + col * 380
        y = 235 + row * 270
        d.ellipse((x, y, x + 56, y + 56), fill=color)
        d.text((x + 14, y + 12), n, font=F_SMALL, fill=BG)
        box(d, (x + 70, y - 12, x + 340, y + 155), title, body, color)
        if row == 0 and col < 3:
            arrow(d, (x + 340, y + 70), (x + 380, y + 70), color, 4)
        if row == 1 and col > 0:
            arrow(d, (x + 70, y + 70), (x + 30, y + 70), color, 4)
        if col == 3 and row == 0:
            arrow(d, (x + 205, y + 155), (x + 205, y + 255), color, 4)
    footer(d)
    save(im, "03-runtime-sequence.png")


def boundary():
    im, d = canvas("원문이 경계를 넘지 않게 만드는 방어선", "MASK 하나가 아니라 조회·변환·목적지·응답을 연속된 통제로 묶는다")
    zones = [
        ("Trusted Data Zone", "Predefined query\nField allowlist", BLUE),
        ("Canonical Zone", "Data class\nValue digest", AMBER),
        ("Privacy Transform", "MASK · HMAC\nVAULT · REMOVE", TEAL),
        ("Egress Boundary", "Server-owned destination\nSchema · secret guard", RED),
        ("External Provider", "AI · SaaS\nDigital Asset", "#A78BFA"),
    ]
    x = 60
    for i, (title, body, color) in enumerate(zones):
        box(d, (x, 295, x + 265, 535), title, body.split("\n"), color)
        if i < len(zones) - 1:
            arrow(d, (x + 265, 415), (x + 300, 415), color)
        x += 300
    d.text((125, 620), "Response Guard", font=F_HEAD, fill=RED)
    arrow(d, (1390, 630), (350, 630), RED, 5)
    d.text((455, 660), "Provider 응답의 PII · Secret 재노출 검사 → Controlled Delivery", font=F_BODY, fill=MUTED)
    footer(d)
    save(im, "04-transform-egress-boundary.png")


def pack_comparison():
    im, d = canvas("하나의 Gateway, 서로 다른 두 실행 Pack", "공통 통제 spine은 재사용하고 완료 조건과 외부 Evidence는 도메인별로 분리한다")
    box(d, (110, 230, 1490, 355), "Common Gateway Spine", "Auth · Retrieval · Snapshot · Decision · Transform · Egress · Audit · Recovery", BLUE, center=True)
    arrow(d, (500, 355), (500, 445), TEAL)
    arrow(d, (1100, 355), (1100, 445), AMBER)
    box(d, (120, 445, 760, 750), "AI Pack", ["서버 소유 Model Profile", "Evaluation Run / Case 고정", "Latency · Token · Failure Evidence", "Response finding과 Controlled Delivery"], TEAL)
    box(d, (840, 445, 1480, 750), "Digital Asset Pack", ["Approved Transaction 재검증", "6개 PRE_EXECUTION Control", "Receipt · Finality · Transfer Evidence", "SENT_UNKNOWN reconciliation"], AMBER)
    footer(d)
    save(im, "05-ai-digital-asset-packs.png")


def recovery():
    im, d = canvas("SENT_UNKNOWN에서는 재시도보다 조회가 먼저다", "외부 효과가 있었는지 모르는 상태를 실패로 단정하지 않고 Evidence로 수렴시킨다")
    events = [
        ("T0", "요청·예약", BLUE), ("T1", "Guard 통과", TEAL), ("T2", "Provider 전송", BLUE),
        ("T3", "응답 유실", RED), ("T4", "SENT_UNKNOWN", AMBER), ("T5", "Status Query", TEAL),
        ("T6", "독립 Evidence", BLUE), ("T7", "원자적 수렴", "#A78BFA")
    ]
    y = 420
    d.line((110, y, 1490, y), fill=LINE, width=8)
    for i, (time, label, color) in enumerate(events):
        x = 120 + i * 190
        d.ellipse((x - 18, y - 18, x + 18, y + 18), fill=color)
        d.text((x - 20, y - 85), time, font=F_HEAD, fill=color)
        d.text((x - 70, y + 45), label, font=F_BODY, fill=TEXT)
    d.rounded_rectangle((310, 650, 1290, 760), radius=20, fill=PANEL, outline=AMBER, width=3)
    d.text((350, 676), "허용되는 다음 행동", font=F_HEAD, fill=AMBER)
    d.text((650, 680), "blind resend 금지  →  provider status 확인  →  evidence 검증", font=F_BODY, fill=TEXT)
    footer(d)
    save(im, "06-sent-unknown-recovery.png")


def lifecycle():
    im, d = canvas("정책은 코드와 다른 생명주기를 가진다", "Replay와 Shadow Evidence를 통과한 후보만 별도 Maker-Checker 승인 후 활성화한다")
    stages = [
        ("DRAFT", "#8AA4C5"), ("VALIDATED", BLUE), ("CANDIDATE", AMBER), ("REPLAY", TEAL),
        ("SHADOW", "#A78BFA"), ("APPROVED", BLUE), ("ACTIVE", TEAL)
    ]
    x = 55
    for i, (stage, color) in enumerate(stages):
        box(d, (x, 330, x + 185, 435), stage, accent=color, center=True)
        if i < len(stages) - 1:
            arrow(d, (x + 185, 382), (x + 213, 382), color, 4)
        x += 213
    box(d, (340, 560, 730, 735), "Maker", ["Candidate 생성", "Shadow 실행 요청"], AMBER)
    box(d, (870, 560, 1260, 735), "Checker", ["Evidence 검토", "승인 · 활성화"], BLUE)
    arrow(d, (730, 650), (870, 650), TEAL)
    d.text((610, 770), "optimistic revision으로 동시 전이와 stale command 차단", font=F_BODY, fill=MUTED)
    footer(d)
    save(im, "07-policy-lifecycle.png")


def verification():
    im, d = canvas("구현했다는 말과 운영 검증했다는 말 사이", "Claim을 다섯 단계로 나눠 현재 증거보다 앞서 말하지 않는다")
    rows = [
        ("IMPLEMENTED", "코드와 자동 테스트", BLUE),
        ("LOCAL_VERIFIED", "고정된 로컬 통합 E2E", TEAL),
        ("QA_LIMITED", "NCP QA 일부 자원·경로", AMBER),
        ("DESIGN_ONLY", "문서와 계약으로 정의", "#A78BFA"),
        ("UNVERIFIED", "Production · HA · DR 등", RED),
    ]
    y = 230
    for label, desc, color in rows:
        d.rounded_rectangle((190, y, 1410, y + 95), radius=18, fill=PANEL, outline=LINE, width=2)
        d.rounded_rectangle((215, y + 18, 535, y + 77), radius=14, fill=color)
        d.text((245, y + 31), label, font=F_BODY, fill=BG)
        d.text((600, y + 30), desc, font=F_HEAD, fill=TEXT)
        y += 115
    footer(d, "검증 범위는 기능 수보다 중요한 시스템 설명의 일부다")
    save(im, "08-verification-claims.png")


def ci_security_pipeline():
    im, d = canvas("검증 Pipeline은 서로 다른 실패를 막는다", "기능 검증과 보안·공급망 검사를 분리하고, 재현할 계약은 commit SHA로 고정한다")

    d.text((82, 220), "CI", font=F_HEAD, fill=BLUE)
    ci_steps = [
        ("Compose", "config"),
        ("Integration", "profiles"),
        ("Prometheus", "rules"),
        ("JUnit", "PostgreSQL"),
        ("Negative", "matrix"),
        ("bootJar", "package"),
    ]
    x = 170
    for index, (title, body) in enumerate(ci_steps):
        box(d, (x, 200, x + 190, 320), title, body, BLUE if index < 3 else TEAL)
        if index < len(ci_steps) - 1:
            arrow(d, (x + 190, 260), (x + 214, 260), BLUE, 3)
        x += 214

    d.text((82, 365), "Security Gate", font=F_HEAD, fill=RED)
    security_steps = [
        ("Gitleaks", "history"),
        ("CodeQL", "Java/Kotlin"),
        ("Trivy FS", "vuln · config"),
        ("Image", "build"),
        ("CycloneDX", "SBOM"),
        ("Trivy Image", "HIGH · CRITICAL"),
    ]
    x = 170
    for index, (title, body) in enumerate(security_steps):
        box(d, (x, 410, x + 190, 530), title, body, RED if index in (0, 1, 5) else AMBER)
        if index < len(security_steps) - 1:
            arrow(d, (x + 190, 470), (x + 214, 470), RED, 3)
        x += 214

    d.rounded_rectangle((170, 650, 1430, 775), radius=20, fill=PANEL, outline=LINE, width=2)
    d.text((205, 675), "두 종류의 고정", font=F_HEAD, fill=TEXT)
    d.text((475, 676), "Repository baseline lock", font=F_BODY, fill=TEAL)
    d.text((475, 716), "로컬 통합 조합 전체를 재현", font=F_SMALL, fill=MUTED)
    d.text((930, 676), "CI producer / fixture pin", font=F_BODY, fill=AMBER)
    d.text((930, 716), "검증 대상 계약만 freeze", font=F_SMALL, fill=MUTED)
    footer(d, "Production 운영 검증은 이 pipeline의 범위 밖이다")
    save(im, "09-ci-security-pipeline.png")


for render in [architecture, lineage, runtime_sequence, boundary, pack_comparison, recovery, lifecycle, verification, ci_security_pipeline]:
    render()
