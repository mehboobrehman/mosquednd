package com.mosquednd.geofence

/**
 * Centralized configuration for geofence edge-case handling.
 * All parameters have sensible defaults and can be overridden at runtime.
 * This is a pure Kotlin data class with no Android framework dependencies.
 */
data class GeofenceConfig(
    // ===== Boundary Precision =====

    /** Epsilon for all float boundary comparisons (prevents oscillation near fence edge) */
    val toleranceBufferMeters: Float = 3f,

    /** Milliseconds location must stay inside before ENTER transition fires */
    val enterDwellTimeMs: Long = 3000L,

    /** Milliseconds location must stay outside before EXIT transition fires */
    val exitDwellTimeMs: Long = 5000L,

    // ===== GPS Noise & Jitter =====

    /** Maximum horizontal accuracy in meters; locations with worse accuracy are rejected */
    val gpsAccuracyThresholdMeters: Float = 100f,

    /** Moving average window size for the GPS noise filter */
    val noiseFilterWindowSize: Int = 3,

    /** Minimum number of consecutive samples that must agree (out of the window) before a transition fires */
    val minConsecutiveSamples: Int = 2,

    // ===== Deduplication =====

    /** Time window in milliseconds for deduplicating identical ENTER/EXIT events for the same fence */
    val dedupWindowMs: Long = 2000L,

    // ===== Performance =====

    /** If > 0, overrides the OS geofence limit. 0 means use platform default (typically 100). */
    val geofenceLimitOverride: Int = 0,

    /** Number of active fences above which spatial index acceleration is enabled */
    val spatialIndexThreshold: Int = 20,

    // ===== Foreground vs Background =====

    /** Location update interval in milliseconds when the app is in the foreground */
    val foregroundUpdateIntervalMs: Long = 15000L,

    /** Location update interval in milliseconds when the app is in the background */
    val backgroundUpdateIntervalMs: Long = 60000L,

    // ===== Health Monitor =====

    /** Initial backoff delay in milliseconds for geofence re-registration retries */
    val backoffBaseMs: Long = 30000L,

    /** Maximum backoff delay in milliseconds (cap for exponential backoff growth) */
    val backoffMaxMs: Long = 3600000L,

    /** Maximum number of consecutive backoff attempts before giving up entirely */
    val maxBackoffAttempts: Int = 10
) {
    companion object {
        /** Default detection radius in meters for mosque proximity geofences */
        const val DEFAULT_DETECTION_RADIUS_M = 100
    }
}
