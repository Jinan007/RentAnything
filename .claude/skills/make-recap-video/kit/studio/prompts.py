"""Build copy-paste image prompts from scenes.json + characters.json (keeps characters consistent)."""
from pathlib import Path
from .common import load_config, load_characters, load_scenes, ep_dir, die, validate_scenes

DEFAULT_STYLE = ("manhwa webtoon illustration, clean sharp lineart, cel shading with painterly highlights, "
                 "vivid cinematic colors, dramatic lighting, highly detailed, original characters")
DEFAULT_NEGATIVE = ("text, letters, watermark, signature, speech bubbles, logo, extra fingers, deformed hands, "
                    "extra limbs, blurry, low quality, cropped face")

SHOTS = {
    "wide": "wide establishing shot",
    "medium": "medium shot",
    "close": "close-up shot",
    "extreme_close": "extreme close-up on the eyes",
    "low_angle": "dramatic low-angle shot looking up",
    "high_angle": "high-angle shot looking down",
    "over_shoulder": "over-the-shoulder shot",
    "action": "dynamic action shot, dutch angle, motion blur streaks",
    "insert": "detail insert shot of the key object",
}
MOODS = {
    "calm": "calm quiet atmosphere, soft warm light",
    "tense": "tense atmosphere, harsh shadows, desaturated palette with one red accent",
    "epic": "epic heroic atmosphere, glowing rim light, dramatic clouds",
    "sad": "sorrowful mood, cold blue tones, falling rain or dust motes",
    "mysterious": "mysterious mood, fog, teal and violet glow",
    "romantic": "romantic mood, soft pink and gold light, gentle bokeh",
    "dark": "dark ominous mood, deep shadows, faint red glow",
    "action": "high-energy action, speed lines, bright impact flashes",
    "comedic": "light comedic mood, bright flat colors, exaggerated expressions",
    "triumphant": "triumphant mood, golden backlight, lens flare",
}
ASPECT_TXT = {
    "9:16": "vertical portrait composition, main subject in the upper two thirds, lower third kept visually quiet",
    "16:9": "wide cinematic landscape composition, main subject slightly off-center",
}


def build_prompt(scene, chars, cfg):
    style = chars.get("style") or DEFAULT_STYLE
    bits = [style, SHOTS.get(scene.get("shot", "medium"), SHOTS["medium"])]
    setting = scene.get("setting", "")
    setting = chars.get("locations", {}).get(setting, setting)
    if setting:
        bits.append(setting)
    cdata = chars.get("characters", {})
    for key in scene.get("characters", [])[:3]:
        c = cdata.get(key)
        if c:
            bits.append(f"{c.get('name', key)} ({c['look']})")
    if scene.get("action"):
        bits.append(scene["action"])
    if scene.get("mood") in MOODS:
        bits.append(MOODS[scene["mood"]])
    tall = scene.get("tall")
    bits.append(ASPECT_TXT.get(cfg["aspect"], ""))
    if tall:
        bits.append("extra tall panel, long vertical scene that can be scrolled down")
    return ", ".join(b.strip().rstrip(",") for b in bits if b)


def image_name(scene):
    return scene.get("image") or f"scene_{int(scene['id']):03d}.png"


def needs_image(scene):
    """Scenes that point to another scene's image (image: 'scene_004.png' already used) don't need a new one."""
    return not scene.get("image")


def cmd_prompts(project, ep, suffix=""):
    cfg = load_config(project)
    chars = load_characters(project)
    meta, scenes = load_scenes(ep_dir(project, ep))
    errs, warns = validate_scenes(scenes, chars)
    if errs:
        die("fix scenes.json first:\n  " + "\n  ".join(errs))
    out_dir = ep_dir(project, ep) / "prompts"
    out_dir.mkdir(parents=True, exist_ok=True)
    neg = chars.get("negative") or DEFAULT_NEGATIVE
    todo = [s for s in scenes if needs_image(s)]
    ratio = "9:16" if cfg["aspect"] == "9:16" else "16:9"
    lines = [f"# {meta.get('title') or 'Episode %s' % ep} — image prompts", "",
             f"- Generate **{len(todo)} images** at aspect ratio **{ratio}** (taller is fine for 'tall' scenes).",
             f"- Save each as the filename shown (PNG or JPG), put them all in `{ep_dir(project, ep) / 'images'}`.",
             "- Keep ONE character reference image attached in your image tool for every prompt with that character.",
             f"- Negative prompt (if your tool has one): `{neg}`", ""]
    flat = []
    for s in todo:
        p = build_prompt(s, chars, cfg) + (f" {suffix}" if suffix else "")
        flat.append(p)
        lines += [f"## {image_name(s)}", f"*Narration:* {s['narration'][:140]}", "", f"```\n{p}\n```", ""]
    (out_dir / f"ep{int(ep):02d}_prompts.md").write_text("\n".join(lines), encoding="utf-8")
    (out_dir / f"ep{int(ep):02d}_prompts.txt").write_text("\n".join(flat) + "\n", encoding="utf-8")
    print(f"{len(todo)} prompts written to:\n  {out_dir / ('ep%02d_prompts.md' % int(ep))}\n  {out_dir / ('ep%02d_prompts.txt' % int(ep))} (one prompt per line, same order)")
    for w in warns:
        print("  warning:", w)


def cmd_sheets(project):
    chars = load_characters(project)
    cdata = chars.get("characters", {})
    if not cdata:
        die("characters.json has no characters yet.")
    style = chars.get("style") or DEFAULT_STYLE
    out = [f"# Character reference sheets — {project}", "",
           "Generate ONE of each, pick the best, then attach it as the reference image for every panel prompt.", ""]
    for key, c in cdata.items():
        p = (f"{style}, character reference sheet, full body front view, three-quarter view and side view of the same "
             f"character, plain light grey background, even flat lighting, {c.get('name', key)} ({c['look']})")
        out += [f"## sheet_{key}.png", "```", p, "```", ""]
    path = Path(project) / "character_sheet_prompts.md"
    path.write_text("\n".join(out), encoding="utf-8")
    print(f"character sheet prompts -> {path}")
