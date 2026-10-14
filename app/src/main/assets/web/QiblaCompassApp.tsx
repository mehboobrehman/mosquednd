import React, { useState } from 'react';
import { useCompassSensor } from './CompassSensor';
import { CompassDialUI } from './CompassDialUI';
import { KaabaIndicator } from './KaabaIndicator';
import { FALLBACK_CITIES, calculateDistanceKm, KAABA_COORDS } from './QiblaMath';

export const QiblaCompassApp: React.FC = () => {
  const {
    state,
    requestCompassPermission,
    setSimulatedHeading,
    selectFallbackCity,
  } = useCompassSensor('karachi');

  const [showCityPicker, setShowCityPicker] = useState(false);
  const [showInfoModal, setShowInfoModal] = useState(false);

  // Check if aligned within ±2° of Qibla
  const normalizedRelativeAngle = (state.relativeQiblaAngle + 360) % 360;
  const isAligned =
    normalizedRelativeAngle <= 2 || normalizedRelativeAngle >= 358;

  const distanceToKaaba = calculateDistanceKm(
    state.location.lat,
    state.location.lng,
    KAABA_COORDS.lat,
    KAABA_COORDS.lng
  );

  return (
    <div className="min-h-screen bg-gradient-to-b from-[#051815] via-[#092622] to-[#0B3B36] text-[#F8F9FA] flex flex-col items-center justify-between p-4 font-sans select-none overflow-x-hidden">
      {/* Top Header */}
      <header className="w-full max-w-md flex items-center justify-between py-3 px-4 bg-[#0B3B36]/80 backdrop-blur-md rounded-2xl border border-[#E5C558]/30 shadow-lg mt-2">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-[#E5C558] to-[#C5A044] flex items-center justify-center shadow-md">
            <span className="text-[#092622] font-black text-base">ق</span>
          </div>
          <div>
            <h1 className="text-sm font-bold tracking-wide text-[#F3E5AB]">MosqueDND Qibla</h1>
            <p className="text-[11px] text-[#F8F9FA]/70 font-mono">
              {state.locationName}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowCityPicker(!showCityPicker)}
            className="px-2.5 py-1 text-xs bg-[#092622] hover:bg-[#07201D] text-[#E5C558] border border-[#E5C558]/40 rounded-lg transition-all shadow-sm"
            title="Select Location / City"
          >
            📍 City
          </button>
          <button
            onClick={() => setShowInfoModal(true)}
            className="w-7 h-7 rounded-full bg-[#092622] text-[#E5C558] border border-[#E5C558]/40 flex items-center justify-center text-xs font-bold hover:bg-[#07201D]"
            title="Info & Help"
          >
            i
          </button>
        </div>
      </header>

      {/* City Picker Modal / Dropdown */}
      {showCityPicker && (
        <div className="absolute top-20 z-50 w-80 bg-[#092622] border-2 border-[#E5C558] rounded-2xl p-4 shadow-2xl backdrop-blur-xl animate-fade-in">
          <div className="flex justify-between items-center mb-3">
            <h3 className="text-sm font-bold text-[#F3E5AB]">Select Location Preset</h3>
            <button
              onClick={() => setShowCityPicker(false)}
              className="text-[#F8F9FA]/60 hover:text-[#F8F9FA] text-xs font-bold px-2 py-0.5"
            >
              ✕ Close
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2 max-h-60 overflow-y-auto pr-1">
            {Object.entries(FALLBACK_CITIES).map(([key, city]) => (
              <button
                key={key}
                onClick={() => {
                  selectFallbackCity(key);
                  setShowCityPicker(false);
                }}
                className="flex flex-col items-start p-2.5 rounded-xl bg-[#0B3B36] hover:bg-[#114943] border border-[#E5C558]/30 transition-all text-left"
              >
                <span className="text-xs font-bold text-[#F3E5AB]">{city.name}</span>
                <span className="text-[10px] text-[#F8F9FA]/70">{city.country}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Main Compass Viewport Container */}
      <main className="relative flex-1 flex flex-col items-center justify-center my-6">
        {state.isLoading ? (
          /* Loading State: Shimmering Gold Geometric Skeleton */
          <div className="w-80 h-80 rounded-full border-4 border-[#E5C558]/30 flex items-center justify-center animate-pulse bg-[#092622]/50 shadow-2xl">
            <div className="flex flex-col items-center gap-2">
              <div className="w-12 h-12 rounded-full border-2 border-t-[#E5C558] border-transparent animate-spin" />
              <span className="text-xs text-[#F3E5AB] font-mono tracking-wider">
                Acquiring GPS & Qibla...
              </span>
            </div>
          </div>
        ) : (
          <div className="relative flex flex-col items-center">
            {/* Fixed Kaaba Indicator at Top Center */}
            <KaabaIndicator
              relativeAngle={state.relativeQiblaAngle}
              qiblaBearing={state.qiblaBearing}
              isAligned={isAligned}
            />

            {/* Rotating Compass Dial */}
            <div className="relative pt-6">
              <CompassDialUI
                heading={state.heading}
                qiblaBearing={state.qiblaBearing}
                relativeAngle={state.relativeQiblaAngle}
                isAligned={isAligned}
              />
            </div>

            {/* Alignment Success Toast */}
            {isAligned && (
              <div className="absolute -bottom-8 bg-[#E5C558] text-[#092622] px-4 py-1 rounded-full text-xs font-bold tracking-wide shadow-[0_0_20px_rgba(229,197,88,0.8)] animate-bounce">
                ✨ Perfectly Aligned with Qibla! ✨
              </div>
            )}
          </div>
        )}
      </main>

      {/* Bottom Control Deck & Telemetry */}
      <footer className="w-full max-w-md flex flex-col gap-3">
        {/* Sensor Permission / Activation Button */}
        {state.permissionStatus !== 'granted' && !state.isSimulated && (
          <button
            onClick={requestCompassPermission}
            className="w-full py-3 px-4 bg-gradient-to-r from-[#E5C558] to-[#C5A044] text-[#092622] font-extrabold text-sm rounded-xl shadow-lg hover:opacity-95 transition-all transform active:scale-95 flex items-center justify-center gap-2"
          >
            <span>🧭 Enable Device Compass</span>
          </button>
        )}

        {/* Manual Simulation Slider (Desktop / Testing fallback) */}
        <div className="bg-[#0B3B36]/90 backdrop-blur-md p-3.5 rounded-2xl border border-[#E5C558]/30 shadow-xl flex flex-col gap-2">
          <div className="flex justify-between items-center text-xs">
            <span className="text-[#F3E5AB] font-semibold flex items-center gap-1.5">
              <span>⚙️ Simulation / Test Heading</span>
              {state.isSimulated && (
                <span className="text-[10px] bg-[#E5C558]/20 text-[#E5C558] px-1.5 py-0.5 rounded">
                  Manual Mode
                </span>
              )}
            </span>
            <span className="font-mono font-bold text-[#F8F9FA]">
              {Math.round(state.heading)}°
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="360"
            step="1"
            value={state.heading}
            onChange={(e) => setSimulatedHeading(parseFloat(e.target.value))}
            className="w-full accent-[#E5C558] cursor-pointer h-1.5 bg-[#092622] rounded-lg"
          />
          <div className="flex justify-between text-[10px] text-[#F8F9FA]/60 font-mono">
            <span>N (0°)</span>
            <span>E (90°)</span>
            <span>S (180°)</span>
            <span>W (270°)</span>
          </div>
        </div>

        {/* Telemetry Summary Cards */}
        <div className="grid grid-cols-3 gap-2 text-center">
          <div className="bg-[#0B3B36]/70 p-2.5 rounded-xl border border-[#C5A044]/30">
            <span className="text-[10px] text-[#F8F9FA]/60 uppercase tracking-wider block">
              Heading
            </span>
            <span className="text-sm font-bold text-[#F3E5AB] font-mono">
              {Math.round(state.heading)}°
            </span>
          </div>
          <div className="bg-[#0B3B36]/70 p-2.5 rounded-xl border border-[#C5A044]/30">
            <span className="text-[10px] text-[#F8F9FA]/60 uppercase tracking-wider block">
              Qibla Bearing
            </span>
            <span className="text-sm font-bold text-[#F3E5AB] font-mono">
              {state.qiblaBearing.toFixed(1)}°
            </span>
          </div>
          <div className="bg-[#0B3B36]/70 p-2.5 rounded-xl border border-[#C5A044]/30">
            <span className="text-[10px] text-[#F8F9FA]/60 uppercase tracking-wider block">
              Distance
            </span>
            <span className="text-sm font-bold text-[#F3E5AB] font-mono">
              {Math.round(distanceToKaaba).toLocaleString()} km
            </span>
          </div>
        </div>
      </footer>

      {/* Info & Usage Modal */}
      {showInfoModal && (
        <div className="absolute inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#092622] border-2 border-[#E5C558] rounded-3xl p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex justify-between items-center">
              <h2 className="text-base font-bold text-[#F3E5AB] flex items-center gap-2">
                <span>🕌</span> About MosqueDND Qibla
              </h2>
              <button
                onClick={() => setShowInfoModal(false)}
                className="text-[#F8F9FA]/60 hover:text-[#F8F9FA] text-sm font-bold"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-[#F8F9FA]/80 leading-relaxed">
              This breathtaking compass computes precise Great Circle bearings to the Holy Kaaba (21.4225° N, 39.8262° E) using WGS84 geospatial formulas.
            </p>
            <div className="flex flex-col gap-1.5 text-xs text-[#F8F9FA]/70 bg-[#0B3B36] p-3 rounded-xl border border-[#C5A044]/30">
              <span className="font-bold text-[#F3E5AB]">How to use:</span>
              <span>1. Enable device compass when prompted on mobile.</span>
              <span>2. Align your phone so the golden Kaaba arrow points straight ahead.</span>
              <span>3. Use the slider on desktop for manual simulation.</span>
            </div>
            <button
              onClick={() => setShowInfoModal(false)}
              className="w-full py-2.5 bg-[#E5C558] text-[#092622] font-bold text-xs rounded-xl shadow-md hover:opacity-95"
            >
              Got It
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
