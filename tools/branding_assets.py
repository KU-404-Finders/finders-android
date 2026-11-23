"""Deterministic conversion of the supplied wordmark/icon; no drawing or font replacement.

Run with py -3.13 tools/branding_assets.py --input <directory>
Pillow is required. Generated QA files live under ignored build/branding, outside app sources.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import shutil
import subprocess
import zipfile

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
QA = ROOT / "build/branding"
BACKUP_REF = "backup/before-text-logo-20261123"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def pixel_sha(im):
    visible = Image.alpha_composite(Image.new("RGBA", im.size, "white"), im.convert("RGBA")).convert("RGB")
    return sha(str(im.size).encode() + visible.tobytes())


def baseline_manifest():
    paths = subprocess.check_output(
        ["git", "ls-tree", "-r", "--name-only", BACKUP_REF, "--", "app/src/main"], cwd=ROOT,
    ).decode().splitlines()
    result = []
    for path in paths:
        if not ("ic_launcher" in path or "img_lost_and_found_ku" in path):
            continue
        if Path(path).suffix not in (".png", ".webp"):
            continue
        data = subprocess.check_output(["git", "show", f"{BACKUP_REF}:{path}"], cwd=ROOT)
        with Image.open(io.BytesIO(data)) as im:
            result.append({"path": path, "sha256": sha(data), "pixel_sha256": pixel_sha(im)})
    return result


def unmatte(icon, background):
    """Recover the existing white lettering's alpha from its solid supplied background."""
    foreground = Image.new("RGBA", icon.size)
    bbox = ImageChops.difference(icon, Image.new("RGB", icon.size, background)).getbbox()
    pixels = []
    for rgb in icon.crop(bbox).getdata():
        alpha = max(0.0, *(max(0, c - b) / (255 - b) for c, b in zip(rgb, background)))
        if alpha == 0:
            pixels.append((0, 0, 0, 0))
        else:
            color = tuple(max(0, min(255, round((c - b * (1 - alpha)) / alpha))) for c, b in zip(rgb, background))
            pixels.append((*color, round(alpha * 255)))
    crop = Image.new("RGBA", (bbox[2] - bbox[0], bbox[3] - bbox[1]))
    crop.putdata(pixels)
    foreground.paste(crop, bbox[:2])
    reconstructed = Image.alpha_composite(Image.new("RGBA", icon.size, (*background, 255)), foreground).convert("RGB")
    assert max(high for low, high in ImageChops.difference(icon, reconstructed).getextrema()) <= 1
    return foreground


