import { Composition } from "remotion";
import { MainScene, MAIN_SCENE_DURATION_FRAMES } from "./MainScene";

export const RemotionRoot: React.FC = () => {
  return (
    <Composition
      id="MainScene"
      component={MainScene}
      durationInFrames={MAIN_SCENE_DURATION_FRAMES}
      fps={30}
      width={1920}
      height={1080}
    />
  );
};
