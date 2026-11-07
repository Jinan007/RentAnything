"""Environment check, installer, project scaffolding, selftest."""
import shutil, subprocess, sys, urllib.request
from pathlib import Path
from .common import KIT, have, save_json, load_json, DEFAULT_CONFIG, die
from . import tts

FONT_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/anton/Anton-Regular.ttf"


def pip_install(pkgs):
    cmd = [sys.executable, "-m", "pip", "install", *pkgs]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0 and "externally-managed" in (r.stderr or ""):
        r = subprocess.run(cmd + ["--break-system-packages"], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-1200:])
        die("pip install failed (see above).")


def cmd_doctor(install=False, voice=None, fonts=False):
    ok = True
    print(f"python  {sys.version.split()[0]}", "OK" if sys.version_info >= (3, 9) else "NEEDS >= 3.9")
    for exe in ("ffmpeg", "ffprobe"):
        good = have(exe)
        ok &= good
        print(f"{exe:8}", "OK" if good else "MISSING  (install ffmpeg: winget install Gyan.FFmpeg | brew install ffmpeg | sudo apt-get install ffmpeg)")
    for mod, pkg in (("PIL", "pillow"), ("numpy", "numpy"), ("piper", "piper-tts")):
        try:
            __import__(mod)
            print(f"{pkg:9}OK")
        except Exception:
            print(f"{pkg:9}MISSING")
            if install:
                print(f"  installing {pkg} ...")
                pip_install([pkg])
            elif mod != "piper":
                ok = False
    if install:
        print("(re-run doctor to confirm)")
    if voice:
        tts.download_voice(voice)
    vs = list((KIT / "voices").glob("*.onnx"))
    print("voices  ", ", ".join(v.stem for v in vs) if vs else "none yet (python recap.py doctor --voice en_US-ryan-high)")
    if fonts:
        dst = KIT / "fonts" / "Anton-Regular.ttf"
        if not dst.exists():
            print("downloading Anton font (Open Font License)...")
            try:
                urllib.request.urlretrieve(FONT_URL, dst)
            except Exception as e:
                print("  font download failed:", e)
    fs = list((KIT / "fonts").glob("*.ttf"))
    print("fonts   ", ", ".join(f.name for f in fs) if fs else "none in kit/fonts (system bold font will be used; run doctor --fonts for a punchier caption font)")
    print("\nReady." if ok else "\nFix the MISSING items above, then run doctor again.")


CHARS_TEMPLATE = {
    "style": "manhwa webtoon illustration, clean sharp lineart, cel shading with painterly highlights, vivid cinematic colors, dramatic lighting, highly detailed, original characters",
    "negative": "text, letters, watermark, signature, speech bubbles, logo, extra fingers, deformed hands, extra limbs, blurry, low quality",
    "characters": {},
    "locations": {},
}


def cmd_new(name, aspect="9:16", title=""):
    base = KIT / "stories" / name
    if base.exists():
        die(f"{base} already exists")
    (base / "episodes").mkdir(parents=True)
    cfg = {"title": title or name, "aspect": aspect}
    save_json(base / "config.json", cfg)
    save_json(base / "characters.json", CHARS_TEMPLATE)
    (base / "bible.md").write_text(f"# {title or name}\n\n(Story bible goes here: premise, power system, cast, villain, episode arc.)\n", encoding="utf-8")
    print(f"story project created: {base}")


def cmd_selftest():
    from .render import cmd_render
    import shutil as sh
    src = KIT / "examples" / "sample-story"
    dst = KIT / "stories" / "_selftest"
    if dst.exists():
        sh.rmtree(dst)
    sh.copytree(src, dst)
    cfg = load_json(dst / "config.json")
    cfg.update({"voice": {"engine": "silent"}, "resolution": "720p", "fps": 12})
    save_json(dst / "config.json", cfg)
    out = cmd_render(str(dst), 1, scale=0.4, placeholders=True, workers=2)
    from .common import probe_duration
    d = probe_duration(out)
    if d < 5:
        die(f"selftest video too short ({d:.1f}s)")
    sh.rmtree(dst)
    print(f"\nSELFTEST PASSED ({d:.1f}s test video rendered with placeholders and silent voice). Voice quality is checked separately: python recap.py tts --test \"Hello there\"")
