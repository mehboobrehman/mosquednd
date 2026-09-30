# === Gson / TypeToken — preserve generic signatures for deserialization ===
# R8 strips generic type signatures by default, which breaks Gson's TypeToken
# (e.g. TypeToken<List<Mosque>>() {}) used in MosqueRepository.getAllMosques().
-keepattributes Signature
-keepattributes *Annotation*

# === Data Models (Gson serialization — keep all fields and constructors) ===
-keep class com.mosquednd.data.Mosque { *; }

# === App Components (AndroidManifest-registered + reflection safety) ===
# Application
-keep class com.mosquednd.MosqueDNDApp { *; }

# Activities
-keep class com.mosquednd.ui.MainActivity { *; }
-keep class com.mosquednd.ui.MapActivity { *; }

# Fragments (created programmatically)
-keep class com.mosquednd.ui.prayer.PrayerTimesFragment { *; }
-keep class com.mosquednd.ui.settings.SettingsFragment { *; }
-keep class com.mosquednd.ui.ComposeQiblaFragment { *; }

# Services (manifest-registered)
-keep class com.mosquednd.service.LocationMonitorService { *; }

# Receivers (manifest-registered)
-keep class com.mosquednd.receiver.GeofenceBroadcastReceiver { *; }
-keep class com.mosquednd.receiver.BootReceiver { *; }
-keep class com.mosquednd.service.AlarmReceiver { *; }
-keep class com.mosquednd.receiver.SilenceAlarmReceiver { *; }
-keep class com.mosquednd.receiver.DNDTimerExpiryReceiver { *; }
-keep class com.mosquednd.receiver.DNDAlarmReceiver { *; }
-keep class com.mosquednd.receiver.MidnightAlarmReceiver { *; }

# Widget (manifest-registered AppWidgetProvider)
-keep class com.mosquednd.widget.PrayerTimesWidget { *; }

# WorkManager Worker (instantiated via reflection — CoroutineWorker)
-keep class com.mosquednd.service.GeofenceService { *; }

# === Google Play Services ===
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# === Kotlin Parcelize (@Parcelize generates CREATOR accessed via reflection) ===
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# === Kotlin Coroutines (keep internal dispatchers and exception handlers) ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
