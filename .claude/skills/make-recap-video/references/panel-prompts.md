# Generating panel images (the user does this part)

1. `python recap.py sheets --project <p>` writes `character_sheet_prompts.md`. The user generates ONE reference sheet per main character and keeps the best.
2. `python recap.py prompts --project <p> --ep N` writes `prompts/epNN_prompts.md` (prompt per scene with its target filename) and `.txt` (one prompt per line in order, for bulk tools). Add `--suffix "--ar 9:16"` for tools that take flags in the prompt.
3. In the image tool: set aspect ratio to the frame (9:16 or 16:9), attach the character reference image whenever the scene has that character (look for "character reference", "image reference" or "reference image" options), paste the prompt, generate, pick the best of the variations.
4. Save as `scene_001.png`, `scene_002.png`... into `episodes/epNN/images/`. If the tool downloads random names, put them in one folder in order and run `python recap.py import --project <p> --ep N --from <folder>` (use `--by mtime` if order is download time; add `--dry-run` first).
5. `python recap.py check --project <p> --ep N` lists missing files, tiny images and bad aspect ratios.
6. A bad panel: regenerate only that scene, overwrite its file, re-render (only that scene re-renders).

Tips to pass on:
- Ask for no text in images (captions and overlays are added by the studio); the negative prompt already says so.
- Vertical videos: leave the lower third quiet (captions). The prompt already asks for it.
- Resolution: anything 1080 px wide or more is enough. Larger is fine.
- If faces drift between panels, regenerate with the reference attached and keep the `look` text unchanged.
- To save effort: use `image` reuse, fewer scenes (raise `scene_seconds` to 11 or 12), and let `pan_*` moves do the work on wide images.
- Never paste artist names or existing series names into prompts.
