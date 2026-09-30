# MosqueDND

> Android companion app for Muslims: automatic DND during prayer, accurate prayer times, Qibla compass, and mosque proximity automation.

---

## Features

| Feature | Description |
|---|---|
| **Prayer Times** | Daily Fajr, Dhuhr, Asr, Maghrib, Isha times calculated by location with MWL/Makkah/Karachi methods and Shafi'/Hanafi Asr |
| **Qibla Compass** | Sensor-driven compass view showing Qibla bearing from current location to Kaaba with calibration feedback |
| **Prayer DND** | Automatic Do Not Disturb activation during prayer windows, with per-prayer toggle, offset control, and a 15-minute auto-disable timer |
| **Geofence Monitoring** | Background location monitoring + geofencing that triggers DND when near a saved mosque |
| **Adhan Alarms** | Exact prayer-time alarms with custom Adhan playback, and per-prayer alarm on/off switches |
| **Widget** | 30-minute updated home-screen widget showing next prayer time and countdown |
| **Import/Export** | Full settings + mosque data export via Gson + SAF, with versioned schema and merge-on-import |
| **Theme** | Light/dark/system theme switching with Material3 DayNight, Islamic gold accent colors |

---

## Tech Stack

- **Language**: Kotlin (minSdk 26, targetSdk 34, compileSdk 34)
- **Build**: Gradle 8.2 with KGP 1.9.22, Java 17
- **Maps**: MapLibre Android SDK 10.3.7 (OpenFreeMap tiles, no API key required)
- **Location**: Google Play Services Location 21.0.1 + FusedLocationProvider
- **Background**: WorkManager 2.9.0 (CoroutineWorker) + AlarmManager exact alarms
- **Serialization**: Gson 2.10.1 (type-safe via `TypeToken.getParameterized()`)
- **UI**: XML layouts + ViewBinding + Material3 + partial Jetpack Compose
- **Tests**: JUnit 4 + Mockito + Robolectric (JVM) + Espresso (instrumentation)

---

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 34

### Build
```bash
./gradlew clean assembleDebug
```

Signing configs live in `app/keystore.properties` and `app/keystore/release.keystore`. Both are gitignored.

### Release
```bash
./gradlew clean assembleRelease
```

---

## Architecture

```
app/src/main/java/com/mosquednd/
├── data/             # ExportData, ImportExportManager (SAF + Gson)
├── detector/         # (removed — empty, was MuslimAppDetector)
├── geofence/         # GeofenceConfig, GeofenceFilter
├── receiver/         # BootReceiver, GeofenceBroadcastReceiver, SilenceAlarmReceiver
├── service/          # AlarmReceiver, LocationMonitorService, GeofenceService, DNDTimer
├── sound/            # AdhanPlayer (MediaPlayer wrapper)
├── theme/            # AppTheme singleton (SharedPreferences-backed theme switching)
├── ui/
│   ├── MainActivity.kt
│   ├── MapActivity.kt
│   ├── onboarding/
│   └── settings/
├── util/             # DNDController, IslamicCalendar, LocationProvider, OffsetFormatter, PrayTimesCalculator
└── widget/           # PrayerTimesWidget (AppWidgetProvider)
```

**Key decisions are tracked in `DECISIONS.md`** — every significant architectural or behavior change is logged there with rationale and impact.

---

## Decision Log

All design decisions, bug fixes, and refactors are documented in `DECISIONS.md` with timestamp, rationale, and file-level impact. Read it before making changes — it documents past traps (R8 generic-signature stripping, deprecated JobIntentService migration, API 26–32 `getParcelableExtra` overflow, etc.).
