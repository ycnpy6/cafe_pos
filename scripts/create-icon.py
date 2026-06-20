from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Pillow is required: pip install pillow") from exc

source = Path("src/main/resources/com/cafepos/images/commongrounds.png")
output = Path("src/main/resources/com/cafepos/images/icon.ico")

if not source.exists():
    raise SystemExit(f"Missing source image: {source}")

sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]

image = Image.open(source).convert("RGBA")
image.save(output, format="ICO", sizes=sizes)

print(f"Wrote {output}")
