import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate, Easing } from "remotion";
import { IslamicStar } from "../components/IslamicStar";

export const Scene6Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Entrance animations
  const starScale = interpolate(frame, [0, 1.2 * fps], [0.4, 1], {
    easing: Easing.bezier(0.34, 1.56, 0.64, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const titleOpacity = interpolate(frame, [0.3 * fps, 1.2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const titleTranslateY = interpolate(frame, [0.3 * fps, 1.2 * fps], [40, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const subtitleOpacity = interpolate(frame, [0.8 * fps, 1.6 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const ctaScale = interpolate(frame, [1.5 * fps, 2.3 * fps], [0.85, 1], {
    easing: Easing.bezier(0.34, 1.56, 0.64, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  // Glowing pulse on CTA button
  const ctaGlow = interpolate(
    Math.sin(frame * 0.1),
    [-1, 1],
    [0.4, 0.9]
  );

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
      {/* Central Star Logo */}
      <div style={{ scale: starScale, marginBottom: 24 }}>
        <IslamicStar size={140} glow />
      </div>

      {/* Main Closing Headline */}
      <h2
        style={{
          fontSize: 68,
          fontWeight: 900,
          color: "#FAF6ED",
          margin: 0,
          lineHeight: 1.2,
          maxWidth: 1300,
          opacity: titleOpacity,
          translate: `0px ${titleTranslateY}px`,
          fontFamily: "'Montserrat', sans-serif",
          textShadow: "0 0 40px rgba(201, 162, 39, 0.3)",
        }}
      >
        Never miss a prayer. <br />
        <span style={{ color: "#FFD700" }}>Never interrupt a Salah.</span>
      </h2>

      {/* App Title */}
      <p
        style={{
          fontSize: 36,
          fontWeight: 600,
          color: "#C9D5CD",
          marginTop: 20,
          marginBottom: 40,
          opacity: subtitleOpacity,
          fontFamily: "'Inter', sans-serif",
        }}
      >
        MosqueDND • Your Peace of Mind During Worship
      </p>

      {/* CTA Button */}
      <div
        style={{
          scale: ctaScale,
          padding: "24px 60px",
          borderRadius: 40,
          backgroundColor: "#C9A227",
          backgroundImage: "linear-gradient(135deg, #FFE885 0%, #C9A227 50%, #8F6E10 100%)",
          color: "#0A1F13",
          fontSize: 32,
          fontWeight: 900,
          letterSpacing: 1,
          boxShadow: `0 15px 40px rgba(0,0,0,0.6), 0 0 30px rgba(255, 215, 0, ${ctaGlow})`,
          border: "2px solid #FFF",
          display: "flex",
          alignItems: "center",
          gap: 16,
        }}
      >
        <span>🕌</span>
        <span>GET MOSQUEDND TODAY</span>
      </div>

      {/* Feature Badges Summary */}
      <div
        style={{
          marginTop: 48,
          display: "flex",
          gap: 24,
          opacity: subtitleOpacity,
        }}
      >
        {["🔕 Auto Silence", "🕌 Exact Adhan", "🧭 3D Qibla", "🗺️ Smart Map"].map((badge, idx) => (
          <div
            key={idx}
            style={{
              padding: "10px 20px",
              borderRadius: 20,
              backgroundColor: "rgba(23, 72, 45, 0.6)",
              border: "1px solid rgba(201, 162, 39, 0.4)",
              color: "#FFD700",
              fontSize: 18,
              fontWeight: 700,
            }}
          >
            {badge}
          </div>
        ))}
      </div>
    </AbsoluteFill>
  );
};
