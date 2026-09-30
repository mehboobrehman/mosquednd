package com.mosquednd.ui.prayer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosquednd.service.AdhanService
import com.mosquednd.service.AlarmReceiver
import com.mosquednd.util.LogHelper

class AdhanAlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PRAYER_NAME = "prayer_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.logd("AdhanAlarmActivity", "onCreate")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_CREATE", mapOf("activity" to "AdhanAlarmActivity"))

        // Turn screen on and show when locked to bypass sleep and lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "ADHAN_ALARM_TRIGGERED", mapOf("prayer" to prayerName))

        setContent {
            AdhanAlarmScreen(
                prayerName = prayerName,
                onSilence = {
                    LogHelper.logd("AdhanAlarmActivity", "Silence clicked")
                    LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "Silence", "activity" to "AdhanAlarmActivity", "prayer" to prayerName))
                    AdhanService.stopAdhan(this)
                    AlarmReceiver.stopCurrentAdhan(this)
                    finish()
                },
                onDismiss = {
                    LogHelper.logd("AdhanAlarmActivity", "Dismiss clicked")
                    LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "Dismiss", "activity" to "AdhanAlarmActivity", "prayer" to prayerName))
                    AdhanService.stopAdhan(this)
                    AlarmReceiver.stopCurrentAdhan(this)
                    finish()
                }
            )
        }
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_RESUME", mapOf("activity" to "AdhanAlarmActivity"))
        super.onResume()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_PAUSE", mapOf("activity" to "AdhanAlarmActivity"))
        super.onPause()
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_DESTROY", mapOf("activity" to "AdhanAlarmActivity"))
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            LogHelper.logd("AdhanAlarmActivity", "Volume key pressed — silencing Adhan")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "VOLUME_KEY_SILENCE", mapOf("activity" to "AdhanAlarmActivity"))
            AdhanService.stopAdhan(this)
            AlarmReceiver.stopCurrentAdhan(this)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun AdhanAlarmScreen(
    prayerName: String,
    onSilence: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1E3A1E) // Elegant green Islamic-themed background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PRAYER TIME ALERT",
                color = Color(0xFFA3E4D7),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "It's time for",
                color = Color.White,
                fontSize = 28.sp,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = prayerName,
                color = Color(0xFF58D68D),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Please prepare for prayer",
                color = Color.LightGray,
                fontSize = 16.sp,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(64.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onSilence,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD35400) // Deep Orange
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Silence",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F618D) // Deep Blue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}