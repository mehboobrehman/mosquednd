package com.mosquednd.util

import android.content.Context
import android.util.Log

/**
 * Debug implementation of AgentLogger with telemetry logging support.
 */
object AgentLogger {
    private const val TAG = "AgentLogger"

    fun init(context: Context, defaultServerIp: String? = null) {
        Log.d(TAG, "AgentLogger initialized with defaultServerIp: $defaultServerIp")
    }

    fun setServerIp(context: Context, ip: String) {
        Log.d(TAG, "Server IP set: $ip")
    }

    fun setServerIpFromUser(url: String) {
        Log.d(TAG, "Server IP set from user: $url")
    }

    fun getServerIp(context: Context): String? {
        return "10.0.2.2"
    }

    fun clearServerIp(context: Context) {
        Log.d(TAG, "Server IP cleared")
    }

    fun d(message: String, tag: String = TAG) {
        Log.d(tag, message)
    }

    fun i(message: String, tag: String = TAG) {
        Log.i(tag, message)
    }

    fun w(message: String, tag: String = TAG) {
        Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        Log.e(tag, message, throwable)
    }

    fun logToAgent(level: String, message: String, stack: String?) {
        Log.d(TAG, "[$level] $message ${stack ?: ""}")
    }
}
