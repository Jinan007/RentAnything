---
name: make-panels
description: >
  This skill should be used when the user runs /make-panels or asks to "make the image prompts", "give me the
  prompts for the panels", "generate prompts for episode 2", "I uploaded the images", "import my images", or
  "check my panels" in the Nokoi Recap Studio. It builds consistent prompts from characters.json and imports and
  checks the images the user generates.
metadata:
  version: "1.0.0"
  author: Nokoi Recap
---

# /make-panels: prompts out, images in

Reference: `../make-recap-video/references/panel-prompts.md`.

**If images are not made yet**
1. Confirm `scenes.json` validates (`python recap.py validate ...`).
2. If `character_sheet_prompts.md` has not been used, run `python recap.py sheets --project stories/<slug>` first and ask the user to make the reference sheets.
3. `python recap.py prompts --project stories/<slug> --ep N` (add `--suffix "--ar 9:16"` etc. only if the user names a tool that needs flags). Give the user the paths to `prompts/epNN_prompts.md` (with filenames) and `.txt` (bulk), and the how-to from the reference in 5 short lines, including the count of images and the filename pattern.

**If the user says images are uploaded**
1. Find where: already in `episodes/epNN/images/`, or in another folder / an upload. For random filenames run `python recap.py import ... --from <folder> --dry-run`, show the mapping, then run it for real (`--by mtime` if they say download order).
2. `python recap.py check ...`. Report missing files by scene number, small or badly-shaped images, and offer to regenerate prompts for specific scenes. Do not render until `check` is clean (or the user chooses `--placeholders` for a draft).
3. When clean, offer `/render-recap`.
