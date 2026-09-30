import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate, Easing } from "remotion";
import { PhoneMockup } from "../components/PhoneMockup";

export const Scene2AutoDND: React.FC = () => {
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

  // State switch timeline: Frame 80+ triggers entering mosque geofence
  const enterGeofenceProgress = interpolate(frame, [2.2 * fps, 3.2 * fps], [0, 1], {
    easing: Easing.bezier(0.34, 1.56, 0.64, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const dndActive = frame > 2.5 * fps;

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
      {/* Left Column: Text & Features */}
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
          Smart Geofencing
        </div>

        <h2
          style={{
            fontSize: 64,
            fontWeight: 800,
            color: "#FAF6ED",
            margin: 0,
            lineHeight: 1.15,
            fontFamily: "'Montserrat', sans-serif",
          }}
        >
          Automatic <span style={{ color: "#FFD700" }}>Mosque DND</span> Mode
        </h2>

        <p
          style={{
            fontSize: 26,
            color: "#C9D5CD",
            lineHeight: 1.5,
            margin: 0,
            fontFamily: "'Inter', sans-serif",
          }}
        >
          Background geofence & location monitoring automatically silences your ringtone when entering mosque boundaries.
        </p>

        {/* Highlight Callout Box */}
        <div
          style={{
            marginTop: 12,
            padding: 24,
            borderRadius: 20,
            backgroundColor: "rgba(15, 58, 34, 0.9)",
            borderLeft: "6px solid #FFD700",
            boxShadow: "0 10px 30px rgba(0,0,0,0.3)",
          }}
        >
          <div style={{ fontSize: 24, fontWeight: 700, color: "#FAF6ED", marginBottom: 6 }}>
            🚫 Zero Disruptions During Salah
          </div>
          <div style={{ fontSize: 20, color: "#9EBEA8" }}>
            Restores normal volume automatically when you leave the mosque!
          </div>
        </div>
      </div>

      {/* Right Column: Phone Mockup with Live DND State Animation */}
      <div
        style={{
          opacity: phoneOpacity,
          translate: `${phoneTranslateX}px 0px`,
        }}
      >
        <PhoneMockup title="MosqueDND" width={420} height={800}>
          <div style={{ padding: 20, display: "flex", flexDirection: "column", gap: 16 }}>
            {/* Geofence Status Header */}
            <div
              style={{
                padding: 16,
                borderRadius: 16,
                backgroundColor: dndActive ? "rgba(26, 92, 58, 0.8)" : "rgba(255, 255, 255, 0.05)",
                border: dndActive ? "1.5px solid #C9A227" : "1px solid rgba(255,255,255,0.1)",
                display: "flex",
                alignItems: "center",
                gap: 14,
              }}
            >
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: "50%",
                  backgroundColor: dndActive ? "#C9A227" : "#354A3D",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 22,
                }}
              >
                {dndActive ? "🕌" : "📍"}
              </div>
              <div>
                <div style={{ fontSize: 13, color: "#8EAD98", fontWeight: 600 }}>MONITORING STATUS</div>
                <div style={{ fontSize: 16, fontWeight: 700, color: "#FAF6ED" }}>
                  {dndActive ? "Inside Mosque Zone" : "Approaching Jamia Mosque (35m)"}
                </div>
              </div>
            </div>

            {/* Main State Card */}
            <div
              style={{
                padding: 24,
                borderRadius: 20,
                backgroundColor: dndActive ? "#14482B" : "#1B2B20",
                border: "2px solid",
                borderColor: dndActive ? "#FFD700" : "#365240",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: 16,
                textAlign: "center",
                boxShadow: dndActive ? "0 0 25px rgba(201, 162, 39, 0.3)" : "none",
              }}
            >
              <div
                style={{
                  width: 90,
                  height: 90,
                  borderRadius: "50%",
                  backgroundColor: dndActive ? "#C9A227" : "#2C3E32",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 48,
                  scale: dndActive ? enterGeofenceProgress : 1,
                }}
              >
                {dndActive ? "🔕" : "🔔"}
              </div>

              <div>
                <div style={{ fontSize: 24, fontWeight: 800, color: "#FAF6ED" }}>
                  {dndActive ? "DND MODE ACTIVE" : "RINGER ON"}
                </div>
                <div style={{ fontSize: 14, color: dndActive ? "#FFD700" : "#8EAD98", marginTop: 4 }}>
                  {dndActive ? "Calls & Notifications Silenced" : "Normal Sound Profile"}
                </div>
              </div>

              {/* Toggle switch visual */}
              <div
                style={{
                  width: 140,
                  height: 48,
                  borderRadius: 24,
                  backgroundColor: dndActive ? "#C9A227" : "#2E4234",
                  padding: 4,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: dndActive ? "flex-end" : "flex-start",
                }}
              >
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: "50%",
                    backgroundColor: "#FAF6ED",
                    boxShadow: "0 2px 6px rgba(0,0,0,0.4)",
                  }}
                />
              </div>
            </div>

            {/* Notification Banner when DND turns on */}
            {dndActive && (
              <div
                style={{
                  padding: 16,
                  borderRadius: 16,
                  backgroundColor: "#FAF6ED",
                  color: "#0A1F13",
                  display: "flex",
                  alignItems: "center",
                  gap: 12,
                  boxShadow: "0 10px 25px rgba(0,0,0,0.5)",
                  scale: enterGeofenceProgress,
                }}
              >
                <div style={{ fontSize: 28 }}>🕌</div>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 800 }}>MosqueDND Guard</div>
                  <div style={{ fontSize: 12, color: "#2B4734", fontWeight: 600 }}>
                    Auto-silenced phone for Jamia Mosque. Have a peaceful Salah!
                  </div>
                </div>
              </div>
            )}
          </div>
        </PhoneMockup>
      </div>
    </AbsoluteFill>
  );
};
