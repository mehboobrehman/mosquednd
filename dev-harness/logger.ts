/**
 * dev-harness/logger.ts
 * 
 * Autonomous Wi-Fi Discovery & Telemetry Logger for MosqueDND.
 * Features:
 * - Persistent caching of server IP/Port.
 * - Non-blocking subnet discovery across common local IP ranges.
 * - Non-blocking 2s timeout for all network operations.
 * - Global event emitter for log stream and connection status.
 */

// Simple storage interface for decoupled environments
interface Storage {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
}

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL';

export interface LogEntry {
  id: string;
  level: LogLevel;
  message: string;
  stack?: string;
  timestamp: string;
  tag: string;
  deviceId: string;
}

export interface ServerConfig {
  ip: string;
  port: number;
  status: 'active' | 'disconnected' | 'probing';
}

class TelemetryLogger {
  private static instance: TelemetryLogger;
  private serverIp: string | null = null;
  private serverPort: number = 5000;
  private deviceId: string = 'ts-dev-client';
  private logs: LogEntry[] = [];
  private pendingDispatch: LogEntry[] = [];
  private maxLogs = 100;
  private status: 'active' | 'disconnected' | 'probing' = 'disconnected';
  private storage: Storage | null = null;
  private listeners: Set<(entry: LogEntry) => void> = new Set();
  private statusListeners: Set<(config: ServerConfig) => void> = new Set();

  private constructor() {
    this.deviceId = `device-${Math.random().toString(36).substring(2, 8)}`;
    this.initStorage();
  }

  public static getInstance(): TelemetryLogger {
    if (!TelemetryLogger.instance) {
      TelemetryLogger.instance = new TelemetryLogger();
    }
    return TelemetryLogger.instance;
  }

  private async initStorage() {
    try {
      // Attempt to use React Native AsyncStorage if available globally or via require
      // In a decoupled module, we check several common locations
      const RNAsyncStorage = (global as any).AsyncStorage || 
                            (global as any).ReactNative?.AsyncStorage;
      
      if (RNAsyncStorage) {
        this.storage = RNAsyncStorage;
      } else {
        // Fallback to localStorage if in browser environment
        if (typeof window !== 'undefined' && window.localStorage) {
          this.storage = {
            getItem: async (key) => window.localStorage.getItem(key),
            setItem: async (key, value) => window.localStorage.setItem(key, value)
          };
        }
      }

      if (this.storage) {
        const cachedIp = await this.storage.getItem('MOSQUE_DND_TELEMETRY_IP');
        const cachedPort = await this.storage.getItem('MOSQUE_DND_TELEMETRY_PORT');
        if (cachedIp) {
          this.serverIp = cachedIp;
          this.serverPort = cachedPort ? parseInt(cachedPort, 10) : 5000;
          this.testConnection();
        }
      }
    } catch (e) {
      // Silent fail for storage initialization
    }
  }

  public setServerUrl(ip: string, port: number = 5000) {
    this.serverIp = ip;
    this.serverPort = port;
    this.saveConfig();
    this.testConnection();
  }

  private async saveConfig() {
    if (this.storage && this.serverIp) {
      await this.storage.setItem('MOSQUE_DND_TELEMETRY_IP', this.serverIp);
      await this.storage.setItem('MOSQUE_DND_TELEMETRY_PORT', this.serverPort.toString());
    }
  }

  public async testConnection(): Promise<boolean> {
    if (!this.serverIp) return false;
    
    this.updateStatus('probing');
    const success = await this.probeIp(this.serverIp, this.serverPort);
    this.updateStatus(success ? 'active' : 'disconnected');
    return success;
  }

  private updateStatus(newStatus: 'active' | 'disconnected' | 'probing') {
    this.status = newStatus;
    const config: ServerConfig = {
      ip: this.serverIp || 'unknown',
      port: this.serverPort,
      status: this.status
    };
    this.statusListeners.forEach(l => l(config));
    // When the server becomes reachable again, flush any logs queued during outage
    if (newStatus === 'active') {
      this.flushPendingDispatch();
    }
  }

