"""Find, import, check and fake (placeholder) panel images."""
import shutil
from pathlib import Path
from PIL import Image, ImageDraw
from .common import natural_key, die, ep_dir, load_config, load_scenes, load_characters, frame_size, validate_scenes
from .prompts import needs_image, image_name

EXTS = [".png", ".jpg", ".jpeg", ".webp"]


def find_image(epdir, scene):
    img_dir = Path(epdir) / "images"
    if scene.get("image"):
        p = img_dir / scene["image"]
        if p.exists():
            return p
        stem = Path(scene["image"]).stem
    else:
        stem = f"scene_{int(scene['id']):03d}"
    for e in EXTS:
        p = img_dir / (stem + e)
        if p.exists():
            return p
    return None


def cmd_import(project, ep, src, by="name", move=False, dry=False):
    epd = ep_dir(project, ep)
    _, scenes = load_scenes(epd)
    todo = [s for s in scenes if needs_image(s)]
    srcp = Path(src)
    if not srcp.is_dir():
        die(f"folder not found: {src}")
    files = [p for p in srcp.iterdir() if p.suffix.lower() in EXTS]
    files.sort(key=(lambda p: p.stat().st_mtime) if by == "mtime" else (lambda p: natural_key(p.name)))
    if len(files) != len(todo):
        print(f"note: {len(files)} images found in '{src}' but {len(todo)} scenes need one.")
    (epd / "images").mkdir(parents=True, exist_ok=True)
    for s, f in zip(todo, files):
        dst = epd / "images" / f"scene_{int(s['id']):03d}{f.suffix.lower()}"
        print(f"  {f.name} -> {dst.name}")
        if not dry:
            (shutil.move if move else shutil.copy2)(f, dst)
    if len(files) < len(todo):
        miss = [f"scene_{int(s['id']):03d}" for s in todo[len(files):]]
        print(f"still missing: {', '.join(miss)}")
    if dry:
        print("(dry run, nothing copied)")


def cmd_check(project, ep):
    cfg = load_config(project)
    epd = ep_dir(project, ep)
    meta, scenes = load_scenes(epd)
    W, H = frame_size(cfg)
    errs, warns = validate_scenes(scenes, load_characters(project))
    missing, odd, small = [], [], []
    for s in scenes:
        p = find_image(epd, s)
        if not p:
            missing.append(image_name(s))
            continue
        try:
            with Image.open(p) as im:
                w, h = im.size
        except Exception as e:
            errs.append(f"{p.name}: cannot open ({e})")
            continue
        if min(w, h) < 720:
            small.append(f"{p.name} is {w}x{h}")
        ar, tar = w / h, W / H
        if not s.get("tall") and (ar / tar > 1.6 or ar / tar < 0.62):
            odd.append(f"{p.name} is {w}x{h} (frame is {cfg['aspect']}); it will be cropped/zoomed a lot")
    for e in errs:
        print("ERROR  ", e)
    for w in warns + small + odd:
        print("warning", w)
    if missing:
        print(f"MISSING {len(missing)} image(s): " + ", ".join(missing))
    print(f"{len(scenes) - len(missing)}/{len(scenes)} scenes have images." + ("  Ready to render." if not missing and not errs else ""))
    return not missing and not errs


def make_placeholder(scene, W, H, path):
    import colorsys
    import numpy as np
    hue = (int(scene["id"]) * 47) % 360
    top = np.array(colorsys.hsv_to_rgb(hue / 360, 0.55, 0.70)) * 255
    bot = np.array(colorsys.hsv_to_rgb(hue / 360, 0.55, 0.25)) * 255
    t = np.linspace(0, 1, H)[:, None, None]
    arr = (top * (1 - t) + bot * t) * np.ones((1, W, 1))
    img = Image.fromarray(arr.astype("uint8"), "RGB")
    d = ImageDraw.Draw(img)
    from .render import get_font
    f = get_font("", int(W * 0.22))
    d.text((W / 2, H * 0.40), str(scene["id"]), font=f, fill=(255, 255, 255), anchor="mm")
    f2 = get_font("", int(W * 0.04))
    d.text((W / 2, H * 0.55), "PLACEHOLDER", font=f2, fill=(255, 255, 255), anchor="mm")
    d.rectangle([W * 0.1, H * 0.1, W * 0.9, H * 0.9], outline=(255, 255, 255), width=3)
    img.save(path)
    return path
