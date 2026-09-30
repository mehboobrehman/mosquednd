package com.mosquednd.ui.qibla

import androidx.compose.ui.geometry.Offset

/**
 * State contract for the Qibla Compass Dial component.
 *
 * @property userHeading Current continuous animated device heading in degrees.
 * @property qiblaBearing Great-circle bearing to Kaaba (0°..359.9°).
 * @property isAligned Heading within ±3° threshold of Qibla bearing.
 * @property accuracyStatus SensorManager.SENSOR_STATUS_* accuracy level.
 * @property isSensorSupported Whether rotation/magnetometer hardware exists.
 * @property isUsingFallback Active fallback to Accel+Mag mode.
 * @property pitchRollOffset Device level tilt deviation for bubble-level guide (x = roll, y = pitch).
 */
data class QiblaDialState(
    val userHeading: Float,
    val qiblaBearing: Float,
    val isAligned: Boolean,
    val accuracyStatus: Int,
    val isSensorSupported: Boolean,
    val isUsingFallback: Boolean,
    val pitchRollOffset: Offset = Offset.Zero
)