  private async probeIp(ip: string, port: number): Promise<boolean> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2000); // 2s timeout

    try {
      const response = await fetch(`http://${ip}:${port}/api/config`, {
        signal: controller.signal,
        headers: { 'Accept': 'application/json' }
      });
      clearTimeout(timeout);
      if (response.ok) {
        const data = await response.json();
        return data.status === 'active';
      }
    } catch (e) {
      // Silent fail
    } finally {
      clearTimeout(timeout);
    }
    return false;
  }

  /**
   * Autonomous Subnet Discovery
   * Probes common local subnets in parallel batches.
   */
  public async discoverServer() {
    if (this.status === 'probing') return;
    this.updateStatus('probing');

    const subnets = ['192.168.1', '192.168.0', '10.0.0', '172.16.0'];
    const ports = [5000, 5001];

    for (const subnet of subnets) {
      // Probe in batches of 20 to avoid overwhelming network stack
      for (let i = 1; i < 255; i += 20) {
        const batch = [];
        for (let j = i; j < i + 20 && j < 255; j++) {
          const ip = `${subnet}.${j}`;
          for (const port of ports) {
            batch.push((async () => {
              const found = await this.probeIp(ip, port);
              if (found) {
                this.setServerUrl(ip, port);
                return true;
              }
              return false;
            })());
          }
        }
        const results = await Promise.all(batch);
        if (results.some(r => r)) {
          this.updateStatus('active');
          return;
        }
      }
    }
    this.updateStatus('disconnected');
  }

  public log(level: LogLevel, message: string, stack?: string, tag: string = 'App') {
    const entry: LogEntry = {
      id: Math.random().toString(36).substring(7),
      level,
      message,
      stack,
      timestamp: new Date().toISOString(),
      tag,
      deviceId: this.deviceId
    };

    this.logs.unshift(entry);
    if (this.logs.length > this.maxLogs) this.logs.pop();
    
    this.listeners.forEach(l => l(entry));

    // Non-blocking background dispatch
    if (this.serverIp && this.status === 'active') {
      this.dispatchLog(entry);
    } else if (this.serverIp && this.status === 'disconnected') {
        // Try to reconnect once on log if disconnected
        this.testConnection().then(active => {
            if (active) this.dispatchLog(entry);
            else this.pendingDispatch.push(entry); // re-queue for retry on next reconnect
        });
    }
  }

  private async dispatchLog(entry: LogEntry) {
    if (!this.serverIp) return;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2000);

    try {
      const response = await fetch(`http://${this.serverIp}:${this.serverPort}/api/log`, {
        method: 'POST',
        signal: controller.signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(entry)
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (e) {
      // Re-queue the entry for retry on next reconnect — never silently drop
      if (this.status === 'active') this.updateStatus('disconnected');
      this.pendingDispatch.push(entry);
    } finally {
      clearTimeout(timeout);
    }
  }

  /** Flushes any logs that were queued while the server was unreachable. */
  private async flushPendingDispatch() {
    if (this.pendingDispatch.length === 0) return;
    const queue = [...this.pendingDispatch];
    this.pendingDispatch = [];
    for (const entry of queue) {
      await this.dispatchLog(entry);
    }
  }

  // Convenience methods
  public d(msg: string, tag?: string) { this.log('DEBUG', msg, undefined, tag); }
  public i(msg: string, tag?: string) { this.log('INFO', msg, undefined, tag); }
  public w(msg: string, tag?: string) { this.log('WARN', msg, undefined, tag); }
  public e(msg: string, error?: any, tag?: string) { 
    const stack = error instanceof Error ? error.stack : (typeof error === 'string' ? error : JSON.stringify(error));
    this.log('ERROR', msg, stack, tag); 
  }

  public onLog(callback: (entry: LogEntry) => void) {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  public onStatusChange(callback: (config: ServerConfig) => void) {
    this.statusListeners.add(callback);
    callback({ ip: this.serverIp || 'unknown', port: this.serverPort, status: this.status });
    return () => this.statusListeners.delete(callback);
  }

  public getLogs() { return [...this.logs]; }
  public getStatus() { return this.status; }
  public getConfig() { return { ip: this.serverIp, port: this.serverPort }; }
}

export const logger = TelemetryLogger.getInstance();
