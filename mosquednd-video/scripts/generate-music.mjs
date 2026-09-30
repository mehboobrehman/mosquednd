import { mkdirSync, writeFileSync } from "node:fs";

const SR = 44100;

const MINOR = [0, 3, 7, 10];   // minor 7
const MAJOR = [0, 4, 7, 11];   // major 7
const MINTRI = [0, 3, 7];
const MAJTRI = [0, 4, 7];

// Islamic / Ambient / Cinematic theme: calm, elegant, gold-emerald feel (76 BPM)
const cfg = {
  bpm: 76,
  root: 45, // A2
  bars: 20,
  padWave: "sine",
  arpWave: "triangle",
  cutoff: 2000,
  delayWet: 0.25,
  swing: 0,
  kick: false,
  hats: false,
  arpSteps: 4,
  padGain: 0.12,
  arpGain: 0.08,
  bassGain: 0.22,
  prog: [
    [0, MINOR],
    [3, MAJTRI],
    [-2, MAJTRI],
    [5, MINOR]
  ]
};

const spb = 60 / cfg.bpm;
const barSec = spb * 4;
const totalSec = cfg.bars * barSec;
const N = Math.floor(totalSec * SR);

const tone = new Float32Array(N);
const perc = new Float32Array(N);

const midiToFreq = (m) => 440 * 2 ** ((m - 69) / 12);
const osc = (type, ph) => {
  switch (type) {
    case "sine": return Math.sin(2 * Math.PI * ph);
    case "square": return ph < 0.5 ? 1 : -1;
    case "sawtooth": return 2 * ph - 1;
    default: { const s = 2 * ph - 1; return 2 * Math.abs(s) - 1; } // triangle
  }
};

function addNote(target, startSec, durSec, freq, gain, type, attack, release) {
  const s0 = Math.floor(startSec * SR);
  const s1 = Math.min(N, Math.floor((startSec + durSec) * SR));
  const aS = Math.max(1, attack * SR), rS = Math.max(1, release * SR);
  for (let i = s0; i < s1; i++) {
    const t = i - s0;
    let e = t < aS ? t / aS : 1;
    const tr = s1 - i;
    if (tr < rS) e = Math.min(e, tr / rS);
    target[i] += gain * e * osc(type, (freq * (i / SR)) % 1);
  }
}

for (let bar = 0; bar < cfg.bars; bar++) {
  const [deg, quality] = cfg.prog[bar % cfg.prog.length];
  const chordRoot = cfg.root + deg;
  const t0 = bar * barSec;
  // Bass drone
  addNote(tone, t0, barSec, midiToFreq(chordRoot - 12), cfg.bassGain, "sine", 0.05, 0.2);
  // Pad chord
  for (const iv of quality) {
    addNote(tone, t0, barSec, midiToFreq(chordRoot + 12 + iv), cfg.padGain, cfg.padWave, barSec * 0.3, barSec * 0.4);
  }
  // Arpeggio
  const arp = quality.map((iv) => chordRoot + 24 + iv);
  const steps = cfg.arpSteps, stepLen = barSec / steps;
  for (let s = 0; s < steps; s++) {
    addNote(tone, t0 + s * stepLen, stepLen * 0.9, midiToFreq(arp[s % arp.length]), cfg.arpGain, cfg.arpWave, 0.01, 0.15);
  }
}

// Lowpass filter
{
  const dt = 1 / SR, rc = 1 / (2 * Math.PI * cfg.cutoff), a = dt / (rc + dt);
  let y = 0;
  for (let i = 0; i < N; i++) { y += a * (tone[i] - y); tone[i] = y; }
}

// Delay
const mix = new Float32Array(N);
for (let i = 0; i < N; i++) mix[i] = tone[i] + perc[i];
{
  const d = Math.floor(spb * 0.75 * SR), wet = cfg.delayWet;
  for (let i = d; i < N; i++) mix[i] += wet * mix[i - d];
}

// Normalize + render WAV
let peak = 0;
for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(mix[i]));
const g = peak > 0 ? 0.9 / peak : 1;
const fadeIn = Math.floor(0.8 * SR), fadeOut = Math.floor(2.0 * SR);

const out = Buffer.alloc(44 + N * 2);
out.write("RIFF", 0); out.writeUInt32LE(36 + N * 2, 4); out.write("WAVE", 8);
out.write("fmt ", 12); out.writeUInt32LE(16, 16); out.writeUInt16LE(1, 20); out.writeUInt16LE(1, 22);
out.writeUInt32LE(SR, 24); out.writeUInt32LE(SR * 2, 28); out.writeUInt16LE(2, 32); out.writeUInt16LE(16, 34);
out.write("data", 36); out.writeUInt32LE(N * 2, 40);

for (let i = 0; i < N; i++) {
  let v = mix[i] * g;
  if (i < fadeIn) v *= i / fadeIn;
  if (i > N - fadeOut) v *= (N - i) / fadeOut;
  v = Math.max(-1, Math.min(1, v));
  out.writeInt16LE((v * 32767) | 0, 44 + i * 2);
}

mkdirSync("public/music", { recursive: true });
writeFileSync("public/music/bg.wav", out);
console.log(`Generated background music: public/music/bg.wav (${totalSec.toFixed(1)}s)`);
