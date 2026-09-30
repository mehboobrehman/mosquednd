import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate, Easing } from "remotion";
import { IslamicStar } from "../components/IslamicStar";

export const Scene1Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Entrance animations using interpolate and bezier easing
  const starScale = interpolate(frame, [0, 1.2 * fps], [0.3, 1], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const starRotate = interpolate(frame, [0, 6 * fps], [0, 45], {
    extrapolateRight: "clamp",
  });

  const titleOpacity = interpolate(frame, [0.5 * fps, 1.5 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const titleTranslateY = interpolate(frame, [0.5 * fps, 1.5 * fps], [40, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const subtitleOpacity = interpolate(frame, [1 * fps, 2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const subtitleTranslateY = interpolate(frame, [1 * fps, 2 * fps], [30, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const badgeOpacity = interpolate(frame, [1.8 * fps, 2.8 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const badgeScale = interpolate(frame, [1.8 * fps, 2.8 * fps], [0.8, 1], {
    easing: Easing.bezier(0.34, 1.56, 0.64, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  return (
    <AbsoluteFill
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "0 100px",
        textAlign: "center",
      }}
    >
      {/* Central Animated Gold Star Logo */}
      <div
        style={{
          scale: starScale,
          rotate: `${starRotate}deg`,
          marginBottom: 30,
        }}
      >
        <IslamicStar size={160} glow />
      </div>

      {/* Main Title */}
      <h1
        style={{
          fontSize: 96,
          fontWeight: 900,
          color: "#FAF6ED",
          margin: 0,
          letterSpacing: 2,
          opacity: titleOpacity,
          translate: `0px ${titleTranslateY}px`,
          textShadow: "0 0 30px rgba(201, 162, 39, 0.4)",
          fontFamily: "'Montserrat', 'Inter', sans-serif",
        }}
      >
        Mosque<span style={{ color: "#FFD700" }}>DND</span>
      </h1>

      {/* Tagline */}
      <p
        style={{
          fontSize: 42,
          fontWeight: 600,
          color: "#C9A227",
          marginTop: 16,
          marginBottom: 48,
          maxWidth: 1000,
          opacity: subtitleOpacity,
          translate: `0px ${subtitleTranslateY}px`,
          letterSpacing: 1,
          fontFamily: "'Montserrat', 'Inter', sans-serif",
        }}
      >
        The Smart Islamic Companion & Auto-DND App
      </p>

      {/* Feature Pills */}
      <div
        style={{
          display: "flex",
          gap: 20,
          flexWrap: "wrap",
          justifyContent: "center",
          opacity: badgeOpacity,
          scale: badgeScale,
        }}
      >
        {[
          "🔕 Auto Mosque Silence",
          "🕌 Precise Prayer Times",
          "🧭 3D Emerald Qibla",
          "🗺️ Offline Detection",
        ].map((feature, idx) => (
          <div
            key={idx}
            style={{
              padding: "16px 28px",
              borderRadius: 30,
              backgroundColor: "rgba(23, 72, 45, 0.85)",
              border: "2px solid #C9A227",
              color: "#FAF6ED",
              fontSize: 22,
              fontWeight: 700,
              boxShadow: "0 10px 25px rgba(0, 0, 0, 0.4)",
            }}
          >
            {feature}
          </div>
        ))}
      </div>
    </AbsoluteFill>
  );
};
