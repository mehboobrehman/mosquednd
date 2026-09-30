import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate, Easing } from "remotion";
import { PhoneMockup } from "../components/PhoneMockup";

export const Scene5MosqueMap: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Entrance animations
  const phoneTranslateX = interpolate(frame, [0, 1.2 * fps], [-60, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const phoneOpacity = interpolate(frame, [0, 1.2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const textTranslateX = interpolate(frame, [0, 1.2 * fps], [60, 0], {
    easing: Easing.bezier(0.16, 1, 0.3, 1),
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const textOpacity = interpolate(frame, [0, 1.2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  // Pulse animation for user GPS dot
  const pulseRadius = interpolate(
    Math.sin(frame * 0.1),
    [-1, 1],
    [10, 24]
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
      {/* Left Column: Phone Mockup with Map & Data Management UI */}
      <div
        style={{
          opacity: phoneOpacity,
          translate: `${phoneTranslateX}px 0px`,
        }}
      >
        <PhoneMockup title="Mosque Map & Data" width={420} height={800}>
          <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 14 }}>
            {/* Map Preview Canvas */}
            <div
              style={{
                height: 380,
                borderRadius: 20,
                backgroundColor: "#0D2619",
                border: "2px solid #C9A227",
                position: "relative",
                overflow: "hidden",
                boxShadow: "0 10px 25px rgba(0,0,0,0.5)",
              }}
            >
              {/* Map grid lines */}
              <svg width="100%" height="100%" style={{ position: "absolute", opacity: 0.15 }}>
                <pattern id="map-grid" width="40" height="40" patternUnits="userSpaceOnUse">
                  <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#C9A227" strokeWidth="1" />
                </pattern>
                <rect width="100%" height="100%" fill="url(#map-grid)" />
              </svg>

              {/* User Location Pulse */}
              <div
                style={{
                  position: "absolute",
                  top: "55%",
                  left: "50%",
                  transform: "translate(-50%, -50%)",
                  width: pulseRadius * 2,
                  height: pulseRadius * 2,
                  borderRadius: "50%",
                  backgroundColor: "rgba(255, 215, 0, 0.25)",
                  border: "2px solid #FFD700",
                }}
              />
              <div
                style={{
                  position: "absolute",
                  top: "55%",
                  left: "50%",
                  transform: "translate(-50%, -50%)",
                  width: 14,
                  height: 14,
                  borderRadius: "50%",
                  backgroundColor: "#FFD700",
                  boxShadow: "0 0 10px #FFD700",
                }}
              />

              {/* Mosque Markers on Map */}
              {[
                { name: "Jamia Mosque", dist: "120m", top: "30%", left: "35%", active: true },
                { name: "Al-Rahman Mosque", dist: "340m", top: "25%", left: "72%", active: false },
                { name: "Bilal Mosque", dist: "480m", top: "72%", left: "68%", active: false },
              ].map((m, idx) => (
                <div
                  key={idx}
                  style={{
                    position: "absolute",
                    top: m.top,
                    left: m.left,
                    transform: "translate(-50%, -100%)",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                  }}
                >
                  <div
                    style={{
                      padding: "4px 8px",
                      borderRadius: 10,
                      backgroundColor: m.active ? "#C9A227" : "#17402A",
                      color: m.active ? "#0A1F13" : "#FAF6ED",
                      fontSize: 11,
                      fontWeight: 800,
                      whiteSpace: "nowrap",
                      boxShadow: "0 4px 10px rgba(0,0,0,0.5)",
                    }}
                  >
                    🕌 {m.name} ({m.dist})
                  </div>
                  <div style={{ fontSize: 20, marginTop: -4 }}>📍</div>
                </div>
              ))}
            </div>

            {/* Geofence Detection Radius Slider */}
            <div
              style={{
                padding: 14,
                borderRadius: 16,
                backgroundColor: "#133D26",
                border: "1px solid rgba(201, 162, 39, 0.3)",
                display: "flex",
                flexDirection: "column",
                gap: 8,
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: "#FAF6ED" }}>Detection Radius</span>
                <span style={{ fontSize: 13, fontWeight: 800, color: "#FFD700" }}>100 Meters</span>
              </div>
              <div
                style={{
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: "rgba(255,255,255,0.1)",
                  position: "relative",
                  overflow: "hidden",
                }}
              >
                <div style={{ width: "60%", height: "100%", backgroundColor: "#FFD700" }} />
              </div>
            </div>

            {/* Secure Import/Export Settings Card */}
            <div
              style={{
                padding: 14,
                borderRadius: 16,
                backgroundColor: "#17482D",
                border: "1px solid #C9A227",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ fontSize: 22 }}>🔒</span>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 800, color: "#FAF6ED" }}>Encrypted Backup</div>
                  <div style={{ fontSize: 11, color: "#8EAD98" }}>Import / Export Settings JSON</div>
                </div>
              </div>
              <div
                style={{
                  padding: "6px 12px",
                  borderRadius: 10,
                  backgroundColor: "#C9A227",
                  color: "#0A1F13",
                  fontSize: 12,
                  fontWeight: 800,
                }}
              >
                EXPORT
              </div>
            </div>
          </div>
        </PhoneMockup>
      </div>

      {/* Right Column: Text & Features */}
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
          Mosque Discovery & Security
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
          Mosque Management & <span style={{ color: "#FFD700" }}>Map Data</span>
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
          Detect nearby mosques automatically, customize geofence trigger radii, and securely back up your settings.
        </p>

        {/* Feature Cards */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {[
            { title: "🗺️ Overpass API & Offline Cache", desc: "Instantly detect surrounding mosques with fast local caching" },
            { title: "🎯 Tunable Detection Radius", desc: "Set geofence boundaries from 10m up to 500m per mosque" },
            { title: "🔐 AES-256 Encrypted Backup", desc: "Safely export and restore all app preferences & saved locations" },
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
    </AbsoluteFill>
  );
};