def mask_image(size, shape):
    scale = 4
    mask = Image.new("L", (size * scale, size * scale))
    draw = ImageDraw.Draw(mask)
    box = (0, 0, mask.width - 1, mask.height - 1)
    if shape == "circle":
        draw.ellipse(box, fill=255)
    else:
        draw.rounded_rectangle(box, radius=size * scale * .22, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def generate(input_dir):
    QA.mkdir(parents=True, exist_ok=True)
    baseline = baseline_manifest()
    (QA / "baseline-assets.json").write_text(json.dumps(baseline, indent=2), encoding="utf-8")
    icon = Image.open(input_dir / "app_icon_green_1024.png").convert("RGB")
    assert icon.size == (1024, 1024)
    background = icon.getpixel((0, 0))
    assert background == (16, 72, 40), "The supplied source color changed; review before regenerating."
    glyph = unmatte(icon, background)
    source_wordmark = input_dir / "wordmark_green_transparent.png"
    target_wordmark = RES / "drawable-nodpi/img_finders_wordmark.png"
    target_wordmark.parent.mkdir(exist_ok=True)
    shutil.copyfile(source_wordmark, target_wordmark)
    assert sha(source_wordmark.read_bytes()) == sha(target_wordmark.read_bytes())
    foreground_bounds = {}
    for density, factor in DENSITIES.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(exist_ok=True)
        legacy_size = round(48 * factor)
        legacy = icon.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
        legacy.save(directory / "ic_launcher.webp", lossless=True, exact=True)
        rounded = legacy.convert("RGBA")
        rounded.putalpha(mask_image(legacy_size, "circle"))
        rounded.save(directory / "ic_launcher_round.webp", lossless=True, exact=True)
        # A 108dp layer containing a 72dp version of the full source icon matches
        # the iOS lettering proportion when Android displays its central 72dp viewport.
        size, inner_size = round(108 * factor), round(72 * factor)
        layer = Image.new("RGBA", (size, size))
        inner = glyph.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
        layer.paste(inner, ((size - inner_size) // 2,) * 2)
        alpha = layer.getchannel("A")
        for y in range(size):
            for x in range(size):
                if alpha.getpixel((x, y)):
                    assert math.hypot(x + .5 - size / 2, y + .5 - size / 2) < 33 * factor
        foreground_bounds[density] = alpha.getbbox()
        layer.save(directory / "ic_launcher_foreground.webp", lossless=True, exact=True)
    icon.resize((512, 512), Image.Resampling.LANCZOS).save(ROOT / "app/src/main/ic_launcher-playstore.png")
    # These original resources are recoverable from the user's existing Git/ZIP backups.
    for relative in ("drawable/img_lost_and_found_ku.png", "drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_background.xml"):
        target = (RES / relative).resolve()
        assert target.is_relative_to(RES.resolve())
        target.unlink(missing_ok=True)
    (QA / "original-assets.png").unlink(missing_ok=True)
    report = {"background": "#104828", "source_wordmark_sha256": sha(source_wordmark.read_bytes()),
              "source_icon_sha256": sha((input_dir / "app_icon_green_1024.png").read_bytes()),
              "foreground_canvas_dp": 108, "source_icon_extent_dp": 72,
              "safe_circle_diameter_dp": 66, "foreground_alpha_bounds": foreground_bounds,
              "unmatte_recomposition_max_channel_error": 1}
    (QA / "asset-validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    preview = Image.new("RGB", (960, 410), "#eeeeee")
    draw = ImageDraw.Draw(preview)
    for i, shape in enumerate(("square", "circle", "rounded")):
        sample = icon.resize((240, 240), Image.Resampling.LANCZOS).convert("RGBA")
        if shape != "square":
            sample.putalpha(mask_image(240, shape))
        preview.paste(sample, (i * 320 + 40, 30), sample)
        draw.text((i * 320 + 40, 280), shape, fill="black")
    wordmark = Image.open(source_wordmark).convert("RGBA")
    wordmark.thumbnail((300, 78), Image.Resampling.LANCZOS)
    preview.paste("white", (0, 310, 960, 410))
    preview.paste(wordmark, ((960 - wordmark.width) // 2, 325), wordmark)
    preview.save(QA / "text-branding-preview.png")
    print(json.dumps(report, indent=2))


def audit_packages(packages):
    baseline = baseline_manifest()
    (QA / "baseline-assets.json").write_text(json.dumps(baseline, indent=2), encoding="utf-8")
    old_bytes = {entry["sha256"] for entry in baseline}
    old_pixels = {entry["pixel_sha256"] for entry in baseline}
    results = []
    for package in packages:
        checked = 0
        with zipfile.ZipFile(package) as archive:
            for entry in archive.namelist():
                assert "img_lost_and_found_ku" not in entry, entry
                if not entry.lower().endswith((".png", ".webp", ".jpg", ".jpeg")):
                    continue
                data = archive.read(entry)
                assert sha(data) not in old_bytes, (package, entry, "original file retained")
                with Image.open(io.BytesIO(data)) as im:
                    assert pixel_sha(im) not in old_pixels, (package, entry, "original image pixels retained")
                checked += 1
            for entry in ("resources.arsc", "base/resources.pb"):
                if entry in archive.namelist():
                    data = archive.read(entry)
                    assert b"img_lost_and_found_ku" not in data
                    assert "img_lost_and_found_ku".encode("utf-16-le") not in data
        results.append({"package": str(package), "images_checked": checked, "old_mascot_matches": 0})
    (QA / "package-validation.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("C:/Users/Administrator/Desktop/finders_branding_input"))
    parser.add_argument("--audit", nargs="+", type=Path)
    args = parser.parse_args()
    if args.audit:
        audit_packages(args.audit)
    else:
        generate(args.input)
