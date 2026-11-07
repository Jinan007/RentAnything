# File formats

## `stories/<slug>/config.json` (everything optional; defaults in `studio/common.py`)
```json
{ "title": "My Series", "aspect": "9:16", "resolution": "1080p", "fps": 24, "scene_seconds": 9,
  "voice": {"engine": "piper", "voice": "en_US-ryan-high", "speed": 1.0},
  "pronunciations": {"Kaelith": "Kay-lith"},
  "captions": {"words_per_chunk": 3, "position": 0.74, "highlight": "#FFD21F"},
  "music": {"mode": "generated", "mood": "dark", "volume": 0.16},
  "sfx": {"enabled": true, "volume": 0.55} }
```
`aspect`: `9:16` for Shorts/Reels, `16:9` for normal videos. `music.mode`: `generated` (moods dark/epic/sad/calm), `file` (set `music.file`), or `none`.

## `stories/<slug>/characters.json`
```json
{ "style": "manhwa webtoon illustration, ... (one fixed style line used in every prompt)",
  "negative": "text, watermark, speech bubbles, extra fingers, ...",
  "characters": { "kai": {"name": "Kai Ren", "say": "Kye Ren", "look": "35-60 word fixed visual description"} },
  "locations": { "ruined_gate": "reusable setting description" } }
```
`say` is how the TTS pronounces the name (captions keep `name`). Every character that appears in a panel prompt must be listed here; costume change = new key (`kai_armor`).

## `episodes/epNN/scenes.json`
```json
{ "title": "Episode 1: ...", "episode": 1, "scenes": [
  { "id": 1,
    "narration": "One or two short spoken sentences (about scene_seconds x 2.6 words).",
    "characters": ["kai"], "setting": "ruined_gate | free text", "action": "what is visibly happening",
    "shot": "wide|medium|close|extreme_close|low_angle|high_angle|over_shoulder|action|insert",
    "mood": "calm|tense|epic|sad|mysterious|romantic|dark|action|comedic|triumphant",
    "camera": "zoom_in|zoom_out|pan_left|pan_right|pan_up|pan_down|drift|static",
    "focus": [0.5, 0.4],
    "fx": ["shake", "flash"],
    "sfx": ["impact", {"name": "whoosh", "at": 1.2}],
    "overlay": {"type": "system", "text": "Quest accepted.\nSurvive."},
    "tall": false, "hold": 0, "image": "scene_004.png", "chapter": "Name for YouTube chapters" } ] }
```
- `narration` empty + `hold` seconds = silent beat. `image` reuses another scene's file (no new image needed). `tall: true` asks for an extra-tall panel; pair with `pan_down`.
- `overlay.type`: `system` (blue game-window box) or `bigtext` (pop-in headline like LEVEL UP).
- `sfx` names: impact, boom, whoosh, riser, system_ping, heartbeat, slash, glitch, shimmer.
- Images live in `episodes/epNN/images/` as `scene_001.png` (png, jpg, jpeg, webp).
