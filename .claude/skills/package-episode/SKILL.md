---
name: package-episode
description: >
  This skill should be used when the user runs /package-episode or asks for "titles", "description", "tags",
  "thumbnail", "chapters", "pinned comment", or "Shorts cut" for a finished Nokoi Recap episode.
metadata:
  version: "1.0.0"
  author: Nokoi Recap
---

# /package-episode: upload kit

Reference: `../make-recap-video/references/youtube-packaging.md`.

1. Read the episode's `script.md`, `scenes.json` and `bible.md`. If the video is rendered, run `python recap.py chapters --project stories/<slug> --ep N` for timestamps (and tell the user to add `chapter` fields and re-render if none exist).
2. Produce the full kit from the reference (5 titles, description with chapters, tags, 3 thumbnail concepts with image prompts and overlay text, pinned comment, Shorts cut suggestion) in one reply.
3. Keep every claim truthful to the episode and original: no other creators' series names in titles or tags.
4. Offer to save the kit as `episodes/epNN/youtube.md`.
