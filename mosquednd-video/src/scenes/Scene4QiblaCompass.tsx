import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate, Easing } from "remotion";
import { PhoneMockup } from "../components/PhoneMockup";

export const Scene4QiblaCompass: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Entrance animations
  const textTranslateX = interpolate(frame, [0, 1.2 * fps], [-60, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const textOpacity = interpolate(frame, [0, 1.2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const phoneTranslateX = interpolate(frame, [0, 1.2 * fps], [60, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const phoneOpacity = interpolate(frame, [0, 1.2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  // Compass rotation animation: turns towards Qibla angle (260deg)
  const compassHeading = interpolate(frame, [0.5 * fps, 3.5 * fps], [120, 260], {
    easing: Easing.bezier(0.25, 1, 0.5, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const isAligned = Math.abs(compassHeading - 260) < 3;

  // Alignment glow animation
  const glowOpacity = interpolate(
    Math.sin(frame * 0.15),
    [-1, 1],
    [0.5, 1]
  );

  return (
    <AbsoluteFill
      style={{
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0 120px",
      }}
    >
      {/* Left Column: Title & Features */}
      <div
        style={{
          width: 750,
          opacity: textOpacity,
          translate: `${textTranslateX}px 0px`,
          display: "flex",
          flexDirection: "column",
          gap: 24,
        }}
      >
        <div
          style={{
            alignSelf: "flex-start",
            padding: "8px 20px",
            borderRadius: 20,
            backgroundColor: "rgba(201, 162, 39, 0.15)",
            border: "1px solid #C9A227",
            color: "#FFD700",
            fontSize: 20,
            fontWeight: 700,
            letterSpacing: 1.5,
            textTransform: "uppercase",
          }}
        >
          3D Sensor Compass
        </div>

        <h2
          style={{
            fontSize: 60,
            fontWeight: 800,
            color: "#FAF6ED",
            margin: 0,
            lineHeight: 1.15,
            fontFamily: "'Montserrat', sans-serif",
          }}
        >
          Emerald & Gold <span style={{ color: "#FFD700" }}>3D Qibla</span> Compass
        </h2>

        <p
          style={{
            fontSize: 24,
            color: "#C9D5CD",
            lineHeight: 1.5,
            margin: 0,
            fontFamily: "'Inter', sans-serif",
          }}
        >
          Dual-orientation (horizontal & vertical) tilt compass engineered with low-pass noise filter and visual alignment glow.
        </p>

        {/* Feature Highlights */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {[
            { title: "🕋 Kaaba Direction Indicator", desc: "Great-circle bearing calculated precisely from your GPS location" },
            { title: "📱 Dual Tilt Support", desc: "Seamless accuracy whether holding device flat or vertically" },
            { title: "✨ Haptic & Visual Glow", desc: "Instant feedback when phone aligns perfectly with Qibla" },
          ].map((item, idx) => (
            <div
              key={idx}
              style={{
                padding: "16px 20px",
                borderRadius: 16,
                backgroundColor: "rgba(15, 58, 34, 0.8)",
                border: "1px solid rgba(201, 162, 39, 0.3)",
              }}
            >
              <div style={{ fontSize: 20, fontWeight: 700, color: "#FFD700" }}>{item.title}</div>
              <div style={{ fontSize: 16, color: "#9EBEA8", marginTop: 4 }}>{item.desc}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Right Column: Phone Mockup with 3D Compass */}
      <div
        style={{
          opacity: phoneOpacity,
          translate: `${phoneTranslateX}px 0px`,
        }}
      >
        <PhoneMockup title="Qibla Finder" width={420} height={800}>
          <div
            style={{
              padding: 20,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "space-around",
              height: "100%",
            }}
          >
            {/* Header info */}
            <div style={{ textAlign: "center" }}>
              <div style={{ fontSize: 14, color: "#8EAD98", fontWeight: 600 }}>QIBLA BEARING</div>
              <div style={{ fontSize: 32, fontWeight: 900, color: "#FFD700" }}>
                {Math.round(compassHeading)}° W
              </div>
            </div>

            {/* 3D Compass Dial */}
            <div
              style={{
                width: 280,
                height: 280,
                borderRadius: "50%",
                background: "radial-gradient(circle, #134227 0%, #0A1F13 70%)",
                border: "4px solid #C9A227",
                boxShadow: isAligned
                  ? `0 0 50px rgba(255, 215, 0, ${glowOpacity}), inset 0 0 30px rgba(255, 215, 0, 0.3)`
                  : "0 10px 30px rgba(0,0,0,0.6)",
                position: "relative",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                rotate: `${-compassHeading}deg`,
              }}
            >
              {/* Outer Ring Ticks */}
              {[...Array(12)].map((_, i) => (
                <div
                  key={i}
                  style={{
                    position: "absolute",
                    width: 2,
                    height: i % 3 === 0 ? 16 : 8,
                    backgroundColor: i % 3 === 0 ? "#FFD700" : "rgba(201, 162, 39, 0.4)",
                    top: 10,
                    left: "50%",
                    transformOrigin: "0px 130px",
                    rotate: `${i * 30}deg`,
                  }}
                />
              ))}

              {/* Cardinal Labels */}
              <div style={{ position: "absolute", top: 22, color: "#FFD700", fontWeight: 800, fontSize: 16 }}>N</div>
              <div style={{ position: "absolute", right: 22, color: "#FAF6ED", fontWeight: 700, fontSize: 14 }}>E</div>
              <div style={{ position: "absolute", bottom: 22, color: "#FAF6ED", fontWeight: 700, fontSize: 14 }}>S</div>
              <div style={{ position: "absolute", left: 22, color: "#FAF6ED", fontWeight: 700, fontSize: 14 }}>W</div>

              {/* Central Rub' el Hizb Pattern */}
              <div
                style={{
                  width: 100,
                  height: 100,
                  borderRadius: "50%",
                  border: "2px solid rgba(201, 162, 39, 0.5)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <div style={{ width: 40, height: 40, backgroundColor: "#C9A227", rotate: "45deg", borderRadius: 4 }} />
              </div>

              {/* 3D Gold Qibla Arrow pointing to 260deg (Kaaba) */}
              <div
                style={{
                  position: "absolute",
                  width: 14,
                  height: 200,
                  top: 40,
                  rotate: "260deg",
                  transformOrigin: "center center",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  zIndex: 20,
                }}
              >
                {/* Arrowhead Kaaba Icon */}
                <div
                  style={{
                    width: 32,
                    height: 32,
                    backgroundColor: "#111",
                    border: "2px solid #FFD700",
                    borderRadius: 4,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    boxShadow: "0 0 15px #FFD700",
                    marginTop: -16,
                  }}
                >
                  <span style={{ fontSize: 16 }}>🕋</span>
                </div>
                {/* Needle shaft */}
                <div style={{ flex: 1, width: 6, backgroundColor: "#FFD700", borderRadius: 3 }} />
              </div>
            </div>

            {/* Alignment Status Pill */}
            <div
              style={{
                padding: "10px 24px",
                borderRadius: 24,
                backgroundColor: isAligned ? "#1A5C3A" : "rgba(255,255,255,0.05)",
                border: isAligned ? "2px solid #FFD700" : "1px solid rgba(255,255,255,0.1)",
                color: isAligned ? "#FFD700" : "#8EAD98",
                fontSize: 16,
                fontWeight: 800,
                letterSpacing: 0.5,
                boxShadow: isAligned ? "0 0 20px rgba(255, 215, 0, 0.4)" : "none",
              }}
            >
              {isAligned ? "Aligned with Qibla ✨" : "Rotate phone towards Qibla"}
            </div>
          </div>
        </PhoneMockup>
      </div>
    </AbsoluteFill>
  );
};
