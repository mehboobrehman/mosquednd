import React from "react";

interface PhoneMockupProps {
  children: React.ReactNode;
  title?: string;
  glowColor?: string;
  width?: number;
  height?: number;
}

export const PhoneMockup: React.FC<PhoneMockupProps> = ({
  children,
  title = "MosqueDND",
  glowColor = "rgba(201, 162, 39, 0.4)",
  width = 440,
  height = 840,
}) => {
  return (
    <div
      style={{
        width,
        height,
        borderRadius: 44,
        padding: 14,
        background: "linear-gradient(145deg, #2D3A31, #0F1A13)",
        boxShadow: `0 25px 60px -10px rgba(0,0,0,0.8), 0 0 40px ${glowColor}`,
        border: "3px solid #C9A227",
        position: "relative",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {/* Speaker / Camera Notch */}
      <div
        style={{
          position: "absolute",
          top: 14,
          left: "50%",
          transform: "translateX(-50%)",
          width: 120,
          height: 24,
          backgroundColor: "#0A140E",
          borderBottomLeftRadius: 16,
          borderBottomRightRadius: 16,
          zIndex: 100,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: 10,
        }}
      >
        <div style={{ width: 40, height: 4, backgroundColor: "#1F2F24", borderRadius: 2 }} />
        <div style={{ width: 8, height: 8, backgroundColor: "#15241B", borderRadius: "50%" }} />
      </div>

      {/* Screen Container */}
      <div
        style={{
          flex: 1,
          borderRadius: 32,
          backgroundColor: "#0C2316",
          overflow: "hidden",
          display: "flex",
          flexDirection: "column",
          position: "relative",
          border: "1px solid rgba(201, 162, 39, 0.2)",
        }}
      >
        {/* Status Bar */}
        <div
          style={{
            height: 38,
            backgroundColor: "rgba(10, 31, 19, 0.95)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            paddingLeft: 24,
            paddingRight: 24,
            paddingTop: 8,
            color: "#FAF6ED",
            fontSize: 13,
            fontWeight: 600,
            letterSpacing: 0.5,
            zIndex: 10,
            borderBottom: "1px solid rgba(201, 162, 39, 0.15)",
          }}
        >
          <span>04:30 PM</span>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span>📶</span>
            <span>🔋 98%</span>
          </div>
        </div>

        {/* App Top Bar */}
        <div
          style={{
            height: 50,
            backgroundColor: "#133522",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            paddingLeft: 20,
            paddingRight: 20,
            borderBottom: "1px solid #C9A227",
            zIndex: 10,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: 6,
                backgroundColor: "#C9A227",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: "bold",
                color: "#0A1F13",
                fontSize: 14,
              }}
            >
              🕌
            </div>
            <span
              style={{
                color: "#FAF6ED",
                fontSize: 17,
                fontWeight: 700,
                letterSpacing: 0.5,
              }}
            >
              {title}
            </span>
          </div>
          <div
            style={{
              padding: "4px 10px",
              borderRadius: 12,
              backgroundColor: "rgba(201, 162, 39, 0.2)",
              border: "1px solid #C9A227",
              color: "#FFD700",
              fontSize: 11,
              fontWeight: 600,
            }}
          >
            PRO
          </div>
        </div>

        {/* Screen Content */}
        <div
          style={{
            flex: 1,
            position: "relative",
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
            backgroundColor: "#0F2B1B",
          }}
        >
          {children}
        </div>
      </div>
    </div>
  );
};
