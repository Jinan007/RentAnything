#!/usr/bin/env python3
"""Nokoi Recap Studio CLI.  python recap.py <command> --help"""
import argparse, sys
from pathlib import Path


def main():
    from studio.common import project_path
    ap = argparse.ArgumentParser(prog="recap.py", description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)

    def proj(p, ep=True):
        p.add_argument("--project", required=True, help="story folder, e.g. stories/my-story (or just its name)")
        if ep:
            p.add_argument("--ep", type=int, required=True, help="episode number")

    d = sub.add_parser("doctor", help="check/install dependencies, download a voice or font")
    d.add_argument("--install", action="store_true"); d.add_argument("--voice"); d.add_argument("--fonts", action="store_true")
    n = sub.add_parser("new", help="create a story project")
    n.add_argument("--name", required=True); n.add_argument("--aspect", default="9:16", choices=["9:16", "16:9"]); n.add_argument("--title", default="")
    v = sub.add_parser("validate", help="check scenes.json"); proj(v)
    pr = sub.add_parser("prompts", help="write copy-paste image prompts for an episode"); proj(pr); pr.add_argument("--suffix", default="", help='e.g. "--ar 9:16 --v 7"')
    sh = sub.add_parser("sheets", help="character reference sheet prompts"); proj(sh, ep=False)
    im = sub.add_parser("import", help="copy a folder of downloaded images into scene_001.. order"); proj(im)
    im.add_argument("--from", dest="src", required=True); im.add_argument("--by", choices=["name", "mtime"], default="name")
    im.add_argument("--move", action="store_true"); im.add_argument("--dry-run", action="store_true")
    ck = sub.add_parser("check", help="verify every scene has a usable image"); proj(ck)
    t = sub.add_parser("tts", help="generate the voiceover"); proj(t)
    t.add_argument("--force", action="store_true"); t.add_argument("--test", help="speak this sentence to voice_test.wav"); t.add_argument("--engine")
    r = sub.add_parser("render", help="render the episode video"); proj(r)
    r.add_argument("--scenes", help="e.g. 1-8 or 3,5,9 (preview)"); r.add_argument("--scale", type=float, default=1.0)
    r.add_argument("--placeholders", action="store_true", help="fill missing images with numbered placeholders (draft timing)")
    r.add_argument("--workers", type=int); r.add_argument("--engine", help="override voice engine (piper|espeak|say|silent)"); r.add_argument("--force", action="store_true")
    s = sub.add_parser("still", help="export one frame of a scene as PNG"); proj(s)
    s.add_argument("--scene", type=int, required=True); s.add_argument("--p", type=float, default=0.5); s.add_argument("--out"); s.add_argument("--scale", type=float, default=0.5)
    c = sub.add_parser("contact", help="contact sheet PNG of a rendered video"); c.add_argument("--video", required=True); c.add_argument("--step", type=int, default=10)
    ch = sub.add_parser("chapters", help="YouTube chapter list from scene 'chapter' fields"); proj(ch)
    cl = sub.add_parser("clean", help="delete build cache for an episode"); proj(cl)
    sub.add_parser("selftest", help="render a tiny test video to prove the install works")
    a = ap.parse_args()

    if a.cmd == "doctor":
        from studio.doctor import cmd_doctor; return cmd_doctor(a.install, a.voice, a.fonts)
    if a.cmd == "new":
        from studio.doctor import cmd_new; return cmd_new(a.name, a.aspect, a.title)
    if a.cmd == "selftest":
        from studio.doctor import cmd_selftest; return cmd_selftest()
    if a.cmd == "contact":
        from studio.render import cmd_contact; return cmd_contact(a.video, a.step)
    project = str(project_path(a.project))
    if a.cmd == "validate":
        from studio.common import load_scenes, load_characters, validate_scenes, ep_dir
        _, sc = load_scenes(ep_dir(project, a.ep)); e, w = validate_scenes(sc, load_characters(project))
        [print("ERROR  ", x) for x in e]; [print("warning", x) for x in w]
        print(f"{len(sc)} scenes, {len(e)} errors, {len(w)} warnings"); sys.exit(1 if e else 0)
    if a.cmd == "prompts":
        from studio.prompts import cmd_prompts; return cmd_prompts(project, a.ep, a.suffix)
    if a.cmd == "sheets":
        from studio.prompts import cmd_sheets; return cmd_sheets(project)
    if a.cmd == "import":
        from studio.images import cmd_import; return cmd_import(project, a.ep, a.src, a.by, a.move, a.dry_run)
    if a.cmd == "check":
        from studio.images import cmd_check; sys.exit(0 if cmd_check(project, a.ep) else 1)
    if a.cmd == "tts":
        from studio.tts import cmd_tts; return cmd_tts(project, a.ep, a.force, a.test, a.engine)
    if a.cmd == "render":
        from studio.render import cmd_render; return cmd_render(project, a.ep, a.scenes, a.scale, a.placeholders, a.workers, a.engine, a.force)
    if a.cmd == "still":
        from studio.render import cmd_still; return cmd_still(project, a.ep, a.scene, a.p, a.out, a.scale)
    if a.cmd == "chapters":
        from studio.render import cmd_chapters; return cmd_chapters(project, a.ep)
    if a.cmd == "clean":
        import shutil
        from studio.common import ep_dir
        shutil.rmtree(ep_dir(project, a.ep) / "build", ignore_errors=True); print("build cache cleared")


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    main()
