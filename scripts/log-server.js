/**
 * Standalone Node.js Wi-Fi Log Collector Server for MosqueDND Telemetry
 * 
 * Features:
 * - Self-healing port handling (Auto-terminates blocking process on 5000 or fallbacks to 5001).
 * - Dual QR Codes: Telemetry Configuration & APK Distribution Links.
 * - Non-blocking log persistence to ./logs/device-stream.log.
 * - Real-time colorized stdout log streaming.
 * - Permissive CORS for easy cross-network telemetry.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');

// Configuration
const DEFAULT_PORT = 5000;
const FALLBACK_PORT = 5001;
const LOGS_DIR = path.join(__dirname, '../logs');
const LOG_FILE_PATH = path.join(LOGS_DIR, 'device-stream.log');
const RELEASE_APK_PATH = path.join(__dirname, '../app/build/outputs/apk/release/MosqueDND.apk');
const APK_PATH = path.join(__dirname, '../app/build/outputs/apk/debug/MosqueDND.apk');
const FALLBACK_APK_PATH = path.join(__dirname, '../app/build/outputs/apk/debug/app-debug.apk');

function getResolvedApkPath() {
    if (fs.existsSync(RELEASE_APK_PATH)) {
        return RELEASE_APK_PATH;
    }
    if (fs.existsSync(APK_PATH)) {
        return APK_PATH;
    }
    if (fs.existsSync(FALLBACK_APK_PATH)) {
        return FALLBACK_APK_PATH;
    }
    return RELEASE_APK_PATH;
}

// Ensure ./logs/ directory exists synchronously
if (!fs.existsSync(LOGS_DIR)) {
    try {
        fs.mkdirSync(LOGS_DIR, { recursive: true });
        console.log(`[LogServer] Created logs directory at: ${LOGS_DIR}`);
    } catch (err) {
        console.error(`[LogServer] Failed to create logs directory:`, err);
    }
}

// Find primary local IPv4 non-internal network interface
function getLocalIPv4() {
    const interfaces = os.networkInterfaces();
    const candidates = [];
    for (const name of Object.keys(interfaces)) {
        for (const net of interfaces[name]) {
            // Skip over non-IPv4 and internal (127.0.0.1) addresses
            if (net.family === 'IPv4' && !net.internal) {
                // Heuristic: Prefer interfaces named 'en', 'eth', 'wlan', 'Wi-Fi' over virtual ones
                const isPreferred = /en|eth|wlan|Wi-Fi/i.test(name);
                candidates.push({ address: net.address, preferred: isPreferred });
            }
        }
    }
    const best = candidates.find(c => c.preferred) || candidates[0];
    return best ? best.address : '127.0.0.1';
}

const localIP = getLocalIPv4();

// ANSI Color codes for stdout formatting
const COLORS = {
    reset: '\x1b[0m',
    bright: '\x1b[1m',
    dim: '\x1b[2m',
    debug: '\x1b[36m', // Cyan
    info: '\x1b[32m',  // Green
    warn: '\x1b[33m',  // Yellow
    error: '\x1b[31m', // Red
    server: '\x1b[35m', // Magenta
    url: '\x1b[34m'     // Blue
};

function colorizeLog(level, text) {
    const color = COLORS[(level || 'info').toLowerCase()] || COLORS.info;
    return `${color}${text}${COLORS.reset}`;
}

/**
 * Attempts to terminate the process holding a specific port.
 * Supports Windows (taskkill) and Unix/macOS (kill).
 */
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
                // Fallback to fuser if lsof fails
                execSync(`fuser -k ${port}/tcp`, { stdio: 'ignore' });
            }
        }
        return true;
    } catch (err) {
        console.warn(`[LogServer] Could not programmatically terminate process on port ${port}:`, err.message);
        return false;
    }
}

