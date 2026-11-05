# RentAnything

## 3D animation + video stack

- **three.js** (`three`, `@react-three/fiber`, `@remotion/three`) for the 3D scene in `src/ThreeScene.tsx`
- **Remotion** (`remotion`, `@remotion/cli`, `@remotion/player`) for timing, captions, audio and MP4 rendering
- **React 19** and **TypeScript** as the glue

Versions are pinned exactly in `package.json`.

### Commands

```bash
npm install          # install dependencies
npm run studio       # open the Remotion Studio preview in the browser
npm run still        # render one frame to out/still.png
npm run render       # render the full video to out/MainScene.mp4
npm run typecheck    # TypeScript check
```

### Where to edit

- `src/ThreeScene.tsx`: the three.js scene. Animate it from `useCurrentFrame()` so renders are deterministic.
- `src/captions.ts`: caption text and timings in seconds.
- `src/MainScene.tsx`: combines the scene, captions and audio. Set the length with `MAIN_SCENE_DURATION_FRAMES`.
- `src/Root.tsx`: composition settings (fps, width, height).

### Adding audio

Put a file at `public/audio/voiceover.mp3`, then add this inside `MainScene`:

```tsx
import { Audio, staticFile } from "remotion";
<Audio src={staticFile("audio/voiceover.mp3")} />
```

### Browser note

Remotion downloads its own headless Chrome the first time you render. If that download is blocked, `remotion.config.ts` falls back to `/opt/pw-browsers/chromium` when that path exists.
