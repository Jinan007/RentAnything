import { AbsoluteFill } from "remotion";
import { ThreeScene } from "./ThreeScene";
import { Captions } from "./Captions";

export const MAIN_SCENE_DURATION_FRAMES = 30 * 8; // 8 seconds at 30 fps

export const MainScene: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: "#0b0d12" }}>
      <ThreeScene />
      <Captions />
    </AbsoluteFill>
  );
};
