"""Free, local text-to-speech: Piper (best), espeak-ng / macOS 'say' (fallbacks), 'silent' (draft timing)."""
import json, os, re, subprocess, sys, tempfile, urllib.request, wave, shutil
from pathlib import Path
from .common import KIT, die, have, probe_duration, sha, load_json, save_json, load_config, load_characters, load_scenes, ep_dir, need_ffmpeg

VOICE_DIR = KIT / "voices"
_cache = {}


def voice_urls(voice_id):
    m = re.match(r"^([a-z]{2,3})_([A-Z]{2})-([\w]+)-(\w+)$", voice_id)
    if not m:
        die(f"voice id '{voice_id}' not recognised. Expected like en_US-ryan-high (see https://huggingface.co/rhasspy/piper-voices).")
    lang, region, name, q = m.groups()
    base = f"https://huggingface.co/rhasspy/piper-voices/resolve/main/{lang}/{lang}_{region}/{name}/{q}/{voice_id}"
    return base + ".onnx", base + ".onnx.json"


def voice_paths(voice_id):
    return VOICE_DIR / f"{voice_id}.onnx", VOICE_DIR / f"{voice_id}.onnx.json"


def download_voice(voice_id):
    VOICE_DIR.mkdir(parents=True, exist_ok=True)
    for url, dst in zip(voice_urls(voice_id), voice_paths(voice_id)):
        if dst.exists() and dst.stat().st_size > 0:
            continue
        print(f"downloading {dst.name} ...")
        try:
            urllib.request.urlretrieve(url, dst)
        except Exception as e:
            if dst.exists():
                dst.unlink()
            die(f"could not download {url}\n  {e}\n  Check your internet connection, or download the two files by hand into {VOICE_DIR}")
    print("voice ready:", voice_id)


def piper_available():
    try:
        import piper  # noqa
        return True
    except Exception:
        return False


def pick_engine(cfg, override=None):
    eng = override or cfg["voice"]["engine"]
    if eng == "piper" and not piper_available():
        for alt in ("say", "espeak"):
            if (alt == "say" and have("say")) or (alt == "espeak" and (have("espeak-ng") or have("espeak"))):
                print(f"note: Piper is not installed, using '{alt}' instead (robotic). Run: python recap.py doctor --install --voice {cfg['voice']['voice']}")
                return alt
        die("Piper TTS is not installed and no fallback voice exists. Run: python recap.py doctor --install --voice en_US-ryan-high")
    return eng


def clean_text(text, pron):
    t = re.sub(r"[*_`#>]+", "", text)
    t = t.replace("\u2014", ", ").replace("\u2013", ", ").replace("\u2026", "...").replace("\n", " ")
    for k, v in (pron or {}).items():
        t = re.sub(rf"\b{re.escape(k)}\b", v, t)
    return re.sub(r"\s+", " ", t).strip()


def _write_silence(path, secs, sr=22050):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(b"\x00\x00" * int(sr * secs))


def synth_piper(text, out, voice_id, speed):
    mp, _ = voice_paths(voice_id)
    if not mp.exists():
        download_voice(voice_id)
    from piper import PiperVoice
    if voice_id not in _cache:
        _cache[voice_id] = PiperVoice.load(str(mp))
    v = _cache[voice_id]
    length_scale = 1.0 / max(0.4, speed)
    with wave.open(str(out), "wb") as wf:
        try:
            from piper import SynthesisConfig           # piper-tts >= 1.3
            v.synthesize_wav(text, wf, syn_config=SynthesisConfig(length_scale=length_scale))
        except ImportError:                              # piper-tts 1.2
            v.synthesize(text, wf, length_scale=length_scale)


def synth_espeak(text, out, voice, speed):
    exe = "espeak-ng" if have("espeak-ng") else "espeak"
    lang = "en-us" if (not voice or "_" in voice) else voice
    subprocess.run([exe, "-v", lang, "-s", str(int(165 * speed)), "-w", str(out), text], check=True, capture_output=True)


def synth_say(text, out, voice, speed):
    tmp = Path(tempfile.mkdtemp()) / "s.aiff"
    cmd = ["say", "-r", str(int(190 * speed)), "-o", str(tmp)]
    if voice and "_" not in voice:
        cmd += ["-v", voice]
    subprocess.run(cmd + [text], check=True, capture_output=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp), str(out)], check=True)


def synth_silent(text, out, speed):
    _write_silence(out, max(0.8, len(text.split()) / 2.6 / max(0.4, speed)))


def synth(text, out, cfg, engine):
    v = cfg["voice"]
    if engine == "piper":
        synth_piper(text, out, v["voice"], v["speed"])
    elif engine == "espeak":
        synth_espeak(text, out, v["voice"], v["speed"])
    elif engine == "say":
        synth_say(text, out, v["voice"], v["speed"])
    elif engine == "silent":
        synth_silent(text, out, v["speed"])
    else:
        die(f"unknown voice engine '{engine}' (use piper, espeak, say or silent)")


def ensure_audio(project, ep, scenes, cfg, force=False, engine=None):
    """Returns {scene_id: (wav_path, seconds)}; empty narration -> (None, 0)."""
    need_ffmpeg()
    epd = ep_dir(project, ep)
    adir = epd / "audio"
    adir.mkdir(parents=True, exist_ok=True)
    chars = load_characters(project)
    pron = dict(cfg.get("pronunciations", {}))
    for c in chars.get("characters", {}).values():
        if c.get("say") and c.get("name"):
            pron.setdefault(c["name"], c["say"])
            pron.setdefault(c["name"].split()[0], c["say"].split()[0] if " " in c["say"] else c["say"])
    eng = pick_engine(cfg, engine)
    mpath = adir / "manifest.json"
    man = load_json(mpath) if mpath.exists() else {}
    result, done, total = {}, 0, sum(1 for s in scenes if s["narration"])
    for s in scenes:
        if not s["narration"]:
            result[s["id"]] = (None, 0.0)
            continue
        text = clean_text(s["narration"], pron)
        key = sha(text, eng, cfg["voice"])
        wav = adir / f"scene_{int(s['id']):03d}.wav"
        m = man.get(str(s["id"]))
        if not force and m and m["hash"] == key and wav.exists():
            result[s["id"]] = (wav, m["seconds"])
            continue
        synth(text, wav, cfg, eng)
        dur = probe_duration(wav)
        man[str(s["id"])] = {"hash": key, "seconds": dur}
        result[s["id"]] = (wav, dur)
        done += 1
        print(f"  voiced scene {s['id']} ({dur:.1f}s)  [{done} new / {total}]", flush=True)
    save_json(mpath, man)
    return result


def cmd_tts(project, ep, force=False, test=None, engine=None):
    cfg = load_config(project)
    if test:
        eng = pick_engine(cfg, engine)
        out = Path(project) / "voice_test.wav"
        synth(clean_text(test, cfg.get("pronunciations")), out, cfg, eng)
        print(f"test clip -> {out} ({probe_duration(out):.1f}s) using engine '{eng}', voice '{cfg['voice']['voice']}'")
        return
    _, scenes = load_scenes(ep_dir(project, ep))
    res = ensure_audio(project, ep, scenes, cfg, force, engine)
    tot = sum(d for _, d in res.values())
    print(f"voiceover ready: {tot/60:.1f} min of narration ({tot:.0f}s) in {ep_dir(project, ep) / 'audio'}")
