"""Synthesised SFX + background music (numpy only) and the final voice/music/sfx mix."""
import subprocess, wave
import numpy as np
from pathlib import Path
from .common import die

SR = 44100


def read_audio(path):
    p = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"], capture_output=True)
    if p.returncode != 0:
        die(f"ffmpeg could not decode {path}: {p.stderr.decode()[-500:]}")
    return np.frombuffer(p.stdout, dtype=np.float32).copy()


def write_wav(path, mono):
    st = np.clip(mono, -1, 1)
    pcm = (np.stack([st, st], axis=1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def _t(d):
    return np.arange(int(SR * d)) / SR


def _lp(x, alpha):
    """one-pole low-pass; alpha scalar or per-sample array in (0,1]."""
    a = np.broadcast_to(alpha, x.shape)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def _sfx(name, seed=1):
    rng = np.random.default_rng(seed)
    if name in ("impact", "boom"):
        d = 1.3 if name == "impact" else 2.4
        t = _t(d)
        f = (150 if name == "impact" else 100) * np.exp(-t * 3.5) + 36
        body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * (3.0 if name == "impact" else 1.7))
        noise = _lp(rng.standard_normal(len(t)).astype("f4"), 0.08) * np.exp(-t * 16)
        return 0.9 * body + 0.7 * noise
    if name == "whoosh":
        t = _t(0.9)
        n = rng.standard_normal(len(t)).astype("f4")
        a = 0.03 + 0.5 * np.sin(np.pi * t / 0.9) ** 2
        return _lp(n, a) * np.sin(np.pi * t / 0.9) ** 1.5 * 1.6
    if name == "riser":
        d = 2.2
        t = _t(d)
        f = 200 + 1600 * (t / d) ** 2
        tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5
        n = rng.standard_normal(len(t)).astype("f4")
        hp = n - _lp(n, 0.15)
        return (tone + 0.5 * hp) * (t / d) ** 2
    if name == "system_ping":
        t = _t(0.9)
        a = np.sin(2 * np.pi * 1320 * t) * np.exp(-t * 7)
        t2 = np.clip(t - 0.12, 0, None)
        b = np.sin(2 * np.pi * 1760 * t2) * np.exp(-t2 * 6) * (t >= 0.12)
        return 0.45 * (a + b)
    if name == "heartbeat":
        t = _t(1.0)
        def thump(t0, amp):
            tt = np.clip(t - t0, 0, None)
            return amp * np.sin(2 * np.pi * 58 * tt) * np.exp(-tt * 22) * (t >= t0)
        return thump(0.0, 1.0) + thump(0.27, 0.75)
    if name == "slash":
        t = _t(0.5)
        n = rng.standard_normal(len(t)).astype("f4")
        hp = (n - _lp(n, 0.2)) * np.exp(-t * 20)
        ring = np.sin(2 * np.pi * 2600 * t) * np.exp(-t * 14) * 0.3
        return 0.8 * hp + ring
    if name == "glitch":
        out = np.zeros(int(SR * 0.6), dtype="f4")
        pos = 0
        while pos < len(out):
            L = int(SR * rng.uniform(0.02, 0.07))
            f = rng.choice([180, 440, 900, 1500])
            seg = np.sign(np.sin(2 * np.pi * f * np.arange(L) / SR)) * rng.uniform(0.2, 0.6)
            if rng.random() < 0.7:
                out[pos:pos + L] = seg[:len(out) - pos]
            pos += L
        return out * 0.5
    if name == "shimmer":
        d = 1.8
        t = _t(d)
        x = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f in (1800, 2400, 3200, 4300))
        return 0.18 * x * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)) * np.exp(-t * 2.2)
    die(f"unknown sfx '{name}'")


def sfx(name, seed=1):
    c = np.asarray(_sfx(name, seed), dtype="f4")
    pk = float(np.abs(c).max()) or 1.0
    return c / pk * 0.9


CHORDS = {
    "dark": ([[55.0, 82.4, 110.0, 130.8], [51.9, 77.8, 103.8, 123.5], [49.0, 73.4, 98.0, 116.5], [51.9, 77.8, 103.8, 123.5]], 4.0, False),
    "epic": ([[73.4, 110.0, 146.8, 174.6], [58.3, 87.3, 116.5, 146.8], [87.3, 130.8, 174.6, 220.0], [65.4, 98.0, 130.8, 164.8]], 4.0, True),
    "sad": ([[55.0, 110.0, 130.8, 164.8], [43.7, 87.3, 130.8, 174.6], [65.4, 130.8, 164.8, 196.0], [49.0, 98.0, 146.8, 196.0]], 5.0, False),
    "calm": ([[65.4, 130.8, 196.0, 261.6], [49.0, 98.0, 146.8, 196.0], [55.0, 110.0, 164.8, 220.0], [43.7, 87.3, 130.8, 220.0]], 5.0, False),
}


