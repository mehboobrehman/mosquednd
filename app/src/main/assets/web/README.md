# MosqueDND Qibla Direction Compass Dial

A breathtaking, fully functional Qibla Direction Compass Dial built as a modular set of components and helper files for the MosqueDND project.

## Modular Architecture (`app/src/main/assets/web/`)

1. **`QiblaMath.ts`**:
   - Implements precise Great Circle Bearing calculations relative to the Holy Kaaba (`21.4225° N, 39.8262° E`).
   - Haversine distance calculations.
   - Preset fallback cities (`London`, `Karachi`, `New York`, `Istanbul`, `Dubai`, `Makkah`).

2. **`CompassSensor.ts`**:
   - Geolocation hook for automatic user coordinate acquisition.
   - DeviceOrientation API handler supporting iOS 13+ permission prompts (`DeviceOrientationEvent.requestPermission()`) and Android absolute heading normalization.
   - Manual simulation heading slider/drag for desktop testing.

3. **`CompassDialUI.tsx`**:
   - SVG compass rose with degree tick marks (major every 30°, minor every 5°), cardinal points (`N`, `E`, `S`, `W`), and modern Islamic geometric star lattice watermark.
   - 60fps hardware-accelerated smooth rotation (`will-change: transform`).

4. **`KaabaIndicator.tsx`**:
   - Fixed top anchor (12 o'clock position) featuring a golden Kaaba icon, directional apex, and glowing pulse halo when aligned within ±2° of the Qibla bearing.

5. **`QiblaCompassApp.tsx`**:
   - Main container orchestrating loading states, error/fallback banners, city selector modal, sensor activation button, and telemetry info cards.

6. **`index.html`**:
   - Standalone web container embedding Tailwind CSS and React UMD for effortless WebView or browser embedding.

## Color Palette (Modern Islamic Luxury)
- **Deep Rich Emerald**: `#092622` / `#0B3B36` / `#051815`
- **Luminous Brushed Gold**: `#E5C558` / `#C5A044` / `#F3E5AB`
- **Soft Pearl White**: `#F8F9FA`
- **Charcoal Contrast**: `#111111`
