package com.mosquednd.util

import android.content.Context
import android.util.Log

/**
 * Release stub for AgentLogger. Zero telemetry, zero logging, zero network traffic in release builds.
 */
object AgentLogger {
    private const val TAG = "AgentLogger"

    fun init(context: Context, defaultServerIp: String? = null) {}
    fun setServerIp(context: Context, ip: String) {}
    fun setServerIpFromUser(url: String) {}
    fun getServerIp(context: Context): String? = null
    fun clearServerIp(context: Context) {}
    fun d(message: String, tag: String = TAG) {}
    fun i(message: String, tag: String = TAG) {}
    fun w(message: String, tag: String = TAG) {}
    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {}
    fun logToAgent(level: String, message: String, stack: String?) {}
}
