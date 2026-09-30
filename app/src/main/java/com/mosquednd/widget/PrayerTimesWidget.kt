package com.mosquednd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.prayer.AsrMethod
import com.mosquednd.prayer.CalculationMethod
import com.mosquednd.prayer.PrayTimesCalculator
import com.mosquednd.prayer.PrayerType
import com.mosquednd.ui.MainActivity
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.PendingIntentConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrayerTimesWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "PrayerTimesWidget"
        private const val PREF_CALCULATION_METHOD = "calculation_method"
        private const val PREF_ASR_METHOD = "asr_method"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    internal fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)

        try {
            val (lat, lng) = LocationProvider.getLastKnownLocation()

            val prefs = MosqueDNDApp.prefs
            val calcMethodStr = prefs.getString(PREF_CALCULATION_METHOD, "mwl") ?: "mwl"
            val asrMethodStr = prefs.getString(PREF_ASR_METHOD, "standard") ?: "standard"

            val calcMethod = CalculationMethod.fromPreferenceValue(calcMethodStr)
            val asrMethod = AsrMethod.fromPreferenceValue(asrMethodStr)

            Log.d(TAG, "Updating widget with method=$calcMethod, asr=$asrMethod")

            val times = PrayTimesCalculator.calculate(
                date = Date(),
                latitude = lat,
                longitude = lng,
                method = calcMethod,
                asrMethod = asrMethod
            )

            val now = System.currentTimeMillis()
            val nextPrayer = times.values
                .filter { it.type != PrayerType.SUNRISE }
                .minByOrNull { prayerTime ->
                    val diff = prayerTime.time.timeInMillis - now
                    if (diff > 0) diff else Long.MAX_VALUE
                }

            val prayerName = nextPrayer?.type?.displayName ?: "--"
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val prayerTimeStr = if (nextPrayer != null) {
                timeFormat.format(nextPrayer.time.time)
            } else {
                "--:--"
            }

            views.setTextViewText(R.id.text_prayer_name, prayerName)
            views.setTextViewText(R.id.text_prayer_time, prayerTimeStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget", e)
            views.setTextViewText(R.id.text_prayer_name, "--")
            views.setTextViewText(R.id.text_prayer_time, "--:--")
        }

        // Tap action — open main activity and navigate to prayer times
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            PendingIntentConstants.NOTIFICATION_WIDGET_TAP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
