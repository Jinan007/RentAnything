"""Shared helpers: paths, config, json, ffmpeg, vocab."""
import json, os, re, subprocess, sys, hashlib, shutil
from pathlib import Path

KIT = Path(__file__).resolve().parent.parent

CAMERAS = ["zoom_in", "zoom_out", "pan_left", "pan_right", "pan_up", "pan_down", "drift", "static"]
FXS = ["shake", "flash", "none"]
SFX_NAMES = ["impact", "boom", "whoosh", "riser", "system_ping", "heartbeat", "slash", "glitch", "shimmer"]
MUSIC_MOODS = ["dark", "epic", "sad", "calm"]
OVERLAY_TYPES = ["system", "bigtext"]

DEFAULT_CONFIG = {
    "title": "",
    "aspect": "9:16",            # "9:16" (Shorts/Reels) or "16:9" (normal YouTube)
    "resolution": "1080p",       # short side: 720p | 1080p | 1440p | 2160p
    "fps": 24,
    "scene_seconds": 9,          # target seconds per panel; the writer uses this to size scenes
    "voice": {"engine": "piper", "voice": "en_US-ryan-high", "speed": 1.0},
    "pronunciations": {},        # {"Kaelith": "Kay-lith"}  (TTS only, captions keep the real spelling)
    "timing": {"lead": 0.15, "tail": 0.30, "min_scene": 1.5, "silent_scene": 1.5},
    "captions": {"enabled": True, "font": "", "words_per_chunk": 3, "size": 0.062,
                 "color": "#FFFFFF", "highlight": "#FFD21F", "stroke": "#000000", "position": 0.74},
    "look": {"vignette": 0.38, "zoom": 0.14},
    "fade": {"in": 0.5, "out": 0.9},
    "music": {"mode": "generated", "file": "", "mood": "dark", "volume": 0.16, "duck": 0.75},
    "sfx": {"enabled": True, "volume": 0.55},
}


def deep_merge(a, b):
    out = dict(a)
    for k, v in (b or {}).items():
        out[k] = deep_merge(out[k], v) if isinstance(v, dict) and isinstance(out.get(k), dict) else v
    return out


def load_json(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def save_json(p, obj):
    Path(p).parent.mkdir(parents=True, exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def die(msg):
    sys.exit(f"ERROR: {msg}")


def project_path(project):
    p = Path(project)
    if not p.exists():
        alt = KIT / "stories" / project
        p = alt if alt.exists() else p
    if not (p / "config.json").exists():
        die(f"'{project}' is not a story project (no config.json). Create one with: python recap.py new --name <slug>")
    return p


def load_config(project):
    return deep_merge(DEFAULT_CONFIG, load_json(Path(project) / "config.json"))


def load_characters(project):
    p = Path(project) / "characters.json"
    return load_json(p) if p.exists() else {"style": "", "negative": "", "characters": {}, "locations": {}}


def ep_dir(project, ep):
    return Path(project) / "episodes" / f"ep{int(ep):02d}"


def frame_size(cfg, scale=1.0):
    short = {"720p": 720, "1080p": 1080, "1440p": 1440, "2160p": 2160}.get(cfg["resolution"], 1080)
    if cfg["aspect"] == "16:9":
        w, h = short * 16 / 9, short
    else:
        w, h = short, short * 16 / 9
    ev = lambda v: max(2, int(round(v * scale / 2.0)) * 2)
    return ev(w), ev(h)


def load_scenes(epdir):
    p = Path(epdir) / "scenes.json"
    if not p.exists():
        die(f"{p} not found. Write the episode first (/write-episode).")
    data = load_json(p)
    scenes = data["scenes"] if isinstance(data, dict) else data
    for i, s in enumerate(scenes, 1):
        s.setdefault("id", i)
        s["narration"] = (s.get("narration") or "").strip()
    return data if isinstance(data, dict) else {"scenes": scenes}, scenes


def validate_scenes(scenes, chars):
    errs, warns = [], []
    known = set((chars or {}).get("characters", {}).keys())
    seen = set()
    for s in scenes:
        sid = s["id"]
        tag = f"scene {sid}"
        if sid in seen:
            errs.append(f"{tag}: duplicate id")
        seen.add(sid)
        n = len(s["narration"].split())
        if n == 0 and not s.get("hold"):
            warns.append(f"{tag}: empty narration (will be a silent {DEFAULT_CONFIG['timing']['silent_scene']}s beat)")
        if n > 45:
            warns.append(f"{tag}: narration is {n} words; split it so each panel stays under ~12 s")
        for c in s.get("characters", []):
            if c not in known:
                errs.append(f"{tag}: unknown character '{c}' (not in characters.json)")
        cam = s.get("camera", "drift")
        if cam not in CAMERAS:
            errs.append(f"{tag}: camera '{cam}' not in {CAMERAS}")
        for fx in s.get("fx", []):
            if fx not in FXS:
                errs.append(f"{tag}: fx '{fx}' not in {FXS}")
        for sf in s.get("sfx", []):
            name = sf["name"] if isinstance(sf, dict) else sf
            if name not in SFX_NAMES:
                errs.append(f"{tag}: sfx '{name}' not in {SFX_NAMES}")
        ov = s.get("overlay")
        if ov and ov.get("type") not in OVERLAY_TYPES:
            errs.append(f"{tag}: overlay type must be one of {OVERLAY_TYPES}")
    return errs, warns


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        die(f"command failed: {' '.join(map(str, cmd))}\n{r.stderr[-1500:]}")
    return r.stdout


def have(cmd):
    return shutil.which(cmd) is not None


def need_ffmpeg():
    if not (have("ffmpeg") and have("ffprobe")):
        die("ffmpeg/ffprobe not found. Install it (see references/install.md) and run: python recap.py doctor")


def probe_duration(path):
    out = run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)])
    return float(out.strip())


def natural_key(s):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", str(s))]


def sha(*parts, n=10):
    h = hashlib.sha1()
    for p in parts:
        h.update(json.dumps(p, sort_keys=True, default=str).encode())
    return h.hexdigest()[:n]


def ts(sec):
    m, s = divmod(max(0.0, sec), 60)
    return f"{int(m):02d}:{int(s):02d}"
