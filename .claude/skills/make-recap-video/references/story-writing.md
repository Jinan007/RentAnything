# Writing original manhwa recap stories

## Originality and safety (non-negotiable)
- Everything is invented: names, systems, worlds, plot beats. Never reuse characters, titles, scenes or distinctive systems from existing manhwa, anime, games or novels, and never write "in the style of [series/artist]" in image prompts. Use generic words: "manhwa webtoon illustration".
- Keep it advertiser-friendly: violence non-graphic, no sexual content, no romance involving minors, no real people or real tragedies, no hateful content.
- Every episode needs genuinely new story and narration (YouTube's "repetitious/mass-produced content" rule). Never recycle one script with swapped names.

## Story bible (`bible.md`) once per series
Title, one-line hook, genre blend, tone, setting, the **power/system rules and its limits and costs** (limits create tension), main character (want, flaw, secret), 3 to 5 supporting characters (role, relationship, secret), main antagonist, midpoint twist, ending, then an **episode arc**: one line per episode, 20 to 30 episodes, each ending on a cliffhanger. Choose a hook that fits a thumbnail ("The weakest hunter gets the only skill that levels up in secret").
Popular original angles: regression/second chance, hidden-system underdog, tower/dungeon climbing, reincarnated villain, cursed academy, revenge with rules. Mix two and add a twist of your own.

## Character `look` strings (consistency lives here)
35 to 60 words, always the same order: age + gender, hair (colour, style, ONE distinctive feature), eyes, face mark/scar, outfit (colours + 2 or 3 items), signature accessory/weapon. Concrete nouns and colours, no vague words ("handsome"), no names of real people or existing characters. Never edit a look mid-series; a costume change is a new character key.

## Episode narration rules
- Recap voice: present tense, short punchy sentences (6 to 18 words), "he/she" more than names, one idea per sentence.
- Length: 800 to 1100 words is ~5 to 7 minutes at ~155 wpm. For 9:16 Shorts aim for 120 to 150 words (~55 s).
- Structure: hook in the first two sentences (a shocking line, not a greeting) → setup → escalation → reveal/power-up → cliffhanger final line. A new twist or power moment roughly every 45 to 60 seconds.
- No filler ("so basically", "as you can see"), no summary of the previous episode longer than one sentence, no "like and subscribe" inside scenes (add it only if the user wants an outro scene).
- Names must be TTS-friendly. If a name is hard to say, add `say` in characters.json.

## Scenes
- One scene = one image. Target `scene_seconds` (default 9 s ~ 23 words). Hard limit 40 words per scene. Rapid action: 4 to 6 s scenes with short lines; emotional beats: 10 to 12 s.
- Vary the camera: never the same move 3 times in a row. `zoom_in` for tension, `zoom_out` for reveals, `pan_*` for establishing/wide shots, `pan_down` + `tall` for dramatic vertical scrolls, `drift` as the neutral default.
- Use `shake` + `impact` on hits, `flash` + `boom` on power-ups, `whoosh` on fast movement, `heartbeat` on dread, `riser` before a reveal, `shimmer` on magic. At most one fx every ~5 scenes, or it stops feeling special.
- `overlay.system` for system-window moments (quests, stats, skills), `overlay.bigtext` for one-word punches (LEVEL UP, BETRAYED). Keep overlay text under 25 words.
- Reuse an image (`"image": "scene_004.png"`) when two consecutive lines happen in the same shot. It saves the user real work.
- Add `chapter` to ~5 scenes per long episode (YouTube chapters).
- Describe `action` as something visible in a still image. Write what the picture shows, not the plot.
