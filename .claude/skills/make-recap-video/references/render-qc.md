# Render and QC

Order (cheap to expensive):
1. Voice check once: `python recap.py tts --project <p> --ep N --test "<a real line from the script>"`, user listens to `voice_test.wav`. Fix pronunciations (`say` / `pronunciations`) or change `voice.voice` / `voice.speed` (1.05 to 1.12 feels punchy for recaps).
2. Timing draft with no images yet: `python recap.py render --project <p> --ep N --placeholders --scale 0.35`. Shows runtime, captions and fx timing.
3. When images exist: `python recap.py check ...`, then preview: `python recap.py render ... --scenes 1-8 --scale 0.5`. For one frame: `python recap.py still ... --scene K --p 0.5`.
4. Final: `python recap.py render --project <p> --ep N` → `episodes/epNN/out/epNN_<slug>_final.mp4`.
5. `python recap.py contact --video <mp4> --step 10` makes a contact sheet; open it and check pacing and captions. `python recap.py chapters ...` prints YouTube chapters.

QC checklist: runtime matches the plan; first 3 seconds are a hook; captions readable and not covering faces; no stretched/blurry panels; no scene longer than ~12 s on one image; voice pronounces names right; music sits under the voice; loudness ~ -14 LUFS (applied automatically); fade-out at the end.

Troubleshooting:
- `piper-tts MISSING` → `python recap.py doctor --install --voice en_US-ryan-high`. Voice download fails → check internet, or place `<voice>.onnx` and `<voice>.onnx.json` into `kit/voices/` by hand.
- `ffmpeg not found` → install it, open a new terminal.
- Slow render → `--workers 4`, `--scale 0.5` for previews, `fps` 24 → 20 in config for drafts.
- Out of memory with huge images → resize panels to ~2000 px on the long side.
- Captions use a plain font → `python recap.py doctor --fonts` or drop any bold `.ttf` into `kit/fonts/`, or set `captions.font` to a path.
- Changed config look/captions/timing → affected scenes re-render automatically; `--force` redoes all; `python recap.py clean` wipes the cache.
