import { ThreeCanvas } from "@remotion/three";
import { useCurrentFrame, useVideoConfig, interpolate } from "remotion";
import { useMemo } from "react";

// A rotating three.js object driven by the Remotion frame counter,
// so every frame is deterministic and renders identically.
const RotatingBox: React.FC = () => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const rotationY = frame * 0.03;
  const scale = interpolate(frame, [0, 30, durationInFrames], [0.6, 1, 1.1], {
    extrapolateRight: "clamp",
  });

  const color = useMemo(() => "#4f9dff", []);

  return (
    <mesh rotation={[0.4, rotationY, 0]} scale={scale}>
      <boxGeometry args={[1.6, 1.6, 1.6]} />
      <meshStandardMaterial color={color} roughness={0.35} metalness={0.2} />
    </mesh>
  );
};

export const ThreeScene: React.FC = () => {
  const { width, height } = useVideoConfig();

  return (
    <ThreeCanvas width={width} height={height} camera={{ fov: 45, position: [0, 0, 5] }}>
      <ambientLight intensity={0.6} />
      <directionalLight position={[3, 5, 4]} intensity={1.2} />
      <RotatingBox />
    </ThreeCanvas>
  );
};
