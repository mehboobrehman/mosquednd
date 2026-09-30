# MosqueDND Wi-Fi Log Telemetry & Zero-ADB Local Testing Guide

This documentation outlines the complete, production-ready Wi-Fi log telemetry pipeline and local APK testing setup for **MosqueDND**. This system enables physical consumer devices—with Developer Options and USB debugging permanently disabled—to stream runtime logs and crash reports over local Wi-Fi to a Node.js log collector server.

---

## Architecture & Components

### 1. Log Collector Server (`scripts/log-server.js`)
- **Self-Healing Port Management**: 
  - Attempts to bind to port `5000` on `0.0.0.0`.
  - Automatically identifies and terminates any blocking process (cross-platform `taskkill` on Windows / `kill` on Unix).
  - Smoothly falls back to port `5001` if port `5000` remains unavailable.
- **Dual Configuration & Distribution Channels**:
  - Automatically discovers the primary local IPv4 address.
  - Displays two distinct terminal QR codes / links:
    1. **Telemetry Config**: `http://<IP>:<PORT>/api/config` — Links mobile devices to this telemetry instance.
    2. **APK Distribution**: `http://<IP>:8080` — Portal for downloading built APKs.
- **Log Stream Management**:
  - Persists logs synchronously to `./logs/device-stream.log`.
  - Streams real-time colorized logs to stdout with device IDs and timestamps.
  - Permissive CORS enables zero-config connection from any device on the subnet.

### 2. TypeScript Dev Harness (`dev-harness/`)
A decoupled, non-blocking development module for React/React Native environments:
- **Autonomous Discovery (`logger.ts`)**: Probes local IP ranges to automatically find the active log server. Caches the identified IP using `AsyncStorage`.
- **Global Error Interceptor (`errorInterceptor.ts`)**: Automatically hooks into `ErrorUtils.setGlobalHandler`, `window.onerror`, and unhandled promise rejections to report fatal crashes in real-time.
- **Interactive Dev Overlay (`DevMenuOverlay.tsx`)**: A floating UI component providing live log inspection, connection status, and manual configuration controls.

### 3. Android Telemetry Client (`AgentLogger.kt`)
- Mirror of the TS logger for native Android layers.
- Supports 2-second non-blocking network timeouts and global uncaught exception handling.

---

## End-to-End Workflow & Instructions

### Step 1: Install Dependencies
Ensure Node.js is installed. Install `qrcode-terminal` for terminal QR code rendering:
```bash
npm install qrcode-terminal
```

### Step 2: Start the Log Collector Server
Run the log server in your terminal:
```bash
node scripts/log-server.js
```
The server will bind to the best available port (5000/5001), display its local network URL, and render terminal QR codes for easy mobile linking.

### Step 3: Integrate the Dev Harness (TS/JS)
In your main application entry point (e.g., `index.js` or `App.tsx`):
```typescript
import { logger } from './dev-harness/logger';
import { initErrorInterceptor } from './dev-harness/errorInterceptor';
import { DevMenuOverlay } from './dev-harness/DevMenuOverlay';

// Initialize global crash interceptor in dev
initErrorInterceptor();

// Add the overlay to your root view
const App = () => (
  <View style={{ flex: 1 }}>
    <MainNavigator />
    <DevMenuOverlay />
  </View>
);
```

### Step 4: Zero-Touch Discovery & Distribution
1. **Connect**: Ensure your mobile device and workstation are on the same Wi-Fi network.
2. **Download**: Scan the **APK Distribution** QR code from the terminal to download and install the latest build.
3. **Link**: Open the app. The `dev-harness` will automatically probe the subnet for the log server. Alternatively, scan the **Telemetry Config** QR code or enter the IP manually in the `DevMenuOverlay`.
4. **Debug**: Runtime logs and crashes will now stream directly to your workstation console and `./logs/device-stream.log`.

---

## Troubleshooting

- **Server Not Found**: Ensure both device and workstation are on the same subnet (e.g., both on `192.168.1.x`).
- **Port Conflict**: The server attempts to kill blocking processes on port 5000. If this fails, it uses 5001. Check the terminal output for the active port.
- **Firewall**: Ensure your workstation firewall allows inbound traffic on ports `5000`, `5001`, and `8080`.
- **QR Code Visibility**: Use a terminal with a dark background for the best QR code contrast. Use `CMD` or `PowerShell` on Windows, or `iTerm2`/`Terminal` on macOS.

---

## Manual Log Server Override (different subnets)

Auto-discovery only scans the **phone's own subnet** (e.g. `10.12.93.1-254`). If the PC running
`scripts/log-server.js` is on a **different subnet** (e.g. `192.168.105.79`), discovery can never
find it — the toast will say `No server found on <phone-subnet>*.`.

Two requirements to fix this:

### 1. Network-level (mandatory)
The phone and the PC must be able to **route to each other** — connect the phone to the **same
Wi-Fi/LAN** as the PC, or bridge the two subnets. The manual override only helps when the two
devices can actually reach each other.

### 2. Pin the server IP manually (app-level)

**Via the app UI (Android build with this feature):** open **Settings → Log Server (telemetry) →
Log Server IP**, and enter `http://192.168.105.79:5000` (a bare IP like `192.168.105.79` or
`192.168.105.79:5000` also works). The value is persisted and used on every launch **without**
subnet scanning; buffered logs are flushed immediately. Clear the field (save empty) to re-enable
auto-discovery.

**Programmatically (any build):**
```kotlin
// From anywhere with the app's context:
AgentLogger.setServerIpFromUser("http://192.168.105.79:5000") // persists + flushes + toasts
AgentLogger.clearServerIp(context)                            // back to auto-discovery
AgentLogger.getServerIp(context)                              // current value, or null
```
`setServerIpFromUser` is `@Synchronized`, accepts a bare IP / `host:port` / full URL, normalizes
to `http://host:5000`, persists to `agent_logger_prefs` (`server_ip`), updates the in-memory cache
immediately, and flushes any buffered logs. A user-pinned IP is never overwritten by a late
subnet-discovery hit.
