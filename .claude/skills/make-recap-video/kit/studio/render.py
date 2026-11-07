"""Frame renderer (Ken-Burns camera, captions, overlays, fx) + segment cache + final mux."""
import math, os, re, subprocess, sys, shutil
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from .common import (KIT, die, load_config, load_characters, load_scenes, ep_dir, frame_size, sha, need_ffmpeg,
                     validate_scenes, ts, save_json, probe_duration)
from .images import find_image, make_placeholder
from . import tts, audio

FONT_CANDIDATES = [
    KIT / "fonts" / "Anton-Regular.ttf",
    "C:/Windows/Fonts/impact.ttf", "C:/Windows/Fonts/arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Impact.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
]


@lru_cache(maxsize=64)
def get_font(path, size):
    size = int(size)
    for cand in ([path] if path else []) + [str(c) for c in sorted(KIT.glob("fonts/*.ttf"))] + [str(c) for c in FONT_CANDIDATES]:
        try:
            if cand and Path(cand).exists():
                return ImageFont.truetype(str(cand), size)
        except Exception:
            continue
    try:
        return ImageFont.load_default(size)
    except TypeError:
        return ImageFont.load_default()


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- camera
def camera(kind, p, iw, ih, W, H, zmax, focus):
    s0 = max(W / iw, H / ih)
    e = p * p * (3 - 2 * p)
    fx, fy = focus
    icx, icy = iw / 2, ih / 2

    def win(z):
        return W / (s0 * z), H / (s0 * z)

    lerp = lambda a, b, t: a + (b - a) * t
    if kind in ("zoom_in", "zoom_out"):
        q = e if kind == "zoom_in" else 1 - e
        z = 1 + zmax * q
        ww, wh = win(z)
        cx, cy = lerp(icx, fx * iw, q), lerp(icy, fy * ih, q)
    elif kind.startswith("pan_"):
        z = 1 + max(zmax, 0.10)
        ww, wh = win(z)
        if kind == "pan_left":
            cx, cy = lerp(iw - ww / 2, ww / 2, e), fy * ih
        elif kind == "pan_right":
            cx, cy = lerp(ww / 2, iw - ww / 2, e), fy * ih
        elif kind == "pan_up":
            cx, cy = fx * iw, lerp(ih - wh / 2, wh / 2, e)
        else:  # pan_down: top -> bottom, the classic webtoon scroll
            cx, cy = fx * iw, lerp(wh / 2, ih - wh / 2, e)
    elif kind == "drift":
        z = 1.06 + 0.02 * p
        ww, wh = win(z)
        cx = ww / 2 + (iw - ww) * (0.35 + 0.30 * e)
        cy = wh / 2 + (ih - wh) * (0.40 + 0.20 * e)
    else:
        z = 1.0
        ww, wh = win(z)
        cx, cy = icx, icy
    return cx, cy, ww, wh


def clamp_cam(cx, cy, ww, wh, iw, ih):
    return min(max(cx, ww / 2), iw - ww / 2), min(max(cy, wh / 2), ih - wh / 2)


def grab(img, cx, cy, ww, wh, W, H):
    x0, y0 = cx - ww / 2, cy - wh / 2
    return img.transform((W, H), Image.AFFINE, (ww / W, 0, x0, 0, wh / H, y0), resample=Image.BICUBIC)


