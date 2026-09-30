package com.mosquednd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

/**
 * Unit tests for Qibla bearing calculations and canvas angle conversions.
 *
 * Verifies the great-circle initial bearing formula used in [CompassSensorHandler.calculateQiblaBearing]
 * and the canvas angle conversion used in [QiblaArrow].
 *
 * Conventions:
 *   - North = 0°, East = 90°, South = 180°, West = 270° (standard compass bearing)
 *   - Canvas angles: 0° = right (+X), 90° = down (+Y), 180° = left (-X), 270° = up (-Y)
 *   - Canvas conversion: targetCanvasAngle = (bearing - 90 + 360) % 360
 *     North(0°) → canvas 270° (up), East(90°) → canvas 0° (right),
 *     South(180°) → canvas 90° (down), West(270°) → canvas 180° (left)
 */
class QiblaBearingTest {

    // -- Bearing calculation (matches CompassSensorHandler.calculateQiblaBearing) --

    private fun calculateQiblaBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val dLon = lon2Rad - lon1Rad

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360

        return bearing
    }

    // -- Canvas angle conversion (matches QiblaArrow) --

    private fun bearingToCanvasAngle(bearing: Float): Float {
        return ((bearing - 90f) % 360f + 360f) % 360f
    }

    // -- Known test cases --

    /**
     * Karachi (24.8607° N, 67.0011° E) → Kaaba (21.4225° N, 39.8261° E)
     * Expected bearing: ~267° (West by South-West)
     *
     * Computed manually:
     *   dLon = 39.8261 - 67.0011 = -27.175°
     *   y = sin(-27.175°) * cos(21.4225°) ≈ -0.4567 * 0.9305 ≈ -0.4249
     *   x = cos(24.8607°) * sin(21.4225°) - sin(24.8607°) * cos(21.4225°) * cos(-27.175°)
     *     ≈ 0.9069 * 0.3651 - 0.4214 * 0.9305 * 0.8921
     *     ≈ 0.3310 - 0.3497 ≈ -0.0187
     *   atan2(-0.4249, -0.0187) ≈ -92.5°
     *   bearing = (-92.5 + 360) % 360 ≈ 267.5°
     */
    @Test
    fun karachiToKaaba_returnsCorrectBearing() {
        val bearing = calculateQiblaBearing(
            24.8607, 67.0011,  // Karachi (Gulshan-e-Hadeed)
            21.4225, 39.8261   // Kaaba
        )
        // Expected ~267°, allow ±1° tolerance
        assertEquals(267.0, bearing, 1.0)
    }

    /**
     * Makkah (Kaaba) to Makkah — bearing should be 0° or 360° (no displacement).
     */
    @Test
    fun samePoint_returnsZero() {
        val bearing = calculateQiblaBearing(
            21.4225, 39.8261,
            21.4225, 39.8261
        )
        assertEquals(0.0, bearing, 0.001)
    }

    /**
     * Point directly north of Kaaba — bearing should be 180° (due south).
     */
    @Test
    fun dueNorth_returnsSouth() {
        val bearing = calculateQiblaBearing(
            30.0, 39.8261,   // Due north of Kaaba along same longitude
            21.4225, 39.8261
        )
        // Should be exactly or very close to 180°
        assertEquals(180.0, bearing, 0.5)
    }

    /**
     * Due east at the equator — bearing should be exactly 270° (due west),
     * because sin(lat) = 0 eliminates the x-term, making the result exact.
     */
    @Test
    fun dueEast_returnsWest() {
        val bearing = calculateQiblaBearing(
            0.0, 10.0,   // Equator, 10° east
            0.0, 0.0     // Equator, prime meridian (due west along equator)
        )
        // Great circle along equator is exactly due west = 270°
        assertEquals(270.0, bearing, 0.001)
    }

    // -- Cardinal bearing tests --

    @Test
    fun northBearing_isZero() {
        val bearing = calculateQiblaBearing(
            0.0, 0.0,  // Equator at prime meridian
            10.0, 0.0  // Due north
        )
        assertEquals(0.0, bearing, 0.5)
    }

    @Test
    fun eastBearing_isNinety() {
        val bearing = calculateQiblaBearing(
            0.0, 0.0,
            0.0, 10.0  // Due east
        )
        assertEquals(90.0, bearing, 0.5)
    }

    @Test
    fun southBearing_isOneEighty() {
        val bearing = calculateQiblaBearing(
            10.0, 0.0,
            0.0, 0.0  // Due south
        )
        assertEquals(180.0, bearing, 0.5)
    }

    @Test
    fun westBearing_isTwoSeventy() {
        val bearing = calculateQiblaBearing(
            0.0, 10.0,
            0.0, 0.0  // Due west
        )
        assertEquals(270.0, bearing, 0.5)
    }

    // -- Canvas angle conversion tests --

    @Test
    fun northBearing_convertsToCanvasUp() {
        // North (0°) → canvas 270° (up)
        assertEquals(270f, bearingToCanvasAngle(0f), 0.001f)
    }

    @Test
    fun eastBearing_convertsToCanvasRight() {
        // East (90°) → canvas 0° (right)
        assertEquals(0f, bearingToCanvasAngle(90f), 0.001f)
    }

    @Test
    fun southBearing_convertsToCanvasDown() {
        // South (180°) → canvas 90° (down)
        assertEquals(90f, bearingToCanvasAngle(180f), 0.001f)
    }

    @Test
    fun westBearing_convertsToCanvasLeft() {
        // West (270°) → canvas 180° (left)
        assertEquals(180f, bearingToCanvasAngle(270f), 0.001f)
    }

    // -- Canvas angle edge cases --

    @Test
    fun bearing360_convertsLikeZero() {
        assertEquals(270f, bearingToCanvasAngle(360f), 0.001f)
    }

    @Test
    fun bearingMinus90_convertsLike180() {
        // -90° bearing = 270° (west), canvas = 180°
        assertEquals(180f, bearingToCanvasAngle(-90f), 0.001f)
    }

    @Test
    fun bearingMinus180_convertsLike90() {
        // -180° = 180° (south), canvas = 90°
        assertEquals(90f, bearingToCanvasAngle(-180f), 0.001f)
    }

    @Test
    fun bearing450_convertsLike0() {
        // 450° = 90° (east), canvas = 0°
        assertEquals(0f, bearingToCanvasAngle(450f), 0.001f)
    }

    @Test
    fun bearing270_convertsTo180() {
        // West bearing → canvas left
        assertEquals(180f, bearingToCanvasAngle(270f), 0.001f)
    }

    // -- Sample Qibla direction from common cities --

    /**
     * Istanbul (41.0082° N, 28.9784° E) → Kaaba (21.4225° N, 39.8261° E)
     * Expected bearing: ~152° (South-East)
     */
    @Test
    fun istanbulToKaaba_returnsSouthEast() {
        val bearing = calculateQiblaBearing(
            41.0082, 28.9784,
            21.4225, 39.8261
        )
        // Istanbul ≈ 152° → 156° depending on exact formula
        assertEquals(152.0, bearing, 5.0)
    }

    /**
     * Jakarta (-6.2088, 106.8456) → Kaaba (21.4225° N, 39.8261° E)
     * Expected bearing: ~295° (West-North-West)
     */
    @Test
    fun jakartaToKaaba_returnsWNW() {
        val bearing = calculateQiblaBearing(
            -6.2088, 106.8456,
            21.4225, 39.8261
        )
        assertEquals(295.0, bearing, 5.0)
    }

    /**
     * Negative longitude wrapping: 0° to 360° normalization is handled
     * by the (bearing + 360) % 360 step.
     */
    @Test
    fun negativeLongitude_correctlyNormalized() {
        val bearing = calculateQiblaBearing(
            0.0, -10.0,  // west of prime meridian
            0.0, 10.0    // east of prime meridian
        )
        // Due east along equator = 90°
        assertEquals(90.0, bearing, 0.5)
    }

    // ── Boundary value tests (poles, equator, antipode) ──

    /**
     * Near North Pole (lat=89.9°, lon=0°) → Kaaba (21.4225° N, 39.8261° E).
     * The great-circle bearing should be approximately 140° (Southeast).
     * Even though pole bearings have higher sensitivity, the formula must
     * produce a valid number (no NaN, no crash).
     */
    @Test
    fun nearNorthPole_returnsValidBearing() {
        val bearing = calculateQiblaBearing(
            89.9, 0.0,
            21.4225, 39.8261
        )
        // Near-pole bearing: roughly 140° towards Kaaba
        assertTrue("Bearing should be > 0", bearing > 0.0)
        assertTrue("Bearing should be < 360", bearing < 360.0)
        assertEquals(140.0, bearing, 10.0)
    }

    /**
     * Near South Pole (lat=-89.9°, lon=0°) → Kaaba.
     * The great-circle bearing should be approximately 40° (Northeast).
     */
    @Test
    fun nearSouthPole_returnsValidBearing() {
        val bearing = calculateQiblaBearing(
            -89.9, 0.0,
            21.4225, 39.8261
        )
        // Near-south-pole bearing: roughly 40° towards Kaaba
        assertTrue("Bearing should be > 0", bearing > 0.0)
        assertTrue("Bearing should be < 360", bearing < 360.0)
        assertEquals(40.0, bearing, 10.0)
    }

    /**
     * Equator at prime meridian (0°, 0°) → Kaaba.
     * Bearing should be approximately 58° (ENE — East-North-East)
     * since Kaaba is northeast from the equator at 0° longitude.
     */
    @Test
    fun equatorToKaaba_returnsValidBearing() {
        val bearing = calculateQiblaBearing(
            0.0, 0.0,
            21.4225, 39.8261
        )
        // From equator/prime-meridian to Kaaba: ~58° ENE
        assertEquals(58.0, bearing, 5.0)
    }

    /**
     * Antipode of Kaaba (lat=-21.4225°, lon=-140.1739°) → Kaaba.
     * This is the exact opposite side of the Earth. The great-circle
     * bearing is technically undefined (any direction leads to Kaaba),
     * but the formula must not crash or return NaN.
     */
    @Test
    fun antipodeOfKaaba_returnsNonNanBearing() {
        val bearing = calculateQiblaBearing(
            -21.4225, -140.1739,
            21.4225, 39.8261
        )
        // Must not be NaN
        assertFalse("Bearing should not be NaN", bearing.isNaN())
        // Must be finite
        assertTrue("Bearing should be finite", bearing.isFinite())
        // Should be in valid range
        assertTrue("Bearing should be >= 0", bearing >= 0.0)
        assertTrue("Bearing should be < 360", bearing < 360.0)
    }
}
