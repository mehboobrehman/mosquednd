import React from 'react';

interface KaabaIndicatorProps {
  relativeAngle: number; // Offset angle between current heading and Qibla bearing
  qiblaBearing: number;
  isAligned: boolean;
}

export const KaabaIndicator: React.FC<KaabaIndicatorProps> = ({
  relativeAngle,
  qiblaBearing,
  isAligned,
}) => {
  return (
    <div className="absolute top-3 left-1/2 transform -translate-x-1/2 flex flex-col items-center z-20 pointer-events-none">
      {/* Outer Glow Halo when aligned within ±2° */}
      <div
        className={`absolute -inset-2 rounded-full transition-all duration-300 ${
          isAligned
            ? 'bg-[#E5C558]/30 blur-md animate-pulse scale-110'
            : 'bg-transparent'
        }`}
      />

      {/* Kaaba Apex Indicator & Golden Icon */}
      <div className="relative flex flex-col items-center">
        {/* Golden Arrow pointing down into the dial */}
        <div
          className={`w-0 h-0 border-x-8 border-x-transparent border-t-[14px] transition-colors duration-300 ${
            isAligned ? 'border-t-[#F3E5AB] filter drop-shadow-[0_0_8px_#E5C558]' : 'border-t-[#E5C558]'
          }`}
        />

        {/* Refined Kaaba Icon Card */}
        <div
          className={`flex items-center gap-1.5 px-3 py-1 rounded-full border shadow-lg backdrop-blur-md transition-all duration-300 ${
            isAligned
              ? 'bg-[#0B3B36] border-[#E5C558] shadow-[0_0_15px_rgba(229,197,88,0.5)]'
              : 'bg-[#092622]/90 border-[#C5A044]/60 shadow-md'
          }`}
        >
          {/* Miniature Kaaba Icon representation (Black cube with golden band) */}
          <div className="relative w-4 h-4 bg-[#111111] border border-[#E5C558] rounded-xs flex items-center justify-center shadow-inner overflow-hidden">
            <div className="absolute top-1 w-full h-[2px] bg-[#E5C558]" />
          </div>

          <div className="flex flex-col items-start leading-tight">
            <span className="text-[10px] font-bold text-[#F3E5AB] tracking-widest uppercase">
              القبلة Qibla
            </span>
            <span className="text-[9px] text-[#F8F9FA]/80 font-mono">
              {qiblaBearing.toFixed(1)}°
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
