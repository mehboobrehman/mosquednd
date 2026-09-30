import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, interpolate, Easing } from "remotion";
import { PhoneMockup } from "../components/PhoneMockup";

export const Scene3PrayerTimes: React.FC = () => {
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

  // Animated Audio Waveform for Adhan player
  const waveHeight = (i: number) => {
    return 10 + Math.abs(Math.sin(frame * 0.2 + i)) * 26;
  };

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
      {/* Left Column: Phone Mockup with Prayer Schedule UI */}
      <div
        style={{
          opacity: phoneOpacity,
          translate: `${phoneTranslateX}px 0px`,
        }}
      >
        <PhoneMockup title="Prayer Schedule" width={420} height={800}>
          <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 10 }}>
            {/* Header Hijri Date Banner */}
            <div
              style={{
                padding: "10px 16px",
                borderRadius: 12,
                backgroundColor: "rgba(201, 162, 39, 0.15)",
                border: "1px solid #C9A227",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
              }}
            >
              <span style={{ fontSize: 13, fontWeight: 700, color: "#FFD700" }}>
                14 Safar 1448 AH
              </span>
              <span style={{ fontSize: 12, color: "#FAF6ED" }}>Karachi, PK</span>
            </div>

            {/* Prayer Times List */}
            {[
              { name: "Fajr", time: "05:12 AM", active: false },
              { name: "Sunrise", time: "06:30 AM", active: false, secondary: true },
              { name: "Dhuhr", time: "12:30 PM", active: false },
              { name: "Asr", time: "03:45 PM", active: true, countdown: "In 12 min" },
              { name: "Maghrib", time: "06:48 PM", active: false },
              { name: "Isha", time: "08:15 PM", active: false },
            ].map((p, idx) => (
              <div
                key={idx}
                style={{
                  padding: "10px 16px",
                  borderRadius: 14,
                  backgroundColor: p.active ? "#195433" : "rgba(255,255,255,0.04)",
                  border: p.active ? "2px solid #FFD700" : "1px solid rgba(255,255,255,0.08)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  boxShadow: p.active ? "0 0 15px rgba(201, 162, 39, 0.3)" : "none",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <div
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: "50%",
                      backgroundColor: p.active ? "#FFD700" : p.secondary ? "#668070" : "#2E6645",
                    }}
                  />
                  <span
                    style={{
                      fontSize: 16,
                      fontWeight: p.active ? 800 : 600,
                      color: p.active ? "#FFD700" : "#FAF6ED",
                    }}
                  >
                    {p.name}
                  </span>
                  {p.active && (
                    <span
                      style={{
                        padding: "2px 8px",
                        borderRadius: 10,
                        backgroundColor: "#FFD700",
                        color: "#0A1F13",
                        fontSize: 10,
                        fontWeight: 900,
                      }}
                    >
                      NEXT
                    </span>
                  )}
                </div>

                <div style={{ textAlign: "right" }}>
                  <div
                    style={{
                      fontSize: 16,
                      fontWeight: 700,
                      color: p.active ? "#FAF6ED" : "#C0D4C7",
                    }}
                  >
                    {p.time}
                  </div>
                  {p.countdown && (
                    <div style={{ fontSize: 11, color: "#FFD700", fontWeight: 700 }}>
                      {p.countdown}
                    </div>
                  )}
                </div>
              </div>
            ))}

            {/* Adhan Audio Player Card */}
            <div
              style={{
                marginTop: 6,
                padding: 14,
                borderRadius: 16,
                backgroundColor: "#113A24",
                border: "1px solid #C9A227",
                display: "flex",
                flexDirection: "column",
                gap: 8,
              }}
            >
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 18 }}>🔊</span>
                  <span style={{ fontSize: 13, fontWeight: 700, color: "#FFD700" }}>
                    Adhan Recitation Alert
                  </span>
                </div>
                <span style={{ fontSize: 11, color: "#8EAD98" }}>Mishary Alafasy</span>
              </div>

              {/* Audio Waveform */}
              <div
                style={{
                  height: 36,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: 4,
                  padding: "0 10px",
                }}
              >
                {[...Array(24)].map((_, i) => (
                  <div
                    key={i}
                    style={{
                      width: 4,
                      height: waveHeight(i),
                      backgroundColor: i % 2 === 0 ? "#FFD700" : "#C9A227",
                      borderRadius: 2,
                    }}
                  />
                ))}
              </div>
            </div>
          </div>
        </PhoneMockup>
      </div>

      {/* Right Column: Text & Feature Specs */}
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
          Astronomical Precision
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
          Precise <span style={{ color: "#FFD700" }}>Prayer Times</span> & Adhan
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
          High-priority exact alarms, customizable time offsets, per-prayer DND durations, and custom Adhan audio recitations.
        </p>

        {/* Feature List Cards */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {[
            { icon: "⏰", title: "Exact Alarm Scheduling", desc: "Android AlarmManager exact alarms bypass battery saver" },
            { icon: "🎛️", title: "Custom Prayer Offsets", desc: "Adjust +/- minutes per prayer to match local mosque timings" },
            { icon: "🕌", title: "Per-Prayer DND Durations", desc: "Set custom silence durations (15m, 30m, 45m) per Salah" },
          ].map((item, idx) => (
            <div
              key={idx}
              style={{
                padding: "16px 20px",
                borderRadius: 16,
                backgroundColor: "rgba(15, 58, 34, 0.8)",
                border: "1px solid rgba(201, 162, 39, 0.3)",
                display: "flex",
                alignItems: "center",
                gap: 16,
              }}
            >
              <div style={{ fontSize: 32 }}>{item.icon}</div>
              <div>
                <div style={{ fontSize: 20, fontWeight: 700, color: "#FAF6ED" }}>{item.title}</div>
                <div style={{ fontSize: 16, color: "#9EBEA8" }}>{item.desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </AbsoluteFill>
  );
};
