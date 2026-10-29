import React from 'react';

interface CompassDialUIProps {
  heading: number;
  qiblaBearing: number;
  relativeAngle: number;
  isAligned: boolean;
}

export const CompassDialUI: React.FC<CompassDialUIProps> = ({
  heading,
  qiblaBearing,
  isAligned,
}) => {
  // Generate degree tick marks (every 10 deg major, 5 deg minor)
  const ticks = Array.from({ length: 72 }, (_, i) => {
    const deg = i * 5;
    const isMajor = deg % 30 === 0;
    const isMedium = deg % 10 === 0;
    const length = isMajor ? 14 : isMedium ? 10 : 6;
    const strokeWidth = isMajor ? 2.5 : 1.5;
    return { deg, length, strokeWidth, isMajor };
  });

  return (
    <div className="relative w-72 h-72 sm:w-80 sm:h-80 md:w-96 md:h-96 flex items-center justify-center select-none">
      {/* Outer ambient glow ring when aligned */}
      <div
        className={`absolute inset-0 rounded-full transition-all duration-500 pointer-events-none ${
          isAligned
            ? 'shadow-[0_0_50px_rgba(229,197,88,0.6)] border-2 border-[#E5C558]'
            : 'shadow-[0_0_25px_rgba(9,38,34,0.4)] border border-[#C5A044]/30'
        }`}
      />

      {/* SVG Compass Rose with smooth hardware-accelerated rotation */}
      <svg
        viewBox="0 0 400 400"
        className="w-full h-full transform-gpu transition-transform duration-75 ease-out"
        style={{
          transform: `rotate(${-heading}deg)`,
          willChange: 'transform',
        }}
      >
        <defs>
          {/* Deep Emerald Radial Gradient */}
          <radialGradient id="emeraldDialGrad" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#0B3B36" />
            <stop offset="70%" stopColor="#092622" />
            <stop offset="100%" stopColor="#051815" />
          </radialGradient>

          {/* Brushed Gold Gradient */}
          <linearGradient id="goldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#F3E5AB" />
            <stop offset="50%" stopColor="#E5C558" />
            <stop offset="100%" stopColor="#C5A044" />
          </linearGradient>

          <linearGradient id="goldSubtle" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#E5C558" stopOpacity="0.8" />
            <stop offset="100%" stopColor="#C5A044" stopOpacity="0.3" />
          </linearGradient>

          {/* Islamic Star Pattern Definition */}
          <pattern id="islamicStarLattice" width="40" height="40" patternUnits="userSpaceOnUse">
            <path
              d="M20 0 L25 15 L40 20 L25 25 L20 40 L15 25 L0 20 L15 15 Z"
              fill="none"
              stroke="#E5C558"
              strokeWidth="0.8"
              opacity="0.12"
            />
            <circle cx="20" cy="20" r="18" fill="none" stroke="#E5C558" strokeWidth="0.5" opacity="0.08" />
          </pattern>
        </defs>

        {/* Dial Background Base */}
        <circle cx="200" cy="200" r="192" fill="url(#emeraldDialGrad)" stroke="url(#goldGrad)" strokeWidth="4" />
        <circle cx="200" cy="200" r="188" fill="url(#islamicStarLattice)" />
        <circle cx="200" cy="200" r="172" fill="#07201D" stroke="#C5A044" strokeWidth="1" strokeDasharray="3 3" opacity="0.6" />

        {/* Islamic 8-Point Star Watermark */}
        <g transform="translate(200, 200)" opacity="0.15">
          <polygon
            points="0,-130 38,-38 130,0 38,38 0,130 -38,38 -130,0 -38,-38"
            fill="none"
            stroke="url(#goldGrad)"
            strokeWidth="1.5"
          />
          <polygon
            points="0,-130 38,-38 130,0 38,38 0,130 -38,38 -130,0 -38,-38"
            transform="rotate(45)"
            fill="none"
            stroke="url(#goldGrad)"
            strokeWidth="1.5"
          />
        </g>

        {/* Degree Ticks & Numbers */}
        <g transform="translate(200, 200)">
          {ticks.map(({ deg, length, strokeWidth, isMajor }) => {
            const angleRad = (deg * Math.PI) / 180;
            const x1 = Math.sin(angleRad) * 162;
            const y1 = -Math.cos(angleRad) * 162;
            const x2 = Math.sin(angleRad) * (162 - length);
            const y2 = -Math.cos(angleRad) * (162 - length);

            return (
              <g key={deg}>
                <line
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke="url(#goldGrad)"
                  strokeWidth={strokeWidth}
                  opacity={isMajor ? 1 : 0.6}
                />
                {isMajor && (
                  <text
                    x={Math.sin(angleRad) * 140}
                    y={-Math.cos(angleRad) * 140}
                    fill="#F8F9FA"
                    fontSize="12"
                    fontWeight="bold"
                    fontFamily="sans-serif"
                    textAnchor="middle"
                    dominantBaseline="central"
                    transform={`rotate(${deg}, ${Math.sin(angleRad) * 140}, ${-Math.cos(angleRad) * 140})`}
                  >
                    {deg === 0 ? '0' : deg === 90 ? '90' : deg === 180 ? '180' : deg === 270 ? '270' : deg}
                  </text>
                )}
              </g>
            );
          })}
        </g>

        {/* Cardinal Points Elaborate Badges (Counter-rotated by heading so they always stay upright) */}
        <g transform="translate(200, 200)" fontSize="16" fontWeight="900" fontFamily="serif" textAnchor="middle" dominantBaseline="central">
          {/* North */}
          <text x="0" y="-145" fill="url(#goldGrad)" transform={`rotate(${heading}, 0, -145)`}>N</text>
          {/* East */}
          <text x="145" y="0" fill="url(#goldGrad)" transform={`rotate(${heading}, 145, 0)`}>E</text>
          {/* South */}
          <text x="0" y="145" fill="url(#goldGrad)" transform={`rotate(${heading}, 0, 145)`}>S</text>
          {/* West */}
          <text x="-145" y="0" fill="url(#goldGrad)" transform={`rotate(${heading}, -145, 0)`}>W</text>
        </g>

        {/* Qibla Marker on the Rotating Dial (Points to Qibla relative to North) */}
        <g transform={`translate(200, 200) rotate(${qiblaBearing})`}>
          {/* Subtle dashed line from center to Qibla marker on dial */}
          <line x1="0" y1="0" x2="0" y2="-150" stroke="#E5C558" strokeWidth="2" strokeDasharray="6 4" opacity="0.8" />
          
          {/* Golden Qibla Pointer Icon on Dial */}
          <polygon points="0,-165 -7,-145 7,-145" fill="url(#goldGrad)" />
          <circle cx="0" cy="-155" r="5" fill="#092622" stroke="url(#goldGrad)" strokeWidth="2" />
        </g>

        {/* Center Ornamental Jewel */}
        <circle cx="200" cy="200" r="16" fill="#051815" stroke="url(#goldGrad)" strokeWidth="3" />
        <circle cx="200" cy="200" r="8" fill="url(#goldGrad)" />
      </svg>
    </div>
  );
};