# ---------------------------------------------------------------- overlays
def wrap(draw, text, font, maxw):
    lines, cur = [], ""
    for w in text.split():
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=font) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def make_overlay(ov, W, H, cfg):
    """returns (RGBA layer, (x, y))"""
    if ov["type"] == "system":
        body_f, head_f = get_font("", W * 0.040), get_font("", W * 0.034)
        d0 = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
        pad = int(W * 0.04)
        bw = int(W * 0.82)
        lines = []
        for para in str(ov.get("text", "")).split("\n"):
            lines += wrap(d0, para, body_f, bw - 2 * pad) or [""]
        lh = int(W * 0.052)
        hh = int(W * 0.06)
        bh = pad * 2 + hh + lh * len(lines)
        layer = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.rounded_rectangle([0, 0, bw - 1, bh - 1], radius=int(W * 0.015), fill=(8, 22, 48, 205), outline=(90, 200, 255, 255), width=max(2, int(W * 0.004)))
        d.text((pad, pad), "[ SYSTEM ]", font=head_f, fill=(120, 220, 255, 255))
        d.line([pad, pad + hh - 6, bw - pad, pad + hh - 6], fill=(90, 200, 255, 160), width=2)
        for i, ln in enumerate(lines):
            d.text((pad, pad + hh + i * lh), ln, font=body_f, fill=(235, 245, 255, 255))
        return layer, (int((W - bw) / 2), int(H * 0.16))
    f = get_font(cfg["captions"].get("font", ""), W * 0.13)
    text = str(ov.get("text", "")).upper()
    d0 = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
    tw = int(d0.textlength(text, font=f)) + 40
    if tw > W * 0.95:
        f = get_font(cfg["captions"].get("font", ""), W * 0.13 * (W * 0.95) / tw)
        tw = int(W * 0.95)
    th = int(f.size * 1.5)
    layer = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((tw / 2, th / 2), text, font=f, fill=hexrgb(cfg["captions"]["highlight"]) + (255,), anchor="mm",
           stroke_width=max(3, int(W * 0.008)), stroke_fill=(0, 0, 0, 255))
    return layer, (int((W - tw) / 2), int(H * 0.30))


# ---------------------------------------------------------------- captions
def caption_chunks(narration, start, dur, per):
    words = narration.split()
    if not words:
        return []
    weights = [len(w) + 2 + (4 if re.search(r"[,.;:!?]$", w) else 0) for w in words]
    tot = float(sum(weights))
    t = start
    timed = []
    for w, wt in zip(words, weights):
        d = dur * wt / tot
        timed.append((w, t, t + d))
        t += d
    chunks, cur = [], []
    for item in timed:
        cur.append(item)
        if len(cur) >= per or re.search(r"[.!?]$", item[0]):
            chunks.append(cur)
            cur = []
    if cur:
        chunks.append(cur)
    return chunks


def draw_caption(frame, chunk_words, active, cfg, W, H):
    c = cfg["captions"]
    font = get_font(c.get("font", ""), W * c["size"])
    d = ImageDraw.Draw(frame)
    space = d.textlength(" ", font=font)
    widths = [d.textlength(w.upper(), font=font) for w, *_ in chunk_words]
    total = sum(widths) + space * (len(widths) - 1)
    rows = [list(range(len(widths)))]
    if total > W * 0.9 and len(widths) > 1:
        mid = len(widths) // 2
        rows = [list(range(mid)), list(range(mid, len(widths)))]
    sw = max(3, int(W * 0.0075))
    lh = font.size * 1.18
    y = H * c["position"] - (len(rows) - 1) * lh / 2
    for row in rows:
        rw = sum(widths[i] for i in row) + space * (len(row) - 1)
        x = (W - rw) / 2
        for i in row:
            col = hexrgb(c["highlight"]) if i == active else hexrgb(c["color"])
            d.text((x, y), chunk_words[i][0].upper(), font=font, fill=col, stroke_width=sw, stroke_fill=hexrgb(c["stroke"]), anchor="lm")
            x += widths[i] + space
        y += lh


# ---------------------------------------------------------------- segment
def vignette_mask(W, H, strength):
    yy, xx = np.mgrid[0:H, 0:W].astype("f4")
    r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) / 1.414
    m = np.clip((r - 0.45) / 0.6, 0, 1) ** 1.6 * strength * 255
    return Image.fromarray(m.astype("uint8"), "L")


