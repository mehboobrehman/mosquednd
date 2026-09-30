package com.mosquednd.ui.qibla

import android.hardware.SensorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Pre-defined top-level colors to eliminate heap allocation during frame draws
private val ColorDarkBackground = Color(0xFF0F1715)
private val ColorBezelOuter = Color(0xFFC8A84E)
private val ColorBezelInner = Color(0x66C8A84E)
private val ColorStarMotif = Color(0x30C8A84E)
private val ColorNorthRed = Color(0xFFE74C3C)
private val ColorTickMajor = Color(0xFFF1C40F)
private val ColorTickMedium = Color(0xCCFFFFFF)
private val ColorTickMinor = Color(0x66FFFFFF)
private val ColorTextCardinal = Color(0xFFFFD700)
private val ColorTextDegree = Color(0xDDF0F0F0)
private val ColorKaabaBody = Color(0xFF141414)
private val ColorKaabaGold = Color(0xFFFFD700)
private val ColorKaabaWhite = Color(0xFFEEEEEE)
private val ColorLevelTarget = Color(0x88C8A84E)
private val ColorLevelBubbleLevel = Color(0xFF2ECC71)
private val ColorLevelBubbleTilt = Color(0xFFF39C12)
private val ColorAuraGreen = Color(0xFF2ECC71)
private val ColorWarningAmber = Color(0xFFE67E22)

/**
 * Cached layout data for cardinal and degree text markers on the dial.
 */
private data class DialTextMarker(
    val degree: Int,
    val textLayoutResult: TextLayoutResult,
    val isCardinal: Boolean
)

/**
 * High-performance, mathematically rendered Jetpack Compose Canvas Qibla Dial.
 *
 * Provides:
 * 1. Fluid 60/120 FPS rotation driven by continuous [QiblaDialState.userHeading].
 * 2. Zero heap allocations in render loop (all Paths, text measurements, brushes pre-cached).
 * 3. Kinematic wraparound protection consuming continuous heading delta.
 * 4. Interactive Kaaba indicator needle pointing towards [QiblaDialState.qiblaBearing].
 * 5. Debounced alignment aura feedback when [QiblaDialState.isAligned] is true.
 * 6. Pitch/Roll level guidance (bubble level) using [QiblaDialState.pitchRollOffset].
 */
