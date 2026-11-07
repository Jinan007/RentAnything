# Installing the studio (once per machine or workspace)

The studio is a small Python toolkit in `kit/` of the `make-recap-video` skill. Copy it into a writable workspace folder because the plugin folder may be read-only and the studio stores stories, voices and renders next to it.

## 1. Locate and copy
- Stage commands reach the kit at `../make-recap-video/kit/` from their own folder. If unsure: `find / -path "*make-recap-video/kit/recap.py" 2>/dev/null | head -1` (macOS/Linux) or `Get-ChildItem -Recurse -Filter recap.py -Path $HOME\.claude` (PowerShell).
- Use the user's connected project folder, otherwise the current directory. Create `nokoi-recap-studio/` there.
- If `nokoi-recap-studio/recap.py` already exists, it is installed: skip to step 3 and never touch its `stories/` folder.
- Copy all of `kit/` into it: `cp -r "<kit>/." nokoi-recap-studio/`.

## 2. Install dependencies (tell the user in one short list)
ffmpeg (encodes video), Pillow + numpy (draw frames, synthesise sound), Piper TTS (free offline voice, ~60 MB voice model downloaded once).

| Need | Windows | macOS | Linux |
|---|---|---|---|
| ffmpeg + ffprobe | `winget install --id Gyan.FFmpeg -e` (new terminal after) | `brew install ffmpeg` | `sudo apt-get install -y ffmpeg` |
| Python >= 3.9 | python.org installer | `brew install python` | distro package |
| Python packages + voice + font | `python recap.py doctor --install --voice en_US-ryan-high --fonts` | same | same (managed Pythons are handled automatically) |

Use `python3` where `python` is not Python 3, and `py -3` on some Windows setups.

## 3. Check and self-test
1. `python recap.py doctor`: repeat until nothing says MISSING (piper may stay missing; then a robotic fallback voice is used).
2. `python recap.py selftest`: renders a tiny test video with placeholder images and silent voice. It must print `SELFTEST PASSED`.
3. `python recap.py tts --project <any story> --ep 1 --test "Hello, this is the voice for the channel."` then ask the user to listen to `voice_test.wav`. First Piper run downloads the voice from huggingface.co and needs internet.

## Notes
- Voices: `en_US-ryan-high` (male, clear), `en_US-lessac-high`, `en_GB-alan-medium`, `en_US-amy-medium` (female). Browse https://huggingface.co/rhasspy/piper-voices. Change `voice.voice` in the story's `config.json`.
- No GPU needed. Speed is roughly 3 to 4x real time at 1080x1920 with 3 workers (a 5-minute episode ~15-20 min). Preview at `--scale 0.5` is ~4x faster. Unchanged scenes are cached, so re-renders after a small edit are fast.
- Updating: copy the new `kit/` over the old one. It never touches `stories/`.
- The bundled Anton font is under the SIL Open Font License.
