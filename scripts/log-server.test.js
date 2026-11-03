/**
 * Node.js Unit & Integration Tests for MosqueDND Live Debug Telemetry Server
 * Run with: node --test scripts/log-server.test.js
 */

const test = require('node:test');
const assert = require('node:assert');
const http = require('http');
const fs = require('fs');
const path = require('path');

// Test schema validation directly or via mock requests
const SCHEMA_PATH = path.join(__dirname, '../app/src/main/assets/schemas/telemetry-envelope.json');

test('Telemetry JSON Schema file exists and is valid JSON', () => {
    assert.strictEqual(fs.existsSync(SCHEMA_PATH), true);
    const content = fs.readFileSync(SCHEMA_PATH, 'utf8');
    const schema = JSON.parse(content);
    assert.strictEqual(schema.title, 'MosqueDND Telemetry Envelope');
    assert.ok(schema.required.includes('schemaVersion'));
    assert.ok(schema.required.includes('sessionId'));
    assert.ok(schema.required.includes('deviceId'));
    assert.ok(schema.required.includes('sequenceNumber'));
});

test('Telemetry Envelope Validation Function Test', () => {
    // Helper validator matching server logic
    function validate(data) {
        if (!data || typeof data !== 'object') return false;
        const required = ['schemaVersion', 'sessionId', 'deviceId', 'sequenceNumber', 'monotonicTimestamp', 'wallClockTimestamp', 'messageType', 'payload'];
        for (const field of required) {
            if (data[field] === undefined || data[field] === null) return false;
        }
        if (!data.payload || !data.payload.level || !data.payload.message) return false;
        return true;
    }

    const validEnvelope = {
        schemaVersion: "1.0.0",
        sessionId: "session-123",
        deviceId: "device-test",
        sequenceNumber: 1,
        monotonicTimestamp: 12345.0,
        wallClockTimestamp: new Date().toISOString(),
        messageType: "log",
        payload: {
            level: "INFO",
            message: "Test log message",
            tag: "Test"
        }
    };

    assert.strictEqual(validate(validEnvelope), true);

    const invalidEnvelope = {
        schemaVersion: "1.0.0",
        // missing sessionId
        deviceId: "device-test",
        sequenceNumber: 1,
        monotonicTimestamp: 12345.0,
        wallClockTimestamp: new Date().toISOString(),
        messageType: "log",
        payload: { level: "INFO", message: "fail" }
    };

    assert.strictEqual(validate(invalidEnvelope), false);
});
