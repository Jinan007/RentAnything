---
name: write-episode
description: >
  This skill should be used when the user runs /write-episode or asks to "write episode 3", "write the next
  episode", "turn the arc into a script", "write the narration", or "split the script into scenes" for a story
  in the Nokoi Recap Studio. It writes the recap narration and the structured scenes.json.
metadata:
  version: "1.0.0"
  author: Nokoi Recap
---

# /write-episode: narration → scenes.json

References: `../make-recap-video/references/story-writing.md` and `scenes-schema.md`.

1. Read `stories/<slug>/bible.md`, `characters.json`, `config.json`, and the previous episode's `script.md` (last lines) for continuity. If several stories exist, ask which; if the episode number is missing, use the next unused one.
2. Pick the length from the format (Short ~130 words, long 800 to 1100 words). Write `episodes/epNN/script.md`: the full spoken narration only, following the recap rules (hook, escalation, reveal, cliffhanger) and the arc line for this episode.
3. Split into scenes sized by `scene_seconds` (words per scene ~ seconds x 2.6, max 40). For each scene fill the schema fields: `characters` (keys from characters.json), `setting`, visible `action`, `shot`, `mood`, varied `camera`, sparing `fx`/`sfx`, `overlay` for system windows or punch words, `chapter` on ~5 scenes, `image` reuse for same-shot neighbours. Use `locations` keys when possible.
4. Save `episodes/epNN/scenes.json`, then run `python recap.py validate --project stories/<slug> --ep N` and fix every error. Report scene count, estimated runtime (words / 155 wpm) and how many images the user will need (scenes without `image`).
5. Show the opening hook and the cliffhanger line and ask whether to adjust or continue with `/make-panels`.
