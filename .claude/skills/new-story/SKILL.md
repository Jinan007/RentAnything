---
name: new-story
description: >
  This skill should be used when the user runs /new-story or asks to "start a new manhwa series", "create a
  story idea", "make a story bible", "invent a new manhwa for my channel", or "set up the recap studio".
  It covers install/doctor, intake, story project creation, the story bible, character look strings and the
  episode arc.
metadata:
  version: "1.0.0"
  author: Nokoi Recap
---

# /new-story: install → intake → project → bible

Shared references: `../make-recap-video/references/` (relative to this skill's folder); kit: `../make-recap-video/kit/`.

## 1. Install check
Follow `install.md` if `nokoi-recap-studio/` is missing or `python recap.py doctor` reports MISSING (piper may be optional at this stage).

## 2. Intake (ask once, skip what the user already said, offer defaults)
- Genre blend and mood (e.g. system underdog, regression revenge, tower climb, reincarnated villain, cursed academy).
- Format: Shorts (9:16, ~55 s, ~130 words) or normal videos (16:9 or 9:16, 5 to 7 min). Default 9:16 if unsure.
- Episode count for the arc (default 20) and seconds per panel (default 9; higher = fewer images to generate).
- Any must-have elements. If they have no idea, propose 3 original one-line hooks and let them pick.

## 3. Create and write
1. `python recap.py new --name <slug> --aspect <9:16|16:9> --title "<title>"`.
2. Write `stories/<slug>/bible.md` per `story-writing.md`: hook, rules and costs of the power system, cast, villain, twist, ending, episode arc (one cliffhanger line per episode).
3. Fill `characters.json`: fixed `style` line, `negative`, 3 to 5 characters with `name`, `say` (TTS pronunciation) and a 35 to 60 word `look`, plus 2 to 4 reusable `locations`.
4. Set `config.json` (aspect, scene_seconds, voice speed, music mood).
5. `python recap.py sheets --project stories/<slug>` and tell the user to generate and keep one reference image per main character (see `panel-prompts.md`).
6. Show a short summary (hook, cast, arc of the first 5 episodes) and ask whether to continue with `/write-episode` for episode 1.
