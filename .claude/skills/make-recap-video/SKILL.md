---
name: make-recap-video
description: >
  This skill should be used when the user wants to make an original manhwa-style recap video for YouTube
  (Nokoi Recap): "make a manhwa recap", "create a story and turn it into a video", "do the whole episode",
  "write a manhwa story and make the video", "automate my recap channel", "story to video with AI images and
  free voice". It runs the whole pipeline: install, story bible, episode script, panel image prompts (the user
  generates and uploads the images), free local text-to-speech, and a finished MP4 with Ken-Burns motion,
  captions, system windows, SFX and music in 9:16 or 16:9.
metadata:
  version: "1.0.0"
  author: Nokoi Recap
---

# Nokoi Recap Studio: story → script → panels → video

Original stories only. The user (a solo creator) supplies images; Claude does everything else. Shared references are in `references/`; the tested toolkit is in `kit/`.

## What the user does vs what Claude does
| Stage | Command | Claude | User |
|---|---|---|---|
| 1 | `/new-story` | install check, story bible, characters, episode arc | pick genre/hook, approve |
| 2 | `/write-episode` | narration script + `scenes.json` | approve/tweak |
| 3 | `/make-panels` | image prompts + character sheets | generate images, upload |
| 4 | `/render-recap` | local voiceover, mix, video, QC | review the preview |
| 5 | `/package-episode` | titles, description, chapters, thumbnail prompts | upload |

Say "do the whole episode" and run 1 to 5 in order, stopping only where the user must act (images) or approve.

## Setup
Follow `references/install.md`: copy `kit/` to `nokoi-recap-studio/` in the workspace (skip if present), run `python recap.py doctor`, then `python recap.py selftest`. Every later command runs from inside `nokoi-recap-studio/`. If Python is `python3` or `py -3` on the machine, use that.

## Project layout (inside nokoi-recap-studio/)
```
stories/<slug>/config.json  characters.json  bible.md  character_sheet_prompts.md
stories/<slug>/episodes/ep01/script.md  scenes.json  prompts/  images/  audio/  build/  out/
```
Formats: `references/scenes-schema.md`. Writing rules: `references/story-writing.md`. Image workflow: `references/panel-prompts.md`. Render/QC: `references/render-qc.md`. Upload kit: `references/youtube-packaging.md`.

## Command cheat sheet (all from nokoi-recap-studio/)
```
python recap.py new --name <slug> --aspect 9:16 --title "..."
python recap.py validate|prompts|check --project stories/<slug> --ep 1
python recap.py sheets --project stories/<slug>
python recap.py import --project stories/<slug> --ep 1 --from <folder>
python recap.py tts --project stories/<slug> --ep 1 --test "line"
python recap.py render --project stories/<slug> --ep 1 [--scenes 1-8] [--scale 0.5] [--placeholders]
python recap.py still|chapters|clean ...   python recap.py contact --video <mp4>
```

## Ground rules
- Ask before writing anything long, but only what is missing: genre, tone, length (Short ~55 s vs 5 to 7 min), aspect, episode count. Offer sensible defaults instead of interrogating.
- Never invent or copy existing IP: no names, characters, systems or "in the style of" from real series or artists, in stories or prompts.
- Keep content advertiser-friendly; no sexual content, no minors in romantic context, non-graphic violence.
- Characters live in `characters.json`; every panel prompt is built from it by `recap.py prompts`. Never hand-write per-scene character descriptions, so faces stay consistent.
- Always run `validate` after writing `scenes.json`, `check` before rendering, and preview a few scenes before a long final render.
- Be honest about limits: voice is Piper (free, good but not human), art quality depends on the user's image tool, long episodes take minutes to render.
