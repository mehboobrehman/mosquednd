import React from "react";
import { AbsoluteFill, useCurrentFrame, interpolate } from "remotion";

export const Background: React.FC = () => {
  const frame = useCurrentFrame();

  // Subtle pulsing ambient light
  const pulse = interpolate(
    Math.sin(frame * 0.05),
    [-1, 1],
    [0.7, 1]
  );

  // Slow rotation for background geometric pattern
  const patternRotation = frame * 0.08;

  return (
    <AbsoluteFill
      style={{
        backgroundColor: "#0A1F13",
        backgroundImage: "radial-gradient(circle at 50% 30%, #17482D 0%, #0A1F13 70%, #05120B 100%)",
        overflow: "hidden",
      }}
    >
      {/* Background radial glow */}
      <div
        style={{
          position: "absolute",
          top: "50%",
          left: "50%",
          width: 1200,
          height: 1200,
          marginTop: -600,
          marginLeft: -600,
          borderRadius: "50%",
          background: "radial-gradient(circle, rgba(201, 162, 39, 0.12) 0%, rgba(26, 92, 58, 0.18) 40%, rgba(0,0,0,0) 70%)",
          opacity: pulse,
          pointerEvents: "none",
        }}
      />

      {/* Decorative Islamic Geometric Pattern Overlay */}
      <svg
        width="100%"
        height="100%"
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          opacity: 0.06,
          rotate: `${patternRotation}deg`,
          scale: 1.2,
        }}
      >
        <pattern
          id="islamic-grid"
          width="120"
          height="120"
          patternUnits="userSpaceOnUse"
        >
          <path
            d="M 60 0 L 120 60 L 60 120 L 0 60 Z"
            fill="none"
            stroke="#C9A227"
            strokeWidth="1.5"
          />
          <circle cx="60" cy="60" r="30" fill="none" stroke="#C9A227" strokeWidth="1" />
          <path
            d="M 60 15 L 105 60 L 60 105 L 15 60 Z"
            fill="none"
            stroke="#FFD700"
            strokeWidth="1"
          />
          <rect
            x="30"
            y="30"
            width="60"
            height="60"
            fill="none"
            stroke="#C9A227"
            strokeWidth="1"
          />
        </pattern>
        <rect width="100%" height="100%" fill="url(#islamic-grid)" />
      </svg>

      {/* Floating particles */}
      {[...Array(12)].map((_, i) => {
        const speed = 0.5 + (i % 3) * 0.3;
        const posY = ((frame * speed * 2 + i * 90) % 1180) - 50;
        const posX = 150 + (i * 140) % 1620;
        const particleOpacity = interpolate(
          posY,
          [0, 200, 800, 1080],
          [0, 0.4, 0.4, 0]
        );

        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: posX,
              top: posY,
              width: 6 + (i % 4) * 2,
              height: 6 + (i % 4) * 2,
              borderRadius: "50%",
              backgroundColor: i % 2 === 0 ? "#C9A227" : "#FFD700",
              boxShadow: "0 0 10px rgba(255, 215, 0, 0.8)",
              opacity: particleOpacity,
            }}
          />
        );
      })}
    </AbsoluteFill>
  );
};
