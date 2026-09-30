package com.mosquednd.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.mosquednd.R
import com.mosquednd.util.DNDController
import com.mosquednd.util.PendingIntentConstants

class DNDTimerExpiryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DNDTimerExpiryReceiver"
        const val ACTION_DND_TIMER_EXPIRED = "com.mosquednd.action.DND_TIMER_EXPIRED"

        fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_DND_TIMER_EXPIRED).apply {
                setClass(context, DNDTimerExpiryReceiver::class.java)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, PendingIntentConstants.DND_TIMER_EXPIRY, intent, flags)
        }

        fun cancelPendingIntent(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(getPendingIntent(context))
            Log.d(TAG, "AlarmManager expiry cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_DND_TIMER_EXPIRED != intent.action) {
            Log.w(TAG, "Unexpected action: ${intent.action}")
            return
        }

        Log.d(TAG, "DND timer expiry received — disabling DND")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                DNDController.disableDND(context)
            }
            Toast.makeText(context, R.string.dnd_auto_disabled, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable DND on timer expiry", e)
        }
    }
}
