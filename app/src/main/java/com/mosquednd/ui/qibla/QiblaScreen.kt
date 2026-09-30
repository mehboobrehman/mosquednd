package com.mosquednd.ui.qibla

import android.location.Location
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquednd.R
import com.mosquednd.ui.qibla.sensor.QiblaSensorManager
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.LogHelper
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// Islamic Color Palette
private val GoldAccent = Color(0xFFC8A84E)
private val EmeraldAccent = Color(0xFF1A5C3A)

fun calculateQiblaBearing(lat: Double, lng: Double): Float {
    val kaabaLat = Math.toRadians(21.4225)
    val kaabaLng = Math.toRadians(39.8262)
    val userLat = Math.toRadians(lat)
    val userLng = Math.toRadians(lng)
    val dLng = kaabaLng - userLng
    val y = Math.sin(dLng) * Math.cos(kaabaLat)
    val x = Math.cos(userLat) * Math.sin(kaabaLat) - Math.sin(userLat) * Math.cos(kaabaLat) * Math.cos(dLng)
    val bearing = Math.toDegrees(Math.atan2(y, x))
    return ((bearing + 360) % 360).toFloat()
}

fun getCardinalDirection(bearing: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((bearing + 22.5f) % 360) / 45f).toInt()
    return directions[index % 8]
}

