package com.mosquednd.whatsnew

object ReleaseNoteRepository {
    
    fun getReleaseNotes(): List<ReleaseNote> {
        return listOf(
            ReleaseNote(
                versionName = "v1.0.7",
                versionCode = 13, // Assuming a new version code for 1.0.7
                releaseDate = "October 2026", // Keeping date consistent, or update if needed
                summary = "Enhanced location accuracy, improved DND scheduling, and refined UI.",
                items = listOf(
                    WhatsNewItem(
                        ReleaseNoteCategory.IMPROVEMENTS,
                        "Location Accuracy",
                        "Improved GPS and network location services for more precise mosque detection."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.CHANGES,
                        "DND Scheduling Logic",
                        "Refined Do Not Disturb scheduling to better align with prayer alert times and user offsets."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.CHANGES,
                        "UI Refinements",
                        "Minor adjustments to the user interface for a smoother and more intuitive experience."
                    ),
                     WhatsNewItem(
                        ReleaseNoteCategory.BUG_FIXES,
                        "Sticky Notifications",
                        "Addressed an issue with persistent notifications that were not clearing correctly."
                    )
                )
            ),
            ReleaseNote(
                versionName = "v1.0.3",
                versionCode = 12,
                releaseDate = "October 2026",
                summary = "Qibla Compass integration, Mosque Caching, and offline support.",
                items = listOf(
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Qibla Compass Integration",
                        "Added a beautiful, modular Qibla compass with smooth sensor filtering and motion gate logic."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Nearby Mosque Caching",
                        "Implemented caching for nearby mosques to improve performance and reduce network calls."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Place Detector Offline Support",
                        "Enhanced place detection to work better in low-connectivity environments."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.CHANGES,
                        "Updated Settings UI",
                        "Redesigned Settings with inline help dialogs and better categorization for prayer calculations."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.BUG_FIXES,
                        "Qibla Sensor Drift",
                        "Fixed systematic bias (~80°) in Qibla heading using getRotationMatrix pipeline."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.BUG_FIXES,
                        "Geofence Service",
                        "Fixed background location check logic when restarting the geofence service."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.IMPROVEMENTS,
                        "Prayer Time Performance",
                        "Optimized prayer time calculation logic for faster startup and updates."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.IMPROVEMENTS,
                        "Battery Optimization Tips",
                        "Added helpful tips in settings to ensure the app works reliably in the background."
                    )
                )
            ),
            ReleaseNote(
                versionName = "v1.0.2",
                versionCode = 11,
                releaseDate = "September 2026",
                summary = "Custom Adhan ringtones and Hijri calendar updates.",
                items = listOf(
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Custom Adhan Ringtones",
                        "Pick your favorite Adhan sound from local storage for prayer alerts."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Master Alarm Toggle",
                        "Easily enable or disable all prayer alarms with a single switch."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.CHANGES,
                        "Hijri Calendar Display",
                        "Updated Hijri date display on the toolbar with support for manual offset adjustment."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.BUG_FIXES,
                        "Reboot Alarm Recovery",
                        "Fixed a bug where alarms would not reschedule correctly after a device reboot."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.BUG_FIXES,
                        "Widget Sync",
                        "Resolved an issue where the prayer time widget would occasionally stop updating."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.IMPROVEMENTS,
                        "Power Efficiency",
                        "Reduced background service power consumption by 15% through smarter location polling."
                    )
                )
            ),
            ReleaseNote(
                versionName = "v1.0.1",
                versionCode = 10,
                releaseDate = "August 2026",
                summary = "Interactive Map and Data Security enhancements.",
                items = listOf(
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Interactive Mosque Map",
                        "Visualize nearby mosques and your geofence boundaries on an interactive map."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.CHANGES,
                        "Encrypted Storage",
                        "Migrated user preferences and sensitive data to encrypted storage for better security."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.BUG_FIXES,
                        "Silent Mode Restoration",
                        "Fixed an issue where Silent Mode was not always restored after leaving a mosque."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.IMPROVEMENTS,
                        "Backup & Restore",
                        "Added functionality to export and import app settings for easy migration."
                    )
                )
            ),
            ReleaseNote(
                versionName = "v1.0.0",
                versionCode = 1,
                releaseDate = "July 2026",
                summary = "Initial release of MosqueDND.",
                items = listOf(
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Automatic DND",
                        "Automatically enable Do Not Disturb mode when you are inside a mosque."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.WHATS_NEW,
                        "Prayer Time Alerts",
                        "Receive accurate prayer time notifications based on your location."
                    ),
                    WhatsNewItem(
                        ReleaseNoteCategory.IMPROVEMENTS,
                        "Geofence Controls",
                        "Fine-tune the detection radius for mosque geofences in settings."
                    )
                )
            )
        )
    }

    fun getLatestReleaseNote(): ReleaseNote? {
        return getReleaseNotes().firstOrNull()
    }

    fun getReleaseNoteForVersion(versionName: String): ReleaseNote? {
        return getReleaseNotes().find { it.versionName == versionName }
    }
}
