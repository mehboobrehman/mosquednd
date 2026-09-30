import React from "react";
import { AbsoluteFill, Sequence, useVideoConfig, interpolate } from "remotion";
import { Audio } from "@remotion/media";
import { staticFile } from "remotion";
import { Background } from "./components/Background";
import { Scene1Intro } from "./scenes/Scene1Intro";
import { Scene2AutoDND } from "./scenes/Scene2AutoDND";
import { Scene3PrayerTimes } from "./scenes/Scene3PrayerTimes";
import { Scene4QiblaCompass } from "./scenes/Scene4QiblaCompass";
import { Scene5MosqueMap } from "./scenes/Scene5MosqueMap";
import { Scene6Outro } from "./scenes/Scene6Outro";

export const MainComposition: React.FC = () => {
  const { durationInFrames, fps } = useVideoConfig();

  // Background audio volume with 1s fade-in and 2s fade-out
  const audioVolume = (f: number) =>
    interpolate(
      f,
      [0, fps, durationInFrames - 2 * fps, durationInFrames],
      [0, 0.25, 0.25, 0],
      { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
    );

  return (
    <AbsoluteFill style={{ backgroundColor: "#0A1F13", color: "#FAF6ED", fontFamily: "sans-serif" }}>
      {/* Background Music Bed */}
      <Audio src={staticFile("music/bg.wav")} loop volume={audioVolume} />

      {/* Global Animated Background */}
      <Background />

      {/* Scene 1: Hook / Intro (0s - 6s, 180 frames) */}
      <Sequence from={0} durationInFrames={180}>
        <Scene1Intro />
      </Sequence>

      {/* Scene 2: Auto Mosque DND (6s - 12.5s, 195 frames) */}
      <Sequence from={180} durationInFrames={195}>
        <Scene2AutoDND />
      </Sequence>

      {/* Scene 3: Prayer Times & Adhan (12.5s - 19s, 195 frames) */}
      <Sequence from={375} durationInFrames={195}>
        <Scene3PrayerTimes />
      </Sequence>

      {/* Scene 4: 3D Qibla Compass (19s - 25.5s, 195 frames) */}
      <Sequence from={570} durationInFrames={195}>
        <Scene4QiblaCompass />
      </Sequence>

      {/* Scene 5: Mosque Map & Data (25.5s - 32s, 195 frames) */}
      <Sequence from={765} durationInFrames={195}>
        <Scene5MosqueMap />
      </Sequence>

      {/* Scene 6: Outro / CTA (32s - 38s, 180 frames) */}
      <Sequence from={960} durationInFrames={180}>
        <Scene6Outro />
      </Sequence>
    </AbsoluteFill>
  );
};
