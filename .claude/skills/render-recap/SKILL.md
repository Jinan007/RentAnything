---
name: render-recap
description: >
  This skill should be used when the user runs /render-recap or asks to "render the episode", "make the video",
  "voice the script", "generate the voiceover", "preview the first scenes", "re-render scene 5", or "change
  the voice/captions/music" in the Nokoi Recap Studio. It covers local text-to-speech, preview, final render and QC.
metadata:
  version: "1.0.0"
  author: Nokoi Recap
---

# /render-recap: voice → preview → final → QC

Reference: `../make-recap-video/references/render-qc.md` (order of steps, QC checklist, troubleshooting).

1. `python recap.py check ...` must be clean (or use `--placeholders` for a timing draft).
2. First episode of a series: run the voice test (`tts --test "<a real line>"`), ask the user to listen to `voice_test.wav`, adjust `voice.voice`, `voice.speed`, `say`/`pronunciations` in config until they like it. Skip on later episodes unless asked.
3. Preview: `render --scenes 1-8 --scale 0.5` (and `still` for single frames). Tell the user the output path and what to check; ask for approval before the long render.
4. Final: `render` without options. Warn that the 1080p render takes a while (~3 to 4x real time) and stays cached per scene, so tweaks re-render only changed scenes.
5. QC: `contact --video <mp4>`, open the sheet, walk the checklist, fix issues (edit `scenes.json` or config, re-render). Report runtime, resolution, file size and the path to `out/epNN_*_final.mp4`.
6. Offer `/package-episode`.

Config changes the user may ask for: captions (`words_per_chunk`, `position`, `highlight`), music (`mode`, `mood`, `volume`, own file), `voice.speed`, `aspect`, `resolution`, `fps`, `sfx.enabled`.