def render_segment(job):
    cfg, sc, W, H, fps = job["cfg"], job["scene"], job["W"], job["H"], job["fps"]
    n = job["frames"]
    out = job["out"]
    img = Image.open(job["image"]).convert("RGB")
    iw, ih = img.size
    s0 = max(W / iw, H / ih)
    if s0 < 0.66:                          # source far bigger than needed: pre-shrink to ~1.5x cover size (faster, sharper)
        img = img.resize((max(2, int(iw * s0 * 1.5)), max(2, int(ih * s0 * 1.5))), Image.LANCZOS)
        iw, ih = img.size
    kind = sc.get("camera", "drift")
    focus = tuple(sc.get("focus", (0.5, 0.42)))
    zmax = cfg["look"]["zoom"]
    fx_list = sc.get("fx", [])
    black = Image.new("RGB", (W, H), (0, 0, 0))
    white = Image.new("RGB", (W, H), (255, 255, 255))
    vig = vignette_mask(W, H, cfg["look"]["vignette"]) if cfg["look"]["vignette"] > 0 else None
    ov_layer = ov_pos = None
    if sc.get("overlay"):
        ov_layer, ov_pos = make_overlay(sc["overlay"], W, H, cfg)
    chunks = []
    if cfg["captions"]["enabled"] and sc["narration"]:
        chunks = caption_chunks(sc["narration"], job["lead"], job["audio_dur"], cfg["captions"]["words_per_chunk"])
    rng = np.random.default_rng(int(sc["id"]))
    ph = rng.uniform(0, 6.28, 2)

    cmd = ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(fps), "-i", "-",
           "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "17", "-pix_fmt", "yuv420p", "-g", str(fps * 2), "-r", str(fps), out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    try:
        for i in range(n):
            t = i / fps
            p = i / max(1, n - 1)
            cx, cy, ww, wh = camera(kind, p, iw, ih, W, H, zmax, focus)
            if "shake" in fx_list and t < 0.7:
                amp = 0.016 * ww * (1 - t / 0.7)
                cx += amp * math.sin(2 * math.pi * 17 * t + ph[0])
                cy += amp * math.sin(2 * math.pi * 21 * t + ph[1])
            cx, cy = clamp_cam(cx, cy, ww, wh, iw, ih)
            fr = grab(img, cx, cy, ww, wh, W, H)
            if vig is not None:
                fr.paste(black, (0, 0), vig)
            if ov_layer is not None and t > 0.3:
                a = min(1.0, (t - 0.3) / 0.25)
                layer = ov_layer
                pos = ov_pos
                if sc["overlay"]["type"] == "bigtext" and t < 0.55:
                    k = 1.35 - 0.35 * min(1.0, (t - 0.3) / 0.25)
                    layer = ov_layer.resize((int(ov_layer.width * k), int(ov_layer.height * k)), Image.BICUBIC)
                    pos = (int((W - layer.width) / 2), int(ov_pos[1] + (ov_layer.height - layer.height) / 2))
                al = layer.getchannel("A").point(lambda v, a=a: int(v * a))
                fr.paste(layer.convert("RGB"), pos, al)
            if chunks:
                for ch in chunks:
                    if ch[0][1] - 0.02 <= t < ch[-1][2] + 0.12:
                        act = next((k for k, w in enumerate(ch) if t < w[2]), len(ch) - 1)
                        draw_caption(fr, ch, act, cfg, W, H)
                        break
            if "flash" in fx_list and t < 0.25:
                fr = Image.blend(fr, white, 0.85 * (1 - t / 0.25))
            if job["fade_in"] and t < job["fade_in"]:
                fr = Image.blend(black, fr, t / job["fade_in"])
            if job["fade_out"] and (n / fps - t) < job["fade_out"]:
                fr = Image.blend(black, fr, max(0.0, (n / fps - t) / job["fade_out"]))
            proc.stdin.write(fr.tobytes())
        proc.stdin.close()
        if proc.wait() != 0:
            raise RuntimeError("ffmpeg failed")
    except Exception:
        try:
            proc.kill()
        except Exception:
            pass
        raise
    return out


