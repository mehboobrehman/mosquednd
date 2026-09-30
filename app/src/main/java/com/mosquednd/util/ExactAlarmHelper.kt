package com.mosquednd.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.mosquednd.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ExactAlarmHelper {

    private const val TAG = "ExactAlarmHelper"

    /**
     * Checks SCHEDULE_EXACT_ALARM permission (API 31+) and shows a settings
     * dialog if the permission is not granted. Delegates to ExactAlarmHelper
     * to avoid duplication across fragments.
     *
     * @return true if exact alarms are already granted or the device is below API 31,
     *         false if the permission is missing and a dialog was shown.
     */
    fun checkExactAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted — showing settings dialog")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.exact_alarm_permission_title)
                    .setMessage(R.string.exact_alarm_permission_message)
                    .setPositiveButton(R.string.exact_alarm_open_settings) { _, _ ->
                        context.startActivity(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                return false
            }
            Log.d(TAG, "SCHEDULE_EXACT_ALARM already granted")
        }
        return true
    }
}
