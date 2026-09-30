package com.mosquednd.ui.qibla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2

class QiblaSensorTest {

    // Helper simulator class reproducing the shortest-path angle wrapping logic in QiblaScreen.kt
    class AngleAccumulatorSimulator {
        var accumulatedHeading = 0f
        var lastSensorHeading = 0f
        var isFirstHeading = true

        fun update(sensorHeading: Float) {
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
            }
        }
    }

    // Helper simulator class reproducing the Sin-Cos low-pass (EMA) filter logic in QiblaSensorManager.kt
    class SinCosFilterSimulator {
        var filteredSin = 0f
        var filteredCos = 0f
        var isFirstReading = true

        fun update(trueAzimuth: Float, alpha: Float = 0.15f) {
            val azimuthRad = Math.toRadians(trueAzimuth.toDouble()).toFloat()
            val targetSin = sin(azimuthRad)
            val targetCos = cos(azimuthRad)

            if (isFirstReading) {
                filteredSin = targetSin
                filteredCos = targetCos
                isFirstReading = false
            } else {
                filteredSin += (targetSin - filteredSin) * alpha
                filteredCos += (targetCos - filteredCos) * alpha
            }
        }

        fun getSmoothedAzimuth(): Float {
            return (Math.toDegrees(atan2(filteredSin.toDouble(), filteredCos.toDouble())).toFloat() + 360f) % 360f
        }
    }

    // Helper simulator class reproducing the watchdog logic in QiblaSensorManager.kt
    class WatchdogSimulator {
        var startTime = 0L
        var lastEventTime = 0L
        var lastValueChangeTime = 0L
        var fallbackTriggered = false
        var rotationVectorSensorExists = true

        fun start(now: Long) {
            startTime = now
            lastEventTime = 0L
            lastValueChangeTime = 0L
            fallbackTriggered = false
        }

        fun onSensorEvent(now: Long, changed: Boolean) {
            lastEventTime = now
            if (changed) {
                lastValueChangeTime = now
            }
        }

        fun checkWatchdog(now: Long): Boolean {
            if (!rotationVectorSensorExists || fallbackTriggered) return false
            
            val timeSinceLastEvent = if (lastEventTime == 0L) (now - startTime) else (now - lastEventTime)
            val timeSinceLastChange = if (lastValueChangeTime == 0L) (now - startTime) else (now - lastValueChangeTime)
            
            if (timeSinceLastEvent >= 1500L || timeSinceLastChange >= 3000L) {
                fallbackTriggered = true
                return true
            }
            return false
        }
    }

    @Test
    fun testWatchdog_fallbackOnNoEvents() {
        val watchdog = WatchdogSimulator()
        var now = 1000L
        watchdog.start(now)
        
        // At T=2000 (1s later), no events yet.
        now = 2000L
        assert(!watchdog.checkWatchdog(now)) // Threshold 1.5s not reached
        
        // At T=2600 (1.6s later), no events.
        now = 2600L
        assert(watchdog.checkWatchdog(now))
        assertTrue(watchdog.fallbackTriggered)
    }

    @Test
    fun testWatchdog_fallbackOnFrozenValues() {
        val watchdog = WatchdogSimulator()
        var now = 1000L
        watchdog.start(now)
        
        // Sensor produces events but values are same
        for (i in 1..25) {
            now += 100L
            watchdog.onSensorEvent(now, changed = false)
            watchdog.checkWatchdog(now)
        }
        // At T=3500 (2.5s since start), lastEventTime is 3500, but lastValueChangeTime is 0.
        // timeSinceLastChange = 3500 - 1000 = 2500. Not yet 3000.
        assert(!watchdog.fallbackTriggered)
        
        now += 600L
        watchdog.onSensorEvent(now, changed = false)
        assertTrue(watchdog.checkWatchdog(now)) // timeSinceLastChange = 4100 - 1000 = 3100 >= 3000
    }

    @Test
    fun testWatchdog_noFallbackOnHealthySensor() {
        val watchdog = WatchdogSimulator()
        var now = 1000L
        watchdog.start(now)
        
        for (i in 1..100) {
            now += 100L
            watchdog.onSensorEvent(now, changed = true)
            assert(!watchdog.checkWatchdog(now))
        }
        assert(!watchdog.fallbackTriggered)
    }

    @Test
    fun testCalculateQiblaBearing_referenceLocations() {
        // Known reference bearings to Kaaba (21.4225 N, 39.8262 E)
        
        // From Karachi (~24.86, 67.01): ~268°
        val karachiBearing = calculateQiblaBearing(24.8607, 67.0011)
        assertEquals(267.7f, karachiBearing, 0.1f)
        
        // From London (~51.51, -0.13): ~119°
        val londonBearing = calculateQiblaBearing(51.5074, -0.1278)
        assertEquals(119.0f, londonBearing, 0.1f)
        
        // From New York (~40.71, -74.00): ~58.5°
        val nyBearing = calculateQiblaBearing(40.7128, -74.0060)
        assertEquals(58.5f, nyBearing, 0.1f)
    }

    @Test
    fun testShortestPathAngleWrapping_northBoundaryCrossings() {
        // Test transition from 359° to 1° (should yield a delta of +2°: 359° -> 361°)
        val simulator1 = AngleAccumulatorSimulator()
        simulator1.update(359f)
        assertEquals(359f, simulator1.accumulatedHeading, 0.01f)

        simulator1.update(1f)
        assertEquals(361f, simulator1.accumulatedHeading, 0.01f)

        // Test transition from 1° to 359° (should yield a delta of -2°: 1° -> -1°)
        val simulator2 = AngleAccumulatorSimulator()
        simulator2.update(1f)
        assertEquals(1f, simulator2.accumulatedHeading, 0.01f)

        simulator2.update(359f)
        assertEquals(-1f, simulator2.accumulatedHeading, 0.01f)
    }

    @Test
    fun testShortestPathAngleWrapping_generalTransitions() {
        val simulator = AngleAccumulatorSimulator()
        
        // Initial state
        simulator.update(350f)
        assertEquals(350f, simulator.accumulatedHeading, 0.01f)

        // Small clockwise change: 350° -> 355° (+5° delta)
        simulator.update(355f)
        assertEquals(355f, simulator.accumulatedHeading, 0.01f)

        // Crossing north boundary: 355° -> 10° (+15° delta)
        simulator.update(10f)
        assertEquals(370f, simulator.accumulatedHeading, 0.01f)

        // Continued movement: 370° + 10° = 380f
        simulator.update(20f)
        assertEquals(380f, simulator.accumulatedHeading, 0.01f)

        // Backwards crossing: 20° -> 350f (-30° delta)
        simulator.update(350f)
        assertEquals(350f, simulator.accumulatedHeading, 0.01f)
    }

    @Test
    fun testSinCosFilter_eliminatesAveragingBoundaryAnomalies() {
        val simulator = SinCosFilterSimulator()

        // First reading: 359°
        simulator.update(359f)
        assertEquals(359f, simulator.getSmoothedAzimuth(), 0.1f)

        // Second reading: 1°.
        // Traditional average would be (359 + 1) / 2 = 180° (due South, an incorrect boundary anomaly).
        // Sin-Cos average with alpha = 0.5f should be exactly 0°/360° (due North).
        simulator.update(1f, alpha = 0.5f)

        val smoothedResult = simulator.getSmoothedAzimuth()
        // The smoothed azimuth should be extremely close to 0°/360° (allow small float precision)
        assertTrue(smoothedResult < 1f || smoothedResult > 359f)
        assertNotEquals(180f, smoothedResult, 5f)
    }

    @Test
    fun testCardinalDirectionCalculations() {
        // Reference angles for all 8 cardinal directions
        assertEquals("N", getCardinalDirection(0f))
        assertEquals("N", getCardinalDirection(15f))
        assertEquals("N", getCardinalDirection(359f))
        assertEquals("N", getCardinalDirection(338f))

        assertEquals("NE", getCardinalDirection(23f))
        assertEquals("NE", getCardinalDirection(45f))
        assertEquals("NE", getCardinalDirection(67f))

        assertEquals("E", getCardinalDirection(68f))
        assertEquals("E", getCardinalDirection(90f))
        assertEquals("E", getCardinalDirection(112f))

        assertEquals("SE", getCardinalDirection(113f))
        assertEquals("SE", getCardinalDirection(135f))
        assertEquals("SE", getCardinalDirection(157f))

        assertEquals("S", getCardinalDirection(158f))
        assertEquals("S", getCardinalDirection(180f))
        assertEquals("S", getCardinalDirection(202f))

        assertEquals("SW", getCardinalDirection(203f))
        assertEquals("SW", getCardinalDirection(225f))
        assertEquals("SW", getCardinalDirection(247f))

        assertEquals("W", getCardinalDirection(248f))
        assertEquals("W", getCardinalDirection(270f))
        assertEquals("W", getCardinalDirection(292f))

        assertEquals("NW", getCardinalDirection(293f))
        assertEquals("NW", getCardinalDirection(315f))
        assertEquals("NW", getCardinalDirection(337f))
    }
}