def music(mood, seconds, seed=3):
    prog, clen, pulse = CHORDS.get(mood, CHORDS["dark"])
    n = int(SR * seconds)
    out = np.zeros(n + int(SR * clen * 2), dtype="f4")
    rng = np.random.default_rng(seed)
    step = int(SR * clen)
    pos, i = 0, 0
    ct = _t(clen * 1.5)
    env = np.minimum(1, ct / 1.2) * np.minimum(1, (ct[-1] - ct) / 1.5)
    while pos < n:
        chord = prog[i % len(prog)]
        pad = np.zeros_like(ct)
        for f in chord:
            for det in (-0.35, 0.0, 0.35):
                ph = rng.uniform(0, 6.28)
                pad += np.sin(2 * np.pi * (f + det) * ct + ph) + 0.3 * np.sin(2 * np.pi * 2 * (f + det) * ct + ph) + 0.1 * np.sin(2 * np.pi * 3 * f * ct)
        out[pos:pos + len(pad)] += pad * env * 0.035
        pos += step
        i += 1
    out = out[:n]
    if pulse:
        beat = int(SR * 0.5)
        kt = _t(0.4)
        kick = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-kt * 9) + 40) / SR) * np.exp(-kt * 9) * 0.35
        for b in range(0, n - len(kick), beat):
            out[b:b + len(kick)] += kick
    m = np.abs(out).max() or 1
    out = out / m * 0.9
    fi, fo = int(SR * 2), int(SR * 3)
    out[:fi] *= np.linspace(0, 1, fi)
    out[-fo:] *= np.linspace(1, 0, fo)
    return out


def _smooth(x, secs):
    k = max(1, int(SR * secs))
    c = np.cumsum(np.insert(x, 0, 0))
    y = (c[k:] - c[:-k]) / k
    return np.concatenate([np.full(k // 2, y[0]), y, np.full(len(x) - len(y) - k // 2, y[-1])])[:len(x)]


def build_mix(timeline, voices, cfg, out_wav):
    total = timeline[-1]["start"] + timeline[-1]["length"]
    n = int(SR * (total + 0.5))
    voice = np.zeros(n, dtype="f4")
    for it in timeline:
        wav, dur = voices.get(it["id"], (None, 0))
        if wav:
            a = read_audio(wav)
            s = int((it["start"] + it["lead"]) * SR)
            seg = a[: max(0, n - s)]
            voice[s:s + len(seg)] += seg
    pk = np.abs(voice).max()
    if pk > 0:
        voice = voice / pk * 0.85

    sfx_track = np.zeros(n, dtype="f4")
    if cfg["sfx"]["enabled"]:
        for it in timeline:
            for sf in it.get("sfx", []):
                name, at = (sf["name"], float(sf.get("at", 0))) if isinstance(sf, dict) else (sf, 0.0)
                clip = sfx(name, seed=it["id"] + 7)
                s = int((it["start"] + at) * SR)
                seg = clip[: max(0, n - s)]
                sfx_track[s:s + len(seg)] += seg * cfg["sfx"]["volume"]

    mus = np.zeros(n, dtype="f4")
    mc = cfg["music"]
    if mc["mode"] == "file" and mc.get("file"):
        a = read_audio(mc["file"])
        a = np.tile(a, int(np.ceil(n / max(1, len(a)))))[:n]
        fi, fo = int(SR * 1.5), int(SR * 2.5)
        a[:fi] *= np.linspace(0, 1, fi); a[-fo:] *= np.linspace(1, 0, fo)
        mus = a / (np.abs(a).max() or 1)
    elif mc["mode"] == "generated":
        mus = music(mc.get("mood", "dark"), n / SR)[:n]
    if mc["mode"] != "none":
        env = _smooth(np.abs(voice), 0.12)
        env = np.clip(env / 0.08, 0, 1)
        env = _smooth(env, 0.35)
        gain = 1 - mc["duck"] * env
        mus = mus * gain * mc["volume"]

    mix = voice + sfx_track + mus
    mix = np.tanh(mix * 1.1) / np.tanh(1.1)
    write_wav(out_wav, mix)
    return out_wav
