/**
 * Standalone Node.js Wi-Fi Log Collector & Telemetry Server for MosqueDND
 * 
 * Features:
 * - Deterministic port binding with fail-fast on conflict or graceful self-healing.
 * - Formal JSON Schema validation for telemetry message envelopes.
 * - Multi-device and multi-session namespacing and storage.
 * - Per-run debug authentication token generation & verification.
 * - Management & Operational Endpoints: /api/health, /api/config, /api/sessions, /api/logs, /api/sessions/export.
 * - Responsive Web Dashboard UI (on / and /viewer) with live tailing, filtering, search, pause/resume, and session selection.
 * - Non-blocking log persistence & graceful shutdown handling.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { execSync } = require('child_process');

// Configuration
const DEFAULT_PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 5000;
const FALLBACK_PORT = 5001;
const LOGS_DIR = path.join(__dirname, '../logs');
const SESSIONS_DIR = path.join(LOGS_DIR, 'sessions');
const RELEASE_APK_PATH = path.join(__dirname, '../app/build/outputs/apk/release/MosqueDND.apk');
const DEBUG_APK_PATH = path.join(__dirname, '../app/build/outputs/apk/debug/MosqueDND.apk');
const FALLBACK_APK_PATH = path.join(__dirname, '../app/build/outputs/apk/debug/app-debug.apk');
const SCHEMA_PATH = path.join(__dirname, '../app/src/main/assets/schemas/telemetry-envelope.json');

// Per-run authentication token
const AUTH_TOKEN = crypto.randomBytes(16).toString('hex');
let authEnforced = false; // Can be toggled via POST /api/config

// In-memory telemetry data store with multi-device and multi-session namespacing
// structure: { deviceId: { deviceId, lastSeen, sessions: { sessionId: { sessionId, startTime, lastActivity, logCount, errorCount, logs: [] } } } }
const telemetryStore = {
    devices: {},
    globalLogs: [] // ring buffer of last 2000 logs across all devices
};

// Rate limiter tracking: deviceId -> { count, windowStart }
const rateLimitMap = new Map();
const RATE_LIMIT_MAX = 200; // max logs per window
const RATE_LIMIT_WINDOW_MS = 1000;

// Load JSON Schema
let telemetrySchema = null;
try {
    if (fs.existsSync(SCHEMA_PATH)) {
        telemetrySchema = JSON.parse(fs.readFileSync(SCHEMA_PATH, 'utf8'));
    }
} catch (err) {
    console.warn('[LogServer] Could not load telemetry-envelope.json schema:', err.message);
}

function validateEnvelope(data) {
    if (!data || typeof data !== 'object') return { valid: false, errors: ['Payload must be a JSON object'] };
    
    const errors = [];
    const required = ['schemaVersion', 'sessionId', 'deviceId', 'sequenceNumber', 'monotonicTimestamp', 'wallClockTimestamp', 'messageType', 'payload'];
    for (const field of required) {
        if (data[field] === undefined || data[field] === null) {
            errors.push(`Missing required field: ${field}`);
        }
    }

    if (errors.length > 0) return { valid: false, errors };

    if (typeof data.sessionId !== 'string' || !data.sessionId.trim()) errors.push('sessionId must be a non-empty string');
    if (typeof data.deviceId !== 'string' || !data.deviceId.trim()) errors.push('deviceId must be a non-empty string');
    if (typeof data.sequenceNumber !== 'number' || data.sequenceNumber < 0) errors.push('sequenceNumber must be a non-negative integer');
    if (typeof data.monotonicTimestamp !== 'number' || data.monotonicTimestamp < 0) errors.push('monotonicTimestamp must be a non-negative number');
    if (typeof data.wallClockTimestamp !== 'string') errors.push('wallClockTimestamp must be a string');
    if (typeof data.messageType !== 'string') errors.push('messageType must be a string');

    if (!data.payload || typeof data.payload !== 'object') {
        errors.push('payload must be an object');
    } else {
        if (!data.payload.level) errors.push('payload.level is required');
        if (!data.payload.message) errors.push('payload.message is required');
    }

    return { valid: errors.length === 0, errors };
}

function getResolvedApkPath() {
    if (fs.existsSync(RELEASE_APK_PATH)) return RELEASE_APK_PATH;
    if (fs.existsSync(DEBUG_APK_PATH)) return DEBUG_APK_PATH;
    if (fs.existsSync(FALLBACK_APK_PATH)) return FALLBACK_APK_PATH;
    return RELEASE_APK_PATH;
}

function getApkVersion() {
    try {
        const versionPropsPath = path.join(__dirname, '../app/version.properties');
        if (fs.existsSync(versionPropsPath)) {
            const content = fs.readFileSync(versionPropsPath, 'utf8');
            const match = content.match(/VERSION_NAME\s*=\s*(.+)/);
            if (match && match[1].trim()) return match[1].trim();
        }
    } catch (err) {}
    try {
        const buildGradlePath = path.join(__dirname, '../app/build.gradle');
        if (fs.existsSync(buildGradlePath)) {
            const content = fs.readFileSync(buildGradlePath, 'utf8');
            const match = content.match(/versionName\s+"([^"]+)"/);
            if (match && match[1].trim()) return match[1].trim();
        }
    } catch (err) {}
    return '1.0.0';
}

// Ensure directories exist
[LOGS_DIR, SESSIONS_DIR].forEach(dir => {
    if (!fs.existsSync(dir)) {
        try {
            fs.mkdirSync(dir, { recursive: true });
        } catch (err) {
            console.error(`[LogServer] Failed to create directory ${dir}:`, err);
        }
    }
});

function getLocalIPv4() {
    const interfaces = os.networkInterfaces();
    const candidates = [];
    for (const name of Object.keys(interfaces)) {
        for (const net of interfaces[name]) {
            if (net.family === 'IPv4' && !net.internal) {
                const isPreferred = /en|eth|wlan|Wi-Fi/i.test(name);
                candidates.push({ address: net.address, preferred: isPreferred });
            }
        }
    }
    const best = candidates.find(c => c.preferred) || candidates[0];
    return best ? best.address : '127.0.0.1';
}

const localIP = getLocalIPv4();
const startTimeMs = Date.now();

// ANSI Colors
const COLORS = {
    reset: '\x1b[0m',
    bright: '\x1b[1m',
    dim: '\x1b[2m',
    debug: '\x1b[36m',
    info: '\x1b[32m',
    warn: '\x1b[33m',
    error: '\x1b[31m',
    fatal: '\x1b[35m',
    server: '\x1b[35m',
    url: '\x1b[34m'
};

function colorizeLog(level, text) {
    const color = COLORS[(level || 'info').toLowerCase()] || COLORS.info;
    return `${color}${text}${COLORS.reset}`;
}

function killProcessOnPort(port) {
    try {
        console.log(`[LogServer] Attempting to free port ${port}...`);
        if (process.platform === 'win32') {
            const output = execSync(`netstat -ano -p tcp`, { encoding: 'utf8' });
            const lines = output.split('\n');
            const pids = new Set();
            for (const line of lines) {
                if (line.includes(`:${port}`) && line.includes('LISTENING')) {
                    const parts = line.trim().split(/\s+/);
                    const pid = parts[parts.length - 1];
                    if (pid && pid !== '0' && pid !== process.pid.toString()) {
                        pids.add(pid);
                    }
                }
            }
            if (pids.size === 0) return false;
            for (const pid of pids) {
                console.log(`[LogServer] Windows: Killing PID ${pid} holding port ${port}...`);
                execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore' });
            }
        } else {
            try {
                const output = execSync(`lsof -t -i:${port}`, { encoding: 'utf8' }).trim();
                if (!output) return false;
                const pids = output.split('\n').map(p => p.trim()).filter(Boolean);
                for (const pid of pids) {
                    if (pid && pid !== process.pid.toString()) {
                        console.log(`[LogServer] Unix: Killing PID ${pid} holding port ${port}...`);
                        execSync(`kill -9 ${pid}`, { stdio: 'ignore' });
                    }
                }
            } catch (e) {
                execSync(`fuser -k ${port}/tcp`, { stdio: 'ignore' });
            }
        }
        return true;
    } catch (err) {
        console.warn(`[LogServer] Could not terminate process on port ${port}:`, err.message);
        return false;
    }
}

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS, PUT, DELETE');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Telemetry-Token, Authorization');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = urlObj.pathname;

    // 1. GET /api/health
    if (req.method === 'GET' && pathname === '/api/health') {
        const uptimeSeconds = Math.floor((Date.now() - startTimeMs) / 1000);
        const deviceCount = Object.keys(telemetryStore.devices).length;
        let sessionCount = 0;
        Object.values(telemetryStore.devices).forEach(d => {
            sessionCount += Object.keys(d.sessions || {}).length;
        });

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'healthy',
            service: 'MosqueDND Telemetry Collector',
            version: '1.0.0',
            uptimeSeconds,
            activeDevices: deviceCount,
            activeSessions: sessionCount,
            totalLogsBuffered: telemetryStore.globalLogs.length,
            authEnforced,
            memoryUsage: process.memoryUsage(),
            timestamp: new Date().toISOString()
        }));
        return;
    }

    // 2. GET /api/config
    if (req.method === 'GET' && pathname === '/api/config') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            ip: localIP,
            port: server.address() ? server.address().port : DEFAULT_PORT,
            status: 'active',
            service: 'MosqueDND Telemetry',
            authToken: AUTH_TOKEN,
            authEnforced,
            timestamp: new Date().toISOString()
        }));
        return;
    }

    // 3. POST /api/config
    if (req.method === 'POST' && pathname === '/api/config') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            try {
                const configData = JSON.parse(body);
                if (configData.authEnforced !== undefined) {
                    authEnforced = Boolean(configData.authEnforced);
                }
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'success', authEnforced, authToken: AUTH_TOKEN }));
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'error', message: 'Invalid JSON config payload' }));
            }
        });
        return;
    }

    // 4. GET /api/sessions
    if (req.method === 'GET' && pathname === '/api/sessions') {
        const sessionsList = [];
        Object.keys(telemetryStore.devices).forEach(deviceId => {
            const dev = telemetryStore.devices[deviceId];
            Object.keys(dev.sessions || {}).forEach(sessionId => {
                const sess = dev.sessions[sessionId];
                sessionsList.push({
                    deviceId,
                    sessionId,
                    startTime: sess.startTime,
                    lastActivity: sess.lastActivity,
                    logCount: sess.logCount,
                    errorCount: sess.errorCount
                });
            });
        });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'success', sessions: sessionsList }));
        return;
    }

    // 5. GET /api/sessions/export
    if (req.method === 'GET' && pathname === '/api/sessions/export') {
        const deviceIdParam = urlObj.searchParams.get('deviceId');
        const sessionIdParam = urlObj.searchParams.get('sessionId');

        let exportData = [];
        if (deviceIdParam && sessionIdParam) {
            const sess = telemetryStore.devices[deviceIdIdParam(deviceIdParam)]?.sessions?.[sessionIdParam];
            if (sess) exportData = sess.logs;
        } else {
            exportData = telemetryStore.globalLogs;
        }

        res.writeHead(200, {
            'Content-Type': 'application/json',
            'Content-Disposition': `attachment; filename="telemetry-export-${Date.now()}.json"`
        });
        res.end(JSON.stringify(exportData, null, 2));
        return;
    }

    // 6. GET /api/logs
    if (req.method === 'GET' && pathname === '/api/logs') {
        const deviceId = urlObj.searchParams.get('deviceId');
        const sessionId = urlObj.searchParams.get('sessionId');
        const level = urlObj.searchParams.get('level');
        const tag = urlObj.searchParams.get('tag');
        const search = urlObj.searchParams.get('search');
        const limit = parseInt(urlObj.searchParams.get('limit') || '200', 10);

        let filtered = telemetryStore.globalLogs;
        if (deviceId) {
            filtered = filtered.filter(l => l.deviceId === deviceId);
        }
        if (sessionId) {
            filtered = filtered.filter(l => l.sessionId === sessionId);
        }
        if (level) {
            filtered = filtered.filter(l => (l.payload?.level || '').toUpperCase() === level.toUpperCase());
        }
        if (tag) {
            filtered = filtered.filter(l => (l.payload?.tag || '').toLowerCase().includes(tag.toLowerCase()));
        }
        if (search) {
            const q = search.toLowerCase();
            filtered = filtered.filter(l => {
                const msg = (l.payload?.message || '').toLowerCase();
                const stk = (l.payload?.stack || '').toLowerCase();
                return msg.includes(q) || stk.includes(q);
            });
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'success',
            count: filtered.length,
            logs: filtered.slice(-limit)
        }));
        return;
    }

    // Helper for deviceId param
    function deviceIdIdParam(id) { return id; }

    // 7. POST /api/log (Incoming device telemetry event)
    if (req.method === 'POST' && (pathname === '/api/log' || pathname === '/api/telemetry')) {
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
            if (body.length > 5 * 1024 * 1024) { // 5MB limit
                req.destroy();
            }
        });

        req.on('end', () => {
            try {
                const envelope = JSON.parse(body);

                // Authentication Check if enforced
                if (authEnforced) {
                    const clientToken = req.headers['x-telemetry-token'] || req.headers['authorization']?.replace('Bearer ', '') || urlObj.searchParams.get('token') || envelope.authToken;
                    if (clientToken !== AUTH_TOKEN) {
                        res.writeHead(401, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ status: 'error', message: 'Unauthorized: Invalid or missing telemetry auth token' }));
                        return;
                    }
                }

                // Schema Validation
                const validation = validateEnvelope(envelope);
                if (!validation.valid) {
                    res.writeHead(400, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ status: 'error', message: 'Schema validation failed', errors: validation.errors }));
                    return;
                }

                const { deviceId, sessionId, sequenceNumber, monotonicTimestamp, wallClockTimestamp, messageType, payload } = envelope;
                const level = (payload.level || 'INFO').toUpperCase();
                const message = payload.message || '';
                const tag = payload.tag || 'General';
                const stack = payload.stack || null;

                // Rate limiting check per device
                const now = Date.now();
                let rateRecord = rateLimitMap.get(deviceId);
                if (!rateRecord || now - rateRecord.windowStart > RATE_LIMIT_WINDOW_MS) {
                    rateRecord = { count: 0, windowStart: now };
                    rateLimitMap.set(deviceId, rateRecord);
                }
                rateRecord.count++;
                if (rateRecord.count > RATE_LIMIT_MAX) {
                    res.writeHead(429, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ status: 'error', message: 'Rate limit exceeded. Slow down.' }));
                    return;
                }

                // Multi-device and multi-session namespacing initialization in store
                if (!telemetryStore.devices[deviceId]) {
                    telemetryStore.devices[deviceId] = {
                        deviceId,
                        firstSeen: wallClockTimestamp,
                        lastSeen: wallClockTimestamp,
                        sessions: {}
                    };
                }
                const deviceRecord = telemetryStore.devices[deviceId];
                deviceRecord.lastSeen = wallClockTimestamp;

                if (!deviceRecord.sessions[sessionId]) {
                    deviceRecord.sessions[sessionId] = {
                        sessionId,
                        startTime: wallClockTimestamp,
                        lastActivity: wallClockTimestamp,
                        logCount: 0,
                        errorCount: 0,
                        logs: []
                    };
                }
                const sessionRecord = deviceRecord.sessions[sessionId];
                sessionRecord.lastActivity = wallClockTimestamp;
                sessionRecord.logCount++;
                if (level === 'ERROR' || level === 'FATAL') {
                    sessionRecord.errorCount++;
                }

                sessionRecord.logs.push(envelope);
                telemetryStore.globalLogs.push(envelope);
                if (telemetryStore.globalLogs.length > 2000) {
                    telemetryStore.globalLogs.shift();
                }

                // Persist session log to file asynchronously
                const sessionLogFile = path.join(SESSIONS_DIR, `${deviceId}_${sessionId}.ndjson`);
                fs.appendFile(sessionLogFile, JSON.stringify(envelope) + '\n', err => {
                    if (err) console.error('[LogServer] Error writing to session log file:', err);
                });

                // Console output
                const stackStr = stack ? `\nStack: ${stack}` : '';
                const stdoutLine = `[${wallClockTimestamp}] [Dev:${deviceId}] [Sess:${sessionId}] [Seq:${sequenceNumber}] [${level}] [${tag}] ${message}${stackStr}`;
                console.log(colorizeLog(level, stdoutLine));

                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'success', serverTime: new Date().toISOString() }));
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'error', message: 'Malformed JSON payload: ' + e.message }));
            }
        });
        return;
    }

    // 8. GET / and GET /viewer (Web Dashboard UI & APK Distribution Portal)
    if (req.method === 'GET' && (pathname === '/' || pathname === '/viewer' || pathname === '/download')) {
        let apkSizeMb = 'N/A';
        let apkLastModified = 'Not built yet';
        const targetApkPath = getResolvedApkPath();
        const apkExists = fs.existsSync(targetApkPath);

        if (apkExists) {
            try {
                const stats = fs.statSync(targetApkPath);
                apkSizeMb = (stats.size / (1024 * 1024)).toFixed(2);
                apkLastModified = new Date(stats.mtime).toLocaleString();
            } catch (err) {}
        }

        const apkVersion = getApkVersion();

        const html = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MosqueDND Telemetry & Debug Dashboard</title>
    <style>
        :root {
            --primary: #1b5e20;
            --primary-hover: #2e7d32;
            --bg: #f4f6f8;
            --card-bg: #ffffff;
            --text: #212121;
            --subtext: #666666;
            --border: #e0e0e0;
            --debug: #00838f;
            --info: #2e7d32;
            --warn: #f57f17;
            --error: #c62828;
            --fatal: #ad1457;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); height: 100vh; display: flex; flex-direction: column; overflow: hidden; }
        header { background: var(--primary); color: #fff; padding: 12px 24px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
        header h1 { font-size: 20px; font-weight: 600; display: flex; align-items: center; gap: 10px; }
        .header-meta { display: flex; gap: 16px; font-size: 13px; align-items: center; }
        .badge { background: rgba(255,255,255,0.2); padding: 4px 10px; border-radius: 12px; font-weight: 500; }
        
        .main-layout { display: flex; flex: 1; overflow: hidden; }
        sidebar { width: 300px; background: var(--card-bg); border-right: 1px solid var(--border); display: flex; flex-direction: column; }
        .sidebar-header { padding: 16px; border-bottom: 1px solid var(--border); font-weight: 600; font-size: 14px; color: var(--subtext); }
        .session-list { flex: 1; overflow-y: auto; padding: 8px; }
        .session-item { padding: 12px; border-radius: 8px; cursor: pointer; margin-bottom: 6px; border: 1px solid var(--border); background: #fafafa; transition: all 0.2s; }
        .session-item:hover { background: #f0f4f1; border-color: var(--primary); }
        .session-item.active { background: #e8f5e9; border-color: var(--primary); }
        .session-device { font-weight: 600; font-size: 13px; color: var(--primary); margin-bottom: 4px; }
        .session-id { font-size: 11px; color: var(--subtext); word-break: break-all; }
        .session-stats { display: flex; gap: 8px; margin-top: 6px; font-size: 11px; }

        .content-area { flex: 1; display: flex; flex-direction: column; background: #fff; overflow: hidden; }
        .toolbar { padding: 12px 20px; border-bottom: 1px solid var(--border); display: flex; gap: 12px; align-items: center; background: #fafafa; flex-wrap: wrap; }
        .toolbar input, .toolbar select { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; outline: none; }
        .toolbar input[type="text"] { flex: 1; min-width: 200px; }
        .btn { padding: 8px 16px; border-radius: 6px; border: none; font-weight: 600; font-size: 13px; cursor: pointer; transition: background 0.2s; }
        .btn-primary { background: var(--primary); color: #fff; }
        .btn-primary:hover { background: var(--primary-hover); }
        .btn-secondary { background: #e0e0e0; color: var(--text); }
        .btn-secondary:hover { background: #d0d0d0; }

        .logs-container { flex: 1; overflow-y: auto; padding: 12px; font-family: monospace; font-size: 12px; background: #0d1117; color: #c9d1d9; }
        .log-row { padding: 6px 8px; border-bottom: 1px solid #21262d; display: flex; gap: 10px; align-items: flex-start; }
        .log-time { color: #8b949e; white-space: nowrap; }
        .log-device { color: #79c0ff; white-space: nowrap; max-width: 120px; overflow: hidden; text-overflow: ellipsis; }
        .log-level { padding: 2px 6px; border-radius: 4px; font-weight: bold; font-size: 10px; text-transform: uppercase; }
        .lvl-debug { background: rgba(0,131,143,0.2); color: #4dd0e1; }
        .lvl-info { background: rgba(46,125,50,0.2); color: #81c784; }
        .lvl-warn { background: rgba(245,127,23,0.2); color: #ffb74d; }
        .lvl-error { background: rgba(198,40,40,0.2); color: #e57373; }
        .lvl-fatal { background: rgba(173,20,87,0.2); color: #f06292; }
        .log-tag { color: #d2a8ff; font-weight: 500; }
        .log-msg { flex: 1; word-break: break-all; white-space: pre-wrap; }
        .log-stack { color: #f85149; margin-top: 4px; font-size: 11px; white-space: pre-wrap; }

        .tab-content { display: none; flex: 1; flex-direction: column; overflow: hidden; }
        .tab-content.active { display: flex; }
        .portal-card { max-width: 500px; margin: 40px auto; background: var(--card-bg); padding: 32px; border-radius: 12px; border: 1px solid var(--border); box-shadow: 0 4px 16px rgba(0,0,0,0.06); text-align: center; }
        .portal-card h2 { color: var(--primary); margin-bottom: 12px; }
    </style>
</head>
<body>
    <header>
        <h1><span>🕌</span> MosqueDND Telemetry Dashboard</h1>
        <div class="header-meta">
            <span class="badge">Auth Token: <code id="authTokenDisplay">${AUTH_TOKEN}</code></span>
            <button class="btn btn-secondary" onclick="switchTab('dashboard')">Dashboard</button>
            <button class="btn btn-secondary" onclick="switchTab('download')">APK Portal</button>
        </div>
    </header>

    <div class="main-layout tab-content active" id="tab-dashboard">
        <sidebar>
            <div class="sidebar-header">Active Sessions</div>
            <div class="session-list" id="sessionList">
                <div style="padding: 12px; color: var(--subtext); text-align: center;">Waiting for devices...</div>
            </div>
        </sidebar>
        <div class="content-area">
            <div class="toolbar">
                <input type="text" id="searchInput" placeholder="Search logs or stack traces..." oninput="filterLogs()">
                <select id="levelFilter" onchange="filterLogs()">
                    <option value="">All Levels</option>
                    <option value="DEBUG">DEBUG</option>
                    <option value="INFO">INFO</option>
                    <option value="WARN">WARN</option>
                    <option value="ERROR">ERROR</option>
                    <option value="FATAL">FATAL</option>
                </select>
                <button class="btn btn-secondary" id="pauseBtn" onclick="togglePause()">Pause</button>
                <button class="btn btn-secondary" onclick="clearLogs()">Clear</button>
                <button class="btn btn-primary" onclick="exportLogs()">Export JSON</button>
            </div>
            <div class="logs-container" id="logsContainer">
                <!-- Live logs injected here -->
            </div>
        </div>
    </div>

    <div class="main-layout tab-content" id="tab-download" style="overflow-y: auto; justify-content: center; align-items: center;">
        <div class="portal-card">
            <h2>MosqueDND APK Distribution</h2>
            <p style="color: var(--subtext); margin-bottom: 20px;">Download the latest debug/release build directly to your device.</p>
            <div style="background: #f8f9fa; border: 1px solid var(--border); border-radius: 8px; padding: 16px; margin-bottom: 20px; text-align: left; font-size: 14px;">
                <div style="display: flex; justify-content: space-between; padding: 6px 0;"><span>Version:</span><strong>v${apkVersion}</strong></div>
                <div style="display: flex; justify-content: space-between; padding: 6px 0;"><span>Size:</span><strong>${apkExists ? `${apkSizeMb} MB` : 'N/A'}</strong></div>
                <div style="display: flex; justify-content: space-between; padding: 6px 0;"><span>Modified:</span><strong>${apkLastModified}</strong></div>
            </div>
            ${apkExists ? `<a href="/MosqueDND.apk" class="btn btn-primary" style="display: block; text-decoration: none; padding: 14px; border-radius: 8px;">Download APK</a>` : `<div style="color: var(--error);">APK not found. Run gradle build first.</div>`}
        </div>
    </div>

    <script>
        let allLogs = [];
        let sessionsData = {};
        let selectedDeviceId = null;
        let selectedSessionId = null;
        let isPaused = false;

        function switchTab(tab) {
            document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
            if (tab === 'dashboard') document.getElementById('tab-dashboard').classList.add('active');
            if (tab === 'download') document.getElementById('tab-download').classList.add('active');
        }

        function togglePause() {
            isPaused = !isPaused;
            document.getElementById('pauseBtn').textContent = isPaused ? 'Resume' : 'Pause';
            document.getElementById('pauseBtn.style.background') = isPaused ? '#f57f17' : '';
        }

        function clearLogs() {
            allLogs = [];
            renderLogs();
        }

        function exportLogs() {
            window.location.href = '/api/sessions/export' + (selectedDeviceId && selectedSessionId ? '?deviceId=' + selectedDeviceId + '&sessionId=' + selectedSessionId : '');
        }

        function selectSession(devId, sessId) {
            selectedDeviceId = devId;
            selectedSessionId = sessId;
            renderSessions();
            renderLogs();
        }

        async function fetchSessionsAndLogs() {
            try {
                const res = await fetch('/api/sessions');
                const data = await res.json();
                if (data.status === 'success') {
                    sessionsData = data.sessions;
                    renderSessions();
                }

                const logsRes = await fetch('/api/logs?limit=500');
                const logsData = await logsRes.json();
                if (logsData.status === 'success' && !isPaused) {
                    allLogs = logsData.logs;
                    renderLogs();
                }
            } catch (e) {
                console.error('Failed to fetch telemetry data:', e);
            }
        }

        function renderSessions() {
            const listEl = document.getElementById('sessionList');
            const grouped = {};
            sessionsData.forEach(s => {
                if (!grouped[s.deviceId]) grouped[s.deviceId] = [];
                grouped[s.deviceId].push(s);
            });

            if (Object.keys(grouped).length === 0) {
                listEl.innerHTML = '<div style="padding: 12px; color: var(--subtext); text-align: center;">Waiting for devices...</div>';
                return;
            }

            let html = '';
            for (const [devId, sessions] of Object.entries(grouped)) {
                sessions.forEach(s => {
                    const isActive = s.deviceId === selectedDeviceId && s.sessionId === selectedSessionId;
                    html += \`<div class="session-item \${isActive ? 'active' : ''}" onclick="selectSession('\${s.deviceId}', '\$'\` + s.sessionId + \`')">
                        <div class="session-device">📱 \${s.deviceId}</div>
                        <div class="session-id">Session: \${s.sessionId.substring(0, 16)}...</div>
                        <div class="session-stats">
                            <span style="color: var(--info);">Logs: \${s.logCount}</span>
                            <span style="color: var(--error);">Errors: \${s.errorCount}</span>
                        </div>
                    </div>\`;
                });
            }
            listEl.innerHTML = html;
        }

        function filterLogs() {
            renderLogs();
        }

        function renderLogs() {
            const container = document.getElementById('logsContainer');
            const search = document.getElementById('searchInput').value.toLowerCase();
            const levelFilter = document.getElementById('levelFilter').value;

            let filtered = allLogs;
            if (selectedDeviceId) filtered = filtered.filter(l => l.deviceId === selectedDeviceId);
            if (selectedSessionId) filtered = filtered.filter(l => l.sessionId === selectedSessionId);
            if (levelFilter) filtered = filtered.filter(l => (l.payload?.level || '').toUpperCase() === levelFilter);
            if (search) {
                filtered = filtered.filter(l => {
                    const msg = (l.payload?.message || '').toLowerCase();
                    const stk = (l.payload?.stack || '').toLowerCase();
                    return msg.includes(search) || stk.includes(search);
                });
            }

            let html = '';
            filtered.forEach(l => {
                const lvl = (l.payload?.level || 'INFO').toUpperCase();
                const lvlClass = 'lvl-' + lvl.toLowerCase();
                const time = l.wallClockTimestamp ? l.wallClockTimestamp.split('T')[1]?.substring(0, 12) || l.wallClockTimestamp : '';
                const tag = l.payload?.tag || 'General';
                const msg = escapeHtml(l.payload?.message || '');
                const stack = l.payload?.stack ? \`<div class="log-stack">\${escapeHtml(l.payload.stack)}</div>\` : '';

                html += \`<div class="log-row">
                    <span class="log-time">\${time}</span>
                    <span class="log-device">\${l.deviceId}</span>
                    <span class="log-level \${lvlClass}">\${lvl}</span>
                    <span class="log-tag">[\${tag}]</span>
                    <div class="log-msg">\${msg}\${stack}</div>
                </div>\`;
            });

            container.innerHTML = html || '<div style="color: #8b949e; text-align: center; padding: 20px;">No telemetry logs matching criteria</div>';
            if (!isPaused) {
                container.scrollTop = container.scrollHeight;
            }
        }

        function escapeHtml(str) {
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        setInterval(fetchSessionsAndLogs, 2000);
        fetchSessionsAndLogs();
    </script>
</body>
</html>`;

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(html);
        return;
    }

    // 9. GET /MosqueDND.apk or GET /app-debug.apk
    if (req.method === 'GET' && (pathname === '/MosqueDND.apk' || pathname === '/app-debug.apk')) {
        const targetApkPath = getResolvedApkPath();
        if (!fs.existsSync(targetApkPath)) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'error', message: 'APK file not found' }));
            return;
        }

        try {
            const stats = fs.statSync(targetApkPath);
            res.writeHead(200, {
                'Content-Type': 'application/vnd.android.package-archive',
                'Content-Disposition': 'attachment; filename="MosqueDND.apk"',
                'Content-Length': stats.size
            });
            const stream = fs.createReadStream(targetApkPath);
            stream.pipe(res);
            stream.on('error', (err) => {
                console.error('[LogServer] Error streaming APK:', err);
                if (!res.headersSent) {
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ status: 'error', message: 'Failed streaming APK' }));
                }
            });
        } catch (err) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'error', message: 'Server error' }));
        }
        return;
    }

    // 404 Not Found
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'error', message: 'Endpoint not found' }));
});

function displayServerInfo(port) {
    const configUrl = `http://${localIP}:${port}/api/config`;
    const dashboardUrl = `http://${localIP}:${port}/`;

    console.log(`\n${COLORS.server}${COLORS.bright}======================================================${COLORS.reset}`);
    console.log(`${COLORS.server}${COLORS.bright} MosqueDND Phase 1 Live Debug Telemetry Collector${COLORS.reset}`);
    console.log(`${COLORS.server}======================================================${COLORS.reset}`);
    console.log(`${COLORS.info} Dashboard:   ${COLORS.url}${dashboardUrl}${COLORS.reset}`);
    console.log(`${COLORS.info} Config API:  ${COLORS.url}${configUrl}${COLORS.reset}`);
    console.log(`${COLORS.info} Auth Token:  ${COLORS.bright}${AUTH_TOKEN}${COLORS.reset}`);
    console.log(`${COLORS.info} Log File:    ${COLORS.dim}${LOGS_DIR}${COLORS.reset}`);
    console.log(`${COLORS.server}======================================================${COLORS.reset}\n`);

    try {
        const qrcode = require('qrcode-terminal');
        console.log(`${COLORS.bright}[1] TELEMETRY DASHBOARD & CONFIG QR${COLORS.reset}`);
        qrcode.generate(dashboardUrl, { small: true });
        console.log(`\n`);
    } catch (e) {
        console.log(`${COLORS.warn}[Notice] 'qrcode-terminal' not installed. Dashboard URL: ${dashboardUrl}${COLORS.reset}\n`);
    }
}

let currentAttemptPort = DEFAULT_PORT;

function startListening(port) {
    currentAttemptPort = port;
    server.listen(port, '0.0.0.0');
}

server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
        console.warn(`[LogServer] Port ${currentAttemptPort} is in use.`);
        if (currentAttemptPort === DEFAULT_PORT) {
            const freed = killProcessOnPort(DEFAULT_PORT);
            if (freed) {
                console.log(`[LogServer] Port freed. Retrying in 1s...`);
                try { server.close(); } catch(e) {}
                setTimeout(() => startListening(DEFAULT_PORT), 1000);
            } else {
                console.warn(`[LogServer] Could not free port ${DEFAULT_PORT}. Trying fallback port ${FALLBACK_PORT}...`);
                try { server.close(); } catch(e) {}
                startListening(FALLBACK_PORT);
            }
        } else {
            console.error(`[Fatal] Port conflict on both primary and fallback ports. Fail-fast exit.`);
            process.exit(1);
        }
    } else {
        console.error(`[LogServer] Server error:`, err);
        process.exit(1);
    }
});

server.on('listening', () => {
    const address = server.address();
    displayServerInfo(address.port);
});

// Graceful shutdown handling
function gracefulShutdown(signal) {
    console.log(`\n[LogServer] Received ${signal}. Shutting down gracefully...`);
    server.close(() => {
        console.log(`[LogServer] HTTP server closed.`);
        process.exit(0);
    });
    // Force close after 3s if hanging
    setTimeout(() => {
        console.warn(`[LogServer] Forced shutdown after timeout.`);
        process.exit(0);
    }, 3000);
}

process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));

startListening(DEFAULT_PORT);
