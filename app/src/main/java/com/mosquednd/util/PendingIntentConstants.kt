package com.mosquednd.util

/**
 * Centralized PendingIntent request codes to prevent collisions between
 * different alarm and notification PendingIntents.
 *
 * Namespace layout (hex, non-overlapping ranges):
 * - 0x1000–0x10FF : Prayer alarms (one per PrayerType ordinal)
 * - 0x2000–0x20FF : DND-start alarms (one per PrayerType ordinal)
 * - 0x2100–0x21FF : DND-end alarms (one per PrayerType ordinal)
 * - 0x3000–0x30FF : DND auto-disable timer expiry
 * - 0x4000–0x40FF : Notification content intents (tap-to-open app)
 * - 0x5000–0x50FF : Notification action intents (silence, dismiss)
 * - 0x6000–0x60FF : Midnight recalculation alarm
 * - 0x7000–0x70FF : Geofence PendingIntent (used by GeofenceService)
 */
object PendingIntentConstants {

    // ── Prayer alarms ──────────────────────────────────
    /** Base request code for prayer alarms. Each prayer type uses base + PrayerType.ordinal. */
    const val PRAYER_ALARM_BASE = 0x1000

    // ── DND alarms ─────────────────────────────────────
    /** Base request code for DND-start alarms. Each prayer type uses base + PrayerType.ordinal. */
    const val DND_ALARM_START_BASE = 0x2000
    /** Base request code for DND-end alarms. Each prayer type uses base + PrayerType.ordinal. */
    const val DND_ALARM_END_BASE = 0x2100

    // ── DND auto-disable timer ─────────────────────────
    /** Request code for the DND auto-disable timer expiry PendingIntent. */
    const val DND_TIMER_EXPIRY = 0x3001

    // ── Notification content intents (tap-to-open) ─────
    /** Request code for foreground service notification tap-to-open intent. */
    const val NOTIFICATION_SERVICE_TAP = 0x4000
    /** Request code for prayer times widget tap-to-open intent. */
    const val NOTIFICATION_WIDGET_TAP = 0x4001
    /** Request code for prayer times full-screen intent. */
    const val NOTIFICATION_FULL_SCREEN = 0x4002

    // ── Notification action intents ────────────────────
    /** Request code for the notification delete intent (dismiss → stops adhan). */
    const val NOTIFICATION_ACTION_DISMISS = 0x5001
    /** Request code for the notification content intent (tap → silence + open app). */
    const val NOTIFICATION_ACTION_SILENCE = 0x5002

    // ── Midnight recalculation ─────────────────────────
    /** Request code for the daily midnight recalculation alarm. */
    const val MIDNIGHT_RECALCULATION = 0x6001

    // ── Geofence ───────────────────────────────────────
    /** Request code for geofence PendingIntent (used by GeofenceService). */
    const val GEOFENCE = 0x7001
}
