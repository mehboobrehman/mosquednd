import React from "react";

interface IslamicStarProps {
  size?: number;
  glow?: boolean;
  color?: string;
}

export const IslamicStar: React.FC<IslamicStarProps> = ({
  size = 120,
  glow = true,
  color = "#C9A227",
}) => {
  return (
    <div
      style={{
        width: size,
        height: size,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        filter: glow ? "drop-shadow(0 0 16px rgba(201, 162, 39, 0.6))" : "none",
      }}
    >
      <svg
        width={size}
        height={size}
        viewBox="0 0 100 100"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <linearGradient id="gold-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#FFE885" />
            <stop offset="50%" stopColor="#C9A227" />
            <stop offset="100%" stopColor="#8F6E10" />
          </linearGradient>
        </defs>

        {/* Outer 8-Pointed Star (Rub' el Hizb) */}
        <rect
          x="15"
          y="15"
          width="70"
          height="70"
          rx="6"
          fill="url(#gold-grad)"
          opacity="0.9"
        />
        <rect
          x="15"
          y="15"
          width="70"
          height="70"
          rx="6"
          fill="url(#gold-grad)"
          opacity="0.9"
          transform="rotate(45 50 50)"
        />

        {/* Inner Emerald Octagon */}
        <circle cx="50" cy="50" r="28" fill="#0A1F13" stroke="#FFE885" strokeWidth="2" />

        {/* Mosque Dome and Minaret Silhouette */}
        <path
          d="M50 28 C43 36, 40 43, 40 54 L60 54 C60 43, 57 36, 50 28 Z"
          fill="url(#gold-grad)"
        />
        <rect x="42" y="54" width="16" height="14" fill="url(#gold-grad)" />
        <path d="M47 68 H53 V58 H47 V68 Z" fill="#0A1F13" />

        {/* Crescent Star on Top */}
        <path
          d="M50 23 A2.5 2.5 0 1 0 51.5 26 A2 2 0 1 1 50 23 Z"
          fill="#FFE885"
        />
      </svg>
    </div>
  );
};
