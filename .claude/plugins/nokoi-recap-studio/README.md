# Nokoi Recap Studio

Make **original manhwa-style recap videos** with Claude: story bible, episode scripts, consistent panel prompts, free local voice, and a finished MP4.

You generate the panel images with any AI image tool from the prompts Claude gives you; everything else is automated.

## Commands
| Command | What it does |
|---|---|
| `/new-story` | Installs the studio, creates a series: bible, characters, episode arc |
| `/write-episode` | Writes narration + `scenes.json` for the next episode |
| `/make-panels` | Builds the image prompts, imports and checks your images |
| `/render-recap` | Free local voiceover, music, SFX, captions, final MP4 |
| `/package-episode` | Titles, description, chapters, tags, thumbnail prompts |

Or just say: *"Make a new manhwa recap episode"* and the whole pipeline runs, pausing only when you need to generate images.

## What the video looks like
Your panels with slow Ken-Burns camera moves (zoom, pan, webtoon-style scroll), word-highlight captions, blue "[ SYSTEM ]" quest windows, pop-in headline text, screen shake and flash on impacts, synthesised SFX, ducked background music, 9:16 or 16:9, 720p to 4K.

## Requirements
Python 3.9+, ffmpeg, and (installed for you) Pillow, numpy, Piper TTS. No GPU, no paid API. Details: `skills/make-recap-video/references/install.md`.

## Install
Add this folder as a Claude plugin (Claude Code / Cowork), then run `/new-story`. Claude copies the toolkit into a `nokoi-recap-studio/` workspace folder and installs what is missing.

## Notes
- All stories must be original; the prompts never reference existing series or artists.
- Bundled font: Anton (SIL Open Font License). Voices are downloaded from the Piper voices repository on first use.