# ---------------------------------------------------------------- orchestration
def parse_range(spec, scenes):
    if not spec:
        return scenes
    ids = set()
    for part in spec.split(","):
        if "-" in part:
            a, b = part.split("-")
            ids |= set(range(int(a), int(b) + 1))
        else:
            ids.add(int(part))
    sel = [s for s in scenes if s["id"] in ids]
    if not sel:
        die(f"no scenes match '{spec}'")
    return sel


def plan_timeline(scenes, voices, cfg):
    fps, t, tl = cfg["fps"], 0.0, []
    tm = cfg["timing"]
    for s in scenes:
        wav, dur = voices.get(s["id"], (None, 0.0))
        if dur > 0:
            length = max(tm["min_scene"], tm["lead"] + dur + tm["tail"])
        else:
            length = float(s.get("hold") or tm["silent_scene"])
        frames = max(1, int(round(length * fps)))
        length = frames / fps
        tl.append({"id": s["id"], "start": t, "length": length, "frames": frames, "lead": tm["lead"], "audio": dur,
                   "sfx": s.get("sfx", []), "chapter": s.get("chapter")})
        t += length
    return tl


def cmd_render(project, ep, scenes_spec=None, scale=1.0, placeholders=False, workers=None, engine=None, force=False):
    need_ffmpeg()
    cfg = load_config(project)
    chars = load_characters(project)
    epd = ep_dir(project, ep)
    meta, allscenes = load_scenes(epd)
    errs, warns = validate_scenes(allscenes, chars)
    if errs:
        die("fix scenes.json first:\n  " + "\n  ".join(errs))
    scenes = parse_range(scenes_spec, allscenes)
    W, H = frame_size(cfg, scale)
    fps = cfg["fps"]

    images, missing = {}, []
    for s in scenes:
        p = find_image(epd, s)
        if p is None:
            if placeholders:
                (epd / "images").mkdir(exist_ok=True)
                p = make_placeholder(s, *frame_size(cfg), epd / "images" / f"_placeholder_{int(s['id']):03d}.png")
            else:
                missing.append(f"scene_{int(s['id']):03d}")
        images[s["id"]] = p
    if missing:
        die(f"missing images: {', '.join(missing)}\n  Upload them (python recap.py import ...) or draft with --placeholders.")

    print(f"1/4 voiceover ({len(scenes)} scenes)...")
    voices = tts.ensure_audio(project, ep, scenes, cfg, engine=engine)
    tl = plan_timeline(scenes, voices, cfg)
    total = tl[-1]["start"] + tl[-1]["length"]
    print(f"    runtime ~ {ts(total)} ({total:.0f}s)")

    build = epd / "build"
    segdir = build / "segments"
    segdir.mkdir(parents=True, exist_ok=True)
    print("2/4 mixing audio (voice + music + sfx)...")
    mix = audio.build_mix(tl, voices, cfg, build / "mix.wav")

    print("3/4 rendering scenes...")
    jobs, seglist = [], []
    for k, (s, it) in enumerate(zip(scenes, tl)):
        img = images[s["id"]]
        st = Path(img).stat()
        key = sha(s, cfg["look"], cfg["captions"], cfg["timing"], W, H, fps, st.st_size, int(st.st_mtime), it["frames"], it["audio"],
                  k == 0, k == len(scenes) - 1, cfg["fade"])
        out = segdir / f"seg_{int(s['id']):03d}_{W}x{H}_{key}.mp4"
        seglist.append(out)
        if out.exists() and not force:
            continue
        jobs.append({"cfg": cfg, "scene": s, "image": str(img), "W": W, "H": H, "fps": fps, "frames": it["frames"],
                     "out": str(out), "lead": it["lead"], "audio_dur": it["audio"],
                     "fade_in": cfg["fade"]["in"] if k == 0 else 0, "fade_out": cfg["fade"]["out"] if k == len(scenes) - 1 else 0})
    cached = len(scenes) - len(jobs)
    if cached:
        print(f"    {cached} scene(s) unchanged, reused from cache")
    if jobs:
        nw = workers or max(1, min(4, (os.cpu_count() or 2) - 1))
        if nw > 1 and len(jobs) > 1:
            import multiprocessing as mp
            with mp.get_context("spawn").Pool(nw) as pool:
                for i, _ in enumerate(pool.imap_unordered(render_segment, jobs), 1):
                    print(f"    rendered {i}/{len(jobs)}", flush=True)
        else:
            for i, j in enumerate(jobs, 1):
                render_segment(j)
                print(f"    rendered {i}/{len(jobs)}", flush=True)

    print("4/4 joining + encoding...")
    lst = build / "segments.txt"
    lst.write_text("".join(f"file '{p.resolve().as_posix()}'\n" for p in seglist), encoding="utf-8")
    slug = Path(project).name
    tag = "preview" if (scenes_spec or scale != 1.0 or placeholders) else "final"
    outdir = epd / "out"
    outdir.mkdir(exist_ok=True)
    outfile = outdir / f"ep{int(ep):02d}_{slug}_{tag}.mp4"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(lst), "-i", str(mix),
                    "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                    "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-shortest", "-movflags", "+faststart", str(outfile)], check=True)
    save_json(build / "timeline.json", [{"id": t["id"], "start": round(t["start"], 2), "length": round(t["length"], 2), "chapter": t["chapter"]} for t in tl])
    print(f"\nDONE  {outfile}  ({W}x{H}, {ts(probe_duration(outfile))}, {outfile.stat().st_size / 1e6:.1f} MB)")
    return outfile