@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val sensorManager = remember { QiblaSensorManager(context) }
    var location by remember { mutableStateOf(LocationProvider.getEffectiveLocation()) }

    // Heading accumulator state — declared before DisposableEffect so the lifecycle observer can capture them.
    var accumulatedHeading by remember { mutableFloatStateOf(0f) }
    var lastSensorHeading by remember { mutableFloatStateOf(0f) }
    var isFirstHeading by remember { mutableStateOf(true) }
    // Debounced alignment badge/haptic state — see the LaunchedEffect(isAligned) below.
    var alignedBannerVisible by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            LogHelper.logd("QiblaScreen", "Lifecycle event: $event")
            if (event == Lifecycle.Event.ON_RESUME) {
                LogHelper.logd("QiblaScreen", "ON_RESUME: Starting sensors")
                sensorManager.start()
                location = LocationProvider.getEffectiveLocation()
                // Reset heading state for clean start
                accumulatedHeading = 0f
                lastSensorHeading = 0f
                isFirstHeading = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                LogHelper.logd("QiblaScreen", "ON_PAUSE: Stopping sensors")
                sensorManager.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // If the lifecycle is already resumed when entering composition (e.g., ViewPager2 tab swipe), start sensors immediately
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            LogHelper.logd("QiblaScreen", "Already RESUMED on composition: Starting sensors")
            sensorManager.start()
            location = LocationProvider.getEffectiveLocation()
            accumulatedHeading = 0f
            lastSensorHeading = 0f
            isFirstHeading = true
        }
        
        onDispose {
            LogHelper.logd("QiblaScreen", "Disposing QiblaScreen: Removing observer and stopping sensors")
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.stop()
        }
    }

    // Retrieve user location falling back to Karachi
    val (lat, lng) = location
    val qiblaBearing = remember(lat, lng) { calculateQiblaBearing(lat, lng) }

    // Distance to Makkah in kilometers
    val distanceKm = remember(lat, lng) {
        val results = FloatArray(1)
        Location.distanceBetween(lat, lng, 21.4225, 39.8262, results)
        (results[0] / 1000).toInt()
    }

    // Sensor Manager status and readings with lifecycle-aware collection
    val sensorHeading by sensorManager.heading.collectAsStateWithLifecycle()
    val isSupported by sensorManager.isSupported.collectAsStateWithLifecycle()
    val accuracyStatus by sensorManager.accuracyStatus.collectAsStateWithLifecycle()
    val isUsingFallback by sensorManager.isUsingFallbackSensors.collectAsStateWithLifecycle()
    val fallbackCount by sensorManager.fallbackCount.collectAsStateWithLifecycle()
    // False until the first real sensor heading arrives (cold start) or the sensors are
    // restarted (warm re-entry). Until it flips, sensorHeading is the initial 0f or a stale
    // value from the previous session and must not be fed to the accumulator.
    val headingInitialized by sensorManager.headingInitialized.collectAsStateWithLifecycle()

    // Log scroll activity
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            LogHelper.logTelemetry(
                LogHelper.TelemetryCategory.GESTURE,
                "QIBLA_SCROLL_START",
                mapOf("value" to scrollState.value)
            )
        } else {
            LogHelper.logTelemetry(
                LogHelper.TelemetryCategory.GESTURE,
                "QIBLA_SCROLL_STOP",
                mapOf("value" to scrollState.value)
            )
        }
    }

    // Reset accumulated heading when sensor source changes (e.g. rotation vector → accel+mag fallback).
    // Without this, the shortest-path diff computes (newAccelMagHeading - oldStuckRotationVectorHeading)
    // producing a massive 100-200° jump that sends the dial spinning.
    LaunchedEffect(fallbackCount) {
        if (fallbackCount > 0) {
            val currentHeading = sensorManager.heading.value
            LogHelper.logd("QiblaScreen", "Sensor fallback detected (count=$fallbackCount). Resetting accumulated heading to ${String.format("%.1f", currentHeading)}")
            accumulatedHeading = currentHeading
            lastSensorHeading = currentHeading
            isFirstHeading = true
        }
    }

    LaunchedEffect(sensorHeading) {
        if (!headingInitialized) {
            // Not initialized yet: sensorHeading is either the initial 0f (cold start) or the
            // previous session's stale value (warm re-entry). Feeding either to the accumulator
            // would bake a synthetic 0.0° origin or a ghost jump into the dial.
            return@LaunchedEffect
        }
        if (sensorHeading.isNaN()) {
            LogHelper.logw("QiblaScreen", "Received NaN sensorHeading, ignoring.")
            return@LaunchedEffect
        }
        if (isFirstHeading) {
            accumulatedHeading = sensorHeading
            lastSensorHeading = sensorHeading
            isFirstHeading = false
        } else {
            var diff = (sensorHeading - lastSensorHeading) % 360f
            if (diff < -180f) {
                diff += 360f
            } else if (diff > 180f) {
                diff -= 360f
            }
            accumulatedHeading += diff
            lastSensorHeading = sensorHeading

            LogHelper.logThrottled(
                "qibla_heading_rotation",
                1000L,
                "DEBUG",
                "QiblaScreen",
                "[GESTURE] Event: QIBLA_HEADING_ROTATE | heading=${(accumulatedHeading % 360f + 360f) % 360f}"
            )
        }
    }

    // Smooth continuous heading animation
    val animatedHeading by animateFloatAsState(
        targetValue = accumulatedHeading,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "headingAnimation"
    )

    // Normalized display heading (0°..359°) for UI text
    val displayHeading = remember(animatedHeading) {
        (animatedHeading % 360f + 360f) % 360f
    }

    // Throttled diagnostic logging (every 1 second) — placed after displayHeading is defined
    LaunchedEffect(sensorHeading) {
        LogHelper.logThrottled(
            "qibla_diagnostic",
            1000L,
            "DEBUG",
            "QiblaScreen",
            "Diagnostic: sensorHeading=${String.format("%.1f", sensorHeading)}° accumulatedHeading=${String.format("%.1f", accumulatedHeading)}° displayHeading=${String.format("%.1f", displayHeading)}° isUsingFallback=$isUsingFallback"
        )
    }

    // Alignment within ±3° threshold
    val angleDiff = abs((displayHeading - qiblaBearing + 540f) % 360f - 180f)
    val isAligned = angleDiff <= 3f

    // Alignment badges/haptics are debounced: the fallback magnetometer path on low-end devices
    // wobbles the heading ±3° around the qibla bearing (observed 2-5 alignment-triggers/second
    // on the itel S685LN while the device sat still), which previously stormed the haptic,
    // the "Aligned with Qibla" banner, and the telemetry stream. Only act when the alignment
    // state holds for 800ms.
    LaunchedEffect(isAligned) {
        delay(800)
        val wasVisible = alignedBannerVisible
        alignedBannerVisible = isAligned
        if (isAligned && !wasVisible) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            LogHelper.logTelemetry(
                LogHelper.TelemetryCategory.GESTURE,
                "QIBLA_ALIGNMENT_ACHIEVED",
                mapOf("bearing" to qiblaBearing, "heading" to displayHeading)
            )
        }
    }

    val pitchRollOffset by sensorManager.pitchRollOffset.collectAsStateWithLifecycle()

    val dialState = QiblaDialState(
        userHeading = animatedHeading,
        qiblaBearing = qiblaBearing,
        isAligned = isAligned,
        accuracyStatus = accuracyStatus,
        isSensorSupported = isSupported,
        isUsingFallback = isUsingFallback,
        pitchRollOffset = pitchRollOffset
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fallback Warning Banners
        if (!isSupported) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Compass sensors are not supported on this device. Qibla compass requires an accelerometer and a magnetometer.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (accuracyStatus == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, GoldAccent, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔄", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Compass accuracy is unreliable. Please calibrate your sensor by waving your device in a figure-8 motion.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Direction / Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Qibla Direction",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${qiblaBearing.roundToInt()}° ${getCardinalDirection(qiblaBearing)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Device Heading",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${displayHeading.roundToInt()}° ${getCardinalDirection(displayHeading)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Distance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.US, "%,d", distanceKm)} km to Makkah",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Alignment Banner / Badge
                AnimatedVisibility(
                    visible = alignedBannerVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        color = EmeraldAccent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Aligned with Qibla ✨",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .wrapContentWidth(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        QiblaDial(state = dialState)

        Spacer(modifier = Modifier.height(24.dp))                    // Green/Gold Pointer Needle extending towards center
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(0.35f)
                            .background(GoldAccent, shape = RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}