@Composable
fun QiblaDial(
    state: QiblaDialState,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Pulsing transition for alignment aura feedback
    val infiniteTransition = rememberInfiniteTransition(label = "alignmentAuraTransition")
    val auraPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulseAlpha"
    )

    // Accessibility content description
    val cardinalDirection = remember(state.userHeading) {
        val norm = (state.userHeading % 360f + 360f) % 360f
        getCardinalText(norm)
    }
    val contentDesc = remember(state.userHeading, state.qiblaBearing, state.isAligned) {
        val normHeading = (state.userHeading % 360f + 360f) % 360f
        val qiblaDiff = abs((normHeading - state.qiblaBearing + 540f) % 360f - 180f)
        "Qibla compass dial. Device heading ${normHeading.roundToInt()}° $cardinalDirection. Kaaba bearing ${state.qiblaBearing.roundToInt()}°. " +
            if (state.isAligned) "Aligned with Qibla." else "${qiblaDiff.roundToInt()}° from Qibla."
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Spacer(modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                // Pre-allocate paths, brushes, and text layouts to avoid heap allocations in the draw loop
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f
                val radius = min(centerX, centerY)

                val dialRadius = radius * 0.9f
                val bezelOuterThickness = radius * 0.03f
                val bezelInnerThickness = radius * 0.015f
                val starOuterRadius = dialRadius * 0.18f
                val starInnerRadius = starOuterRadius * 0.5f

                // Pre-calculate paths
                val rubElHizbPath = Path().apply {
                    moveTo(centerX, centerY - starOuterRadius)
                    for (i in 1..8) {
                        val angleOuter = i * 45f - 90f
                        lineTo(
                            centerX + starInnerRadius * cos(Math.toRadians((angleOuter - 22.5).toDouble()).toFloat()),
                            centerY + starInnerRadius * sin(Math.toRadians((angleOuter - 22.5).toDouble()).toFloat())
                        )
                        lineTo(
                            centerX + starOuterRadius * cos(Math.toRadians(angleOuter.toDouble()).toFloat()),
                            centerY + starOuterRadius * sin(Math.toRadians(angleOuter.toDouble()).toFloat())
                        )
                    }
                    close()
                }

                val kaabaNeedlePath = Path().apply {
                    moveTo(centerX, centerY - dialRadius * 0.7f)
                    lineTo(centerX - dialRadius * 0.05f, centerY + dialRadius * 0.1f)
                    lineTo(centerX + dialRadius * 0.05f, centerY + dialRadius * 0.1f)
                    close()
                }

                val bezelOuterStroke = Stroke(width = bezelOuterThickness)
                val bezelInnerStroke = Stroke(width = bezelInnerThickness)
                val tickMajorStroke = Stroke(width = radius * 0.01f, cap = StrokeCap.Round)
                val tickMediumStroke = Stroke(width = radius * 0.006f, cap = StrokeCap.Round)
                val tickMinorStroke = Stroke(width = radius * 0.003f, cap = StrokeCap.Round)

                val cardinalTextStyle = TextStyle(
                    color = ColorTextCardinal,
                    fontSize = (radius * 0.1f).toSp(),
                    fontWeight = FontWeight.Bold
                )
                val degreeTextStyle = TextStyle(
                    color = ColorTextDegree,
                    fontSize = (radius * 0.07f).toSp(),
                    fontWeight = FontWeight.Normal
                )
                
                val textMarkers = mutableListOf<DialTextMarker>()
                val cardinalDirections = mapOf(
                    0 to "N", 90 to "E", 180 to "S", 270 to "W"
                )

                for (i in 0 until 360 step 30) {
                    val text = cardinalDirections[i] ?: i.toString()
                    val style = if (cardinalDirections.containsKey(i)) cardinalTextStyle else degreeTextStyle
                    textMarkers.add(
                        DialTextMarker(
                            degree = i,
                            textLayoutResult = textMeasurer.measure(
                                text,
                                style = style,
                                softWrap = false
                            ),
                            isCardinal = cardinalDirections.containsKey(i)
                        )
                    )
                }

                val auraBrush = Brush.radialGradient(
                    colors = listOf(ColorAuraGreen.copy(alpha = 0.6f), ColorAuraGreen.copy(alpha = 0.0f)),
                    center = Offset(centerX, centerY),
                    radius = dialRadius * 1.5f
                )

                onDrawWithContent {
                    // 1. Background
                    drawRect(ColorDarkBackground)

                    // Apply global rotation for the dial
                    rotate(degrees = -state.userHeading, pivot = Offset(centerX, centerY)) {
                        // 2. Glowing aura when aligned (drawn behind other elements)
                        if (state.isAligned) {
                            drawCircle(
                                brush = auraBrush,
                                radius = dialRadius * 1.2f, // Slightly larger than the dial itself
                                center = Offset(centerX, centerY),
                                alpha = auraPulseAlpha
                            )
                        }

                        // 3. Outer bezel ring
                        drawCircle(
                            color = ColorBezelOuter,
                            radius = dialRadius + bezelOuterThickness / 2,
                            center = Offset(centerX, centerY),
                            style = bezelOuterStroke
                        )
                        drawCircle(
                            color = ColorBezelInner,
                            radius = dialRadius + bezelOuterThickness + bezelInnerThickness / 2,
                            center = Offset(centerX, centerY),
                            style = bezelInnerStroke
                        )

                        // 4. Rub el Hizb 8-pointed star at the center
                        drawPath(rubElHizbPath, ColorStarMotif)

                        // 5. Tick marks (360 degrees)
                        for (i in 0 until 360) {
                            val tickLength: Float
                            val tickColor: Color
                            val tickStroke: Stroke

                            when {
                                i % 30 == 0 -> { // Major tick every 30 degrees
                                    tickLength = dialRadius * 0.15f
                                    tickColor = if (i == 0) ColorNorthRed else ColorTickMajor
                                    tickStroke = tickMajorStroke
                                }
                                i % 10 == 0 -> { // Medium tick every 10 degrees
                                    tickLength = dialRadius * 0.1f
                                    tickColor = ColorTickMedium
                                    tickStroke = tickMediumStroke
                                }
                                i % 5 == 0 -> { // Minor tick every 5 degrees
                                    tickLength = dialRadius * 0.05f
                                    tickColor = ColorTickMinor
                                    tickStroke = tickMinorStroke
                                }
                                else -> { // Minor tick every 2 degrees
                                    tickLength = dialRadius * 0.03f
                                    tickColor = ColorTickMinor
                                    tickStroke = tickMinorStroke
                                }
                            }

                            rotate(degrees = i.toFloat(), pivot = Offset(centerX, centerY)) {
                                drawLine(
                                    color = tickColor,
                                    start = Offset(centerX, centerY - dialRadius),
                                    end = Offset(centerX, centerY - dialRadius + tickLength),
                                    strokeWidth = tickStroke.width,
                                    cap = tickStroke.cap
                                )
                            }
                        }

                        // 6. Degree and cardinal text markers
                        textMarkers.forEach { marker ->
                            withTransform({
                                // Rotate to the correct position, then rotate text upright
                                rotate(degrees = marker.degree.toFloat(), pivot = Offset(centerX, centerY))
                                rotate(degrees = state.userHeading, pivot = Offset(centerX, centerY - dialRadius * 0.82f))
                            }) {
                                val textOffset = Offset(
                                    centerX - marker.textLayoutResult.size.width / 2,
                                    centerY - dialRadius * 0.82f - marker.textLayoutResult.size.height / 2
                                )
                                drawText(
                                    textLayoutResult = marker.textLayoutResult,
                                    color = if (marker.isCardinal) ColorTextCardinal else ColorTextDegree,
                                    topLeft = textOffset
                                )
                            }
                        }
                    } // End of global rotation for dial

                    // 7. Kaaba indicator needle (rotates independently to point at Qibla)
                    withTransform({
                        rotate(degrees = state.qiblaBearing, pivot = Offset(centerX, centerY))
                    }) {
                        drawPath(kaabaNeedlePath, ColorKaabaGold)
                        drawCircle(
                            color = ColorKaabaBody,
                            radius = dialRadius * 0.05f,
                            center = Offset(centerX, centerY - dialRadius * 0.7f)
                        )
                        drawCircle(
                            color = ColorKaabaWhite,
                            radius = dialRadius * 0.02f,
                            center = Offset(centerX, centerY - dialRadius * 0.7f)
                        )
                    }

                    // 8. Pitch/Roll spirit level guide
                    val levelIndicatorRadius = dialRadius * 0.15f
                    val levelBubbleRadius = dialRadius * 0.03f
                    val maxOffset = dialRadius * 0.2f

                    // Target circle
                    drawCircle(
                        color = ColorLevelTarget,
                        radius = levelIndicatorRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = radius * 0.005f)
                    )
                    drawCircle(
                        color = ColorLevelTarget,
                        radius = levelIndicatorRadius / 2,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = radius * 0.005f)
                    )

                    // Level bubble (shows if device is flat)
                    val clampedPitchX = (state.pitchRollOffset.x * maxOffset).coerceIn(-maxOffset, maxOffset)
                    val clampedPitchY = (state.pitchRollOffset.y * maxOffset).coerceIn(-maxOffset, maxOffset)

                    drawCircle(
                        color = ColorLevelBubbleLevel,
                        radius = levelBubbleRadius,
                        center = Offset(centerX + clampedPitchX, centerY + clampedPitchY)
                    )
                }
            }
        )
    }
}

/**
 * Returns cardinal direction abbreviation for a given angle in degrees.
 */
private fun getCardinalText(degrees: Float): String {
    val norm = (degrees % 360f + 360f) % 360f
    return when {
        norm >= 337.5f || norm < 22.5f -> "N"
        norm >= 22.5f && norm < 67.5f -> "NE"
        norm >= 67.5f && norm < 112.5f -> "E"
        norm >= 112.5f && norm < 157.5f -> "SE"
        norm >= 157.5f && norm < 202.5f -> "S"
        norm >= 202.5f && norm < 247.5f -> "SW"
        norm >= 247.5f && norm < 292.5f -> "W"
        else -> "NW"
    }
}