def cmd_still(project, ep, scene_id, p=0.5, out=None, scale=1.0):
    cfg = load_config(project)
    epd = ep_dir(project, ep)
    _, scenes = load_scenes(epd)
    sc = next((s for s in scenes if s["id"] == scene_id), None)
    if not sc:
        die(f"scene {scene_id} not found")
    W, H = frame_size(cfg, scale)
    img_path = find_image(epd, sc) or make_placeholder(sc, W, H, epd / f"_still_placeholder.png")
    voices = {sc["id"]: (None, max(2.0, len(sc["narration"].split()) / 2.6))}
    tl = plan_timeline([sc], voices, cfg)[0]
    out = out or str(epd / "build" / f"still_{int(scene_id):03d}.png")
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    job = {"cfg": cfg, "scene": sc, "image": str(img_path), "W": W, "H": H, "fps": cfg["fps"], "frames": tl["frames"],
           "out": str(Path(out).with_suffix(".mp4")), "lead": tl["lead"], "audio_dur": tl["audio"], "fade_in": 0, "fade_out": 0}
    render_segment(job)
    t = max(0.0, min(p, 1.0)) * (tl["frames"] - 1) / cfg["fps"]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{t:.3f}", "-i", job["out"], "-frames:v", "1", out], check=True)
    os.remove(job["out"])
    print("still ->", out)


def cmd_contact(video, step=10, cols=5):
    need_ffmpeg()
    v = Path(video)
    out = v.with_name(v.stem + "_contact.png")
    dur = probe_duration(v)
    n = max(1, int(math.ceil(dur / step)))
    rows = int(math.ceil(n / cols))
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(v), "-vf", f"fps=1/{step},scale=240:-2,tile={cols}x{rows}", "-frames:v", "1", str(out)], check=True)
    print("contact sheet ->", out)


def cmd_chapters(project, ep):
    tp = ep_dir(project, ep) / "build" / "timeline.json"
    if not tp.exists():
        die("render the episode first (timeline.json is written by render).")
    import json
    tl = json.load(open(tp, encoding="utf-8"))
    rows = [(t["start"], t["chapter"]) for t in tl if t.get("chapter")]
    if not rows:
        print("No scene has a 'chapter' field. Add \"chapter\": \"Name\" to scenes in scenes.json, then re-render.")
        return
    if rows[0][0] > 0:
        rows.insert(0, (0, "Intro"))
    print("\n".join(f"{ts(s)} {c}" for s, c in rows))