const server = http.createServer((req, res) => {
    // Enable CORS headers for all requests
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    // Handle OPTIONS preflight request
    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const urlPath = req.url.split('?')[0];

    // GET / and GET /download
    if (req.method === 'GET' && (urlPath === '/' || urlPath === '/download')) {
        let apkSizeMb = 'N/A';
        let apkLastModified = 'Not built yet';
        const targetApkPath = getResolvedApkPath();
        const apkExists = fs.existsSync(targetApkPath);

        if (apkExists) {
            try {
                const stats = fs.statSync(targetApkPath);
                apkSizeMb = (stats.size / (1024 * 1024)).toFixed(2);
                apkLastModified = new Date(stats.mtime).toLocaleString();
            } catch (err) {
                console.error('[LogServer] Error reading APK stats:', err);
            }
        }

        const html = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MosqueDND APK Download</title>
    <style>
        :root {
            --primary: #1b5e20;
            --primary-hover: #2e7d32;
            --bg: #f4f6f8;
            --card-bg: #ffffff;
            --text: #212121;
            --subtext: #666666;
            --border: #e0e0e0;
        }
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 20px;
        }
        .card {
            background-color: var(--card-bg);
            max-width: 440px;
            width: 100%;
            border-radius: 16px;
            box-shadow: 0 4px 24px rgba(0,0,0,0.08);
            padding: 32px 24px;
            text-align: center;
        }
        .app-icon {
            font-size: 56px;
            margin-bottom: 12px;
        }
        h1 {
            font-size: 24px;
            font-weight: 700;
            color: var(--primary);
            margin-bottom: 6px;
        }
        .subtitle {
            font-size: 14px;
            color: var(--subtext);
            margin-bottom: 24px;
        }
        .details-box {
            background-color: #f8f9fa;
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 24px;
            text-align: left;
        }
        .detail-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 14px;
            padding: 8px 0;
        }
        .detail-row:not(:last-child) {
            border-bottom: 1px solid #eeeeee;
        }
        .detail-label {
            color: var(--subtext);
            font-weight: 500;
        }
        .detail-value {
            font-weight: 600;
            color: var(--text);
        }
        .btn-download {
            display: block;
            width: 100%;
            background-color: var(--primary);
            color: #ffffff;
            font-size: 16px;
            font-weight: 600;
            padding: 16px 20px;
            border-radius: 12px;
            text-decoration: none;
            box-shadow: 0 4px 12px rgba(27, 94, 32, 0.3);
            transition: background-color 0.2s, transform 0.1s;
        }
        .btn-download:hover {
            background-color: var(--primary-hover);
        }
        .btn-download:active {
            transform: scale(0.98);
        }
        .notice-error {
            background-color: #ffebee;
            color: #c62828;
            border: 1px solid #ffcdd2;
            padding: 12px;
            border-radius: 8px;
            font-size: 14px;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="app-icon">🕌</div>
        <h1>MosqueDND</h1>
        <p class="subtitle">Android Debug Build Distribution</p>
        
        <div class="details-box">
            <div class="detail-row">
                <span class="detail-label">File Name</span>
                <span class="detail-value">MosqueDND.apk</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">File Size</span>
                <span class="detail-value">${apkExists ? `${apkSizeMb} MB` : 'N/A'}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">Last Modified</span>
                <span class="detail-value">${apkLastModified}</span>
            </div>
        </div>

        ${apkExists 
            ? `<a href="/MosqueDND.apk" class="btn-download">Download MosqueDND APK</a>`
            : `<div class="notice-error">APK file not found. Run <code>./gradlew.bat assembleDebug</code> to generate the build.</div>`
        }
    </div>
</body>
</html>`;

        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(html);
        return;
    }

    // GET /MosqueDND.apk or GET /app-debug.apk (backward compatibility)
    if (req.method === 'GET' && (urlPath === '/MosqueDND.apk' || urlPath === '/app-debug.apk')) {
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
            stream.on('error', (streamErr) => {
                console.error('[LogServer] Error streaming APK:', streamErr);
                if (!res.headersSent) {
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ status: 'error', message: 'Failed streaming APK' }));
                }
            });
        } catch (err) {
            console.error('[LogServer] Error handling APK download:', err);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'error', message: 'Server error' }));
        }
        return;
    }

    // GET /api/config
    if (req.method === 'GET' && req.url === '/api/config') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            ip: localIP,
            port: server.address() ? server.address().port : DEFAULT_PORT,
            status: 'active',
            timestamp: new Date().toISOString(),
            service: 'MosqueDND Telemetry'
        }));
        return;
    }

    // POST /api/log
    if (req.method === 'POST' && req.url === '/api/log') {
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
            if (body.length > 2 * 1024 * 1024) { // 2MB limit
                req.destroy();
            }
        });

        req.on('end', () => {
            try {
                const logEntry = JSON.parse(body);
                const timestamp = logEntry.timestamp || new Date().toISOString();
                const level = (logEntry.level || 'INFO').toUpperCase();
                const deviceId = logEntry.deviceId || 'unknown-device';
                const message = logEntry.message || '';
                const stack = logEntry.stack ? `\nStack: ${logEntry.stack}` : '';

                const formattedLine = `[${timestamp}] [${deviceId}] [${level}] ${message}${stack}\n`;

                // Append to ./logs/device-stream.log
                fs.appendFile(LOG_FILE_PATH, formattedLine, err => {
                    if (err) {
                        console.error(`${COLORS.error}[LogServer] Failed to write to log file:${COLORS.reset}`, err);
                    }
                });

                // Stream formatted colorized log to stdout
                const stdoutLine = `[${timestamp}] [${deviceId}] [${level}] ${message}${stack}`;
                console.log(colorizeLog(level, stdoutLine));

                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'success' }));
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'error', message: 'Invalid JSON payload' }));
            }
        });
        return;
    }

    // 404 Not Found
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'error', message: 'Endpoint not found' }));
});

function displayServerInfo(port) {
    const configUrl = `http://${localIP}:${port}/api/config`;
    const apkUrl = `http://${localIP}:${port}/download`;

    console.log(`\n${COLORS.server}${COLORS.bright}======================================================${COLORS.reset}`);
    console.log(`${COLORS.server}${COLORS.bright} MosqueDND Wi-Fi Log Collector & Distribution Server${COLORS.reset}`);
    console.log(`${COLORS.server}======================================================${COLORS.reset}`);
    console.log(`${COLORS.info} Listening on: ${COLORS.url}http://${localIP}:${port}${COLORS.reset}`);
    console.log(`${COLORS.info} Log File:    ${COLORS.dim}${LOG_FILE_PATH}${COLORS.reset}`);
    console.log(`${COLORS.server}======================================================${COLORS.reset}\n`);

    try {
        const qrcode = require('qrcode-terminal');
        
        console.log(`${COLORS.bright}[1] TELEMETRY CONFIGURATION${COLORS.reset}`);
        console.log(`URL: ${COLORS.url}${configUrl}${COLORS.reset}`);
        console.log(`Scan to link mobile device to this workstation:`);
        qrcode.generate(configUrl, { small: true });
        console.log(`\n`);

        console.log(`${COLORS.bright}[2] APK DISTRIBUTION PORTAL${COLORS.reset}`);
        console.log(`URL: ${COLORS.url}${apkUrl}${COLORS.reset}`);
        console.log(`Scan to download the latest build from distribution server:`);
        qrcode.generate(apkUrl, { small: true });
        console.log(`\n`);
        
    } catch (e) {
        console.log(`${COLORS.warn}[Notice] 'qrcode-terminal' not found. URLs for manual entry:${COLORS.reset}`);
        console.log(`  Telemetry Config: ${configUrl}`);
        console.log(`  APK Distribution: ${apkUrl}\n`);
    }
    console.log(`${COLORS.server}${COLORS.bright}======================================================${COLORS.reset}\n`);
}

let hasKilledAndRetried = false;
let currentAttemptPort = DEFAULT_PORT;

function startListening(port) {
    currentAttemptPort = port;
    server.listen(port, '0.0.0.0');
}

server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
        console.warn(`[LogServer] Port ${currentAttemptPort} is in use by another process.`);
        
        if (currentAttemptPort === DEFAULT_PORT && !hasKilledAndRetried) {
            hasKilledAndRetried = true;
            const freed = killProcessOnPort(DEFAULT_PORT);
            if (freed) {
                console.log(`[LogServer] Port freed. Retrying in 1s...`);
                try { server.close(); } catch(e) {}
                setTimeout(() => startListening(DEFAULT_PORT), 1000);
            } else {
                console.log(`[LogServer] Could not free port ${DEFAULT_PORT}. Falling back to ${FALLBACK_PORT}...`);
                try { server.close(); } catch(e) {}
                startListening(FALLBACK_PORT);
            }
        } else if (currentAttemptPort === DEFAULT_PORT) {
            console.log(`[LogServer] Port ${DEFAULT_PORT} still busy after retry. Falling back to ${FALLBACK_PORT}...`);
            try { server.close(); } catch(e) {}
            startListening(FALLBACK_PORT);
        } else {
            console.error(`[Fatal] Fallback port ${currentAttemptPort} also in use. Server cannot start.`);
            process.exit(1);
        }
    } else {
        console.error(`[LogServer] Fatal server error:`, err);
        process.exit(1);
    }
});

server.on('listening', () => {
    const address = server.address();
    displayServerInfo(address.port);
});

// Start the server
startListening(DEFAULT_PORT);
