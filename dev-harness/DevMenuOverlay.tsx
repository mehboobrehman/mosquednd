/**
 * dev-harness/DevMenuOverlay.tsx
 * 
 * Floating Development Overlay for MosqueDND.
 * Provides interactive control over telemetry configuration, 
 * subnet discovery, and real-time log inspection.
 */

import React, { useState, useEffect, useCallback } from 'react';
// @ts-ignore
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput, SafeAreaView, Modal, Platform, ActivityIndicator } from 'react-native';
import { logger, LogEntry, ServerConfig } from './logger';

export const DevMenuOverlay: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [status, setStatus] = useState<ServerConfig>({ ip: 'unknown', port: 5000, status: 'disconnected' });
  const [manualIp, setManualIp] = useState('');
  const [manualPort, setManualPort] = useState('5000');

  useEffect(() => {
    // Synchronize initial logs
    setLogs(logger.getLogs());

    // Subscribe to new logs and status changes
    const unsubscribeLog = logger.onLog((entry) => {
      setLogs(prev => [entry, ...prev].slice(0, 50));
    });

    const unsubscribeStatus = logger.onStatusChange((config) => {
      setStatus(config);
      setManualIp(config.ip);
      setManualPort(config.port.toString());
    });

    return () => {
      unsubscribeLog();
      unsubscribeStatus();
    };
  }, []);

  const handleDiscovery = useCallback(async () => {
    await logger.discoverServer();
  }, []);

  const handleSaveManual = useCallback(() => {
    const port = parseInt(manualPort, 10) || 5000;
    logger.setServerUrl(manualIp, port);
  }, [manualIp, manualPort]);

  const renderLogLevel = (level: string) => {
    let color = '#32cd32'; // INFO - Green
    if (level === 'WARN') color = '#ffd700'; // Yellow
    if (level === 'ERROR' || level === 'FATAL') color = '#ff4500'; // Red
    if (level === 'DEBUG') color = '#00ced1'; // Cyan

    return <Text style={[styles.logLevel, { color }]}>[{level}]</Text>;
  };

  if (!isOpen) {
    return (
      <TouchableOpacity 
        style={styles.floatingPill} 
        onPress={() => setIsOpen(true)}
        activeOpacity={0.8}
      >
        <View style={[styles.statusDot, { backgroundColor: status.status === 'active' ? '#4caf50' : (status.status === 'probing' ? '#ff9800' : '#f44336') }]} />
        <Text style={styles.pillText}>DEV</Text>
      </TouchableOpacity>
    );
  }

  return (
    <Modal visible={isOpen} animationType="slide" transparent={false}>
      <SafeAreaView style={styles.modalContainer}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>MosqueDND Telemetry Harness</Text>
          <TouchableOpacity onPress={() => setIsOpen(false)} style={styles.closeButton}>
            <Text style={styles.closeButtonText}>✕</Text>
          </TouchableOpacity>
        </View>

        <ScrollView style={styles.scrollContent}>
          {/* Status Section */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Connection Status</Text>
            <View style={styles.statusRow}>
               <View style={[styles.statusBadge, { backgroundColor: status.status === 'active' ? '#4caf50' : (status.status === 'probing' ? '#ff9800' : '#f44336') }]}>
                  <Text style={styles.statusBadgeText}>{status.status.toUpperCase()}</Text>
               </View>
               <Text style={styles.statusAddress}>{status.ip}:{status.port}</Text>
            </View>
            
            <View style={styles.buttonGroup}>
              <TouchableOpacity style={styles.actionButton} onPress={handleDiscovery} disabled={status.status === 'probing'}>
                {status.status === 'probing' ? <ActivityIndicator size="small" color="#fff" /> : <Text style={styles.actionButtonText}>Auto-Scan Subnet</Text>}
              </TouchableOpacity>
              <TouchableOpacity style={[styles.actionButton, { backgroundColor: '#5c6bc0' }]} onPress={() => logger.testConnection()}>
                <Text style={styles.actionButtonText}>Test Link</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Manual Config Section */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Manual Server IP Override</Text>
            <View style={styles.inputRow}>
              <TextInput 
                style={[styles.input, { flex: 3 }]} 
                placeholder="IP (e.g. 192.168.1.50)" 
                value={manualIp}
                onChangeText={setManualIp}
                keyboardType="numeric"
              />
              <TextInput 
                style={[styles.input, { flex: 1, marginLeft: 10 }]} 
                placeholder="Port" 
                value={manualPort}
                onChangeText={setManualPort}
                keyboardType="numeric"
              />
            </View>
            <TouchableOpacity style={[styles.actionButton, { marginTop: 10, backgroundColor: '#263238' }]} onPress={handleSaveManual}>
              <Text style={styles.actionButtonText}>Apply Configuration</Text>
            </TouchableOpacity>
          </View>

          {/* Log Stream Section */}
          <View style={[styles.section, { flex: 1 }]}>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>Real-time Log Stream</Text>
              <TouchableOpacity onPress={() => setLogs([])}>
                <Text style={styles.clearText}>Clear</Text>
              </TouchableOpacity>
            </View>
            <View style={styles.logContainer}>
              {logs.length === 0 ? (
                <Text style={styles.emptyLogs}>No logs recorded yet.</Text>
              ) : (
                logs.map(log => (
                  <View key={log.id} style={styles.logEntry}>
                    <Text style={styles.logTimestamp}>{log.timestamp.split('T')[1].split('Z')[0]}</Text>
                    {renderLogLevel(log.level)}
                    <Text style={styles.logTag}>[{log.tag}]</Text>
                    <Text style={styles.logMessage}>{log.message}</Text>
                    {log.stack ? <Text style={styles.logStack}>{log.stack.substring(0, 200)}...</Text> : null}
                  </View>
                ))
              )}
            </View>
          </View>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  floatingPill: {
    position: 'absolute',
    bottom: 40,
    right: 20,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 8,
    flexDirection: 'row',
    alignItems: 'center',
    zIndex: 9999,
    ...Platform.select({
      ios: { shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.3, shadowRadius: 4 },
      android: { elevation: 5 }
    })
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6
  },
  pillText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold'
  },
  modalContainer: {
    flex: 1,
    backgroundColor: '#f5f5f5'
  },
  header: {
    height: 60,
    backgroundColor: '#212121',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 15
  },
  headerTitle: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold'
  },
  closeButton: {
    padding: 10
  },
  closeButtonText: {
    color: '#fff',
    fontSize: 20
  },
  scrollContent: {
    flex: 1,
    padding: 15
  },
  section: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 15,
    marginBottom: 15,
    ...Platform.select({
      ios: { shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.1, shadowRadius: 2 },
      android: { elevation: 2 }
    })
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#333',
    marginBottom: 10
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 15
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
    marginRight: 10
  },
  statusBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: 'bold'
  },
  statusAddress: {
    fontSize: 14,
    color: '#666',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace'
  },
  buttonGroup: {
    flexDirection: 'row',
    justifyContent: 'space-between'
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#2196f3',
    paddingVertical: 12,
    borderRadius: 6,
    alignItems: 'center',
    marginHorizontal: 4
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '600'
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center'
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: '#333',
    backgroundColor: '#fafafa'
  },
  clearText: {
    color: '#2196f3',
    fontSize: 12,
    fontWeight: '600'
  },
  logContainer: {
    backgroundColor: '#1e1e1e',
    borderRadius: 8,
    padding: 10,
    minHeight: 300
  },
  emptyLogs: {
    color: '#666',
    textAlign: 'center',
    marginTop: 20
  },
  logEntry: {
    marginBottom: 8,
    borderBottomWidth: 0.5,
    borderBottomColor: '#333',
    paddingBottom: 4
  },
  logTimestamp: {
    color: '#888',
    fontSize: 10,
    fontFamily: 'monospace'
  },
  logLevel: {
    fontSize: 10,
    fontWeight: 'bold',
    marginRight: 4
  },
  logTag: {
    color: '#aaa',
    fontSize: 10,
    fontWeight: 'bold'
  },
  logMessage: {
    color: '#ddd',
    fontSize: 12,
    marginTop: 2
  },
  logStack: {
    color: '#666',
    fontSize: 10,
    fontStyle: 'italic',
    marginTop: 2
  }
});
