import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate } from "remotion";
import { captions } from "./captions";

export const Captions: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const seconds = frame / fps;

  const active = captions.find((c) => seconds >= c.start && seconds <= c.end);
  if (!active) return null;

  const opacity = interpolate(
    seconds,
    [active.start, active.start + 0.25, active.end - 0.25, active.end],
    [0, 1, 1, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );

  return (
    <AbsoluteFill style={{ justifyContent: "flex-end", alignItems: "center", paddingBottom: 90 }}>
      <div
        style={{
          opacity,
          color: "white",
          fontFamily: "sans-serif",
          fontSize: 56,
          fontWeight: 700,
          padding: "12px 28px",
          borderRadius: 12,
          background: "rgba(0,0,0,0.55)",
        }}
      >
        {active.text}
      </div>
    </AbsoluteFill>
  );
};
