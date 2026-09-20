import base64
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
WORKSPACE = OUT.parents[4]

FIGURES = [
    {
        "source": WORKSPACE / "ADP-DA/02_ai/notebooks/03_evaluation_ai_transform_tradeoff_v1.ipynb",
        "sha256": "455ae2db20e82d7e5e1df9a1bec9a99acc87bc902c85ec2079673adf76ebcd04",
        "cell": 37,
        "output": "01-transform-relationship-utility.png",
    },
    {
        "source": WORKSPACE / "ADP-DA/03_digital_asset/notebooks/runtime_validation/DA_04_outbound_destination.ipynb",
        "sha256": "f8aec16bb0e6b18abf16e128205baca89e4ccdc63047edd02c75d813414e39ba",
        "cell": 12,
        "output": "02-destination-field-minimization.png",
    },
    {
        "source": WORKSPACE / "ADP-DA/03_digital_asset/notebooks/runtime_validation/DA_06_recovery_idempotency.ipynb",
        "sha256": "630010c2452a5a8cfb2d85b6cd3fe102c57dce440262d3b17525bdcb2fd962a3",
        "cell": 9,
        "output": "03-recovery-strategy-risk.png",
    },
    {
        "source": WORKSPACE / "ADP-DA/03_digital_asset/notebooks/runtime_validation/DA_02_exact_preservation.ipynb",
        "sha256": "b79e617ba350d90d437548a91ba910ff67f021908d8ebaf830920b014e6bc5fa",
        "cell": 16,
        "output": "06-digital-asset-amount-precision.png",
    },
    {
        "source": WORKSPACE / "ADP-DA/03_digital_asset/notebooks/runtime_validation/DA_03_trace_binding.ipynb",
        "sha256": "aae7be8a7c29a43316f8e5ceffc1e5ad680568e3e07787427a0a98ed182dc57b",
        "cell": 29,
        "output": "07-zero-value-movement-evidence.png",
    },
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def extract_png(notebook_path: Path, cell_index: int) -> bytes:
    notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
    outputs = notebook["cells"][cell_index].get("outputs", [])
    images = [output.get("data", {}).get("image/png") for output in outputs]
    images = [image for image in images if image]
    if len(images) != 1:
        raise RuntimeError(
            f"expected one PNG in {notebook_path} cell {cell_index}, found {len(images)}"
        )
    payload = images[0]
    if isinstance(payload, list):
        payload = "".join(payload)
    return base64.b64decode(payload)


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


def add_counterfactual_notice(path: Path) -> None:
    image = Image.open(path).convert("RGB")
    draw = ImageDraw.Draw(image, "RGBA")
    notice = "Counterfactual experiment · 실제 timeout 발생률/중복률 아님"
    label_font = font(max(13, image.width // 70), True)
    bbox = draw.textbbox((0, 0), notice, font=label_font)
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = max(24, image.width - width - 26)
    y = 56
    draw.rounded_rectangle(
        (x - 12, y - 6, x + width + 12, y + height + 9),
        radius=9,
        fill=(8, 20, 38, 225),
        outline=(244, 184, 96, 255),
        width=2,
    )
    draw.text((x, y), notice, font=label_font, fill=(255, 244, 220, 255))
    image.save(path, "PNG", optimize=True)


for figure in FIGURES:
    source = figure["source"]
    actual_hash = sha256(source)
    if actual_hash != figure["sha256"]:
        raise RuntimeError(
            f"source notebook changed: {source}\n"
            f"expected {figure['sha256']}\nactual   {actual_hash}"
        )
    target = OUT / figure["output"]
    target.write_bytes(extract_png(source, figure["cell"]))
    if figure["output"] == "03-recovery-strategy-risk.png":
        add_counterfactual_notice(target)
    print(f"wrote {target.relative_to(WORKSPACE)}")
