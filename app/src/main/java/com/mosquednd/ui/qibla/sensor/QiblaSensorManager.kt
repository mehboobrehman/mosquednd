package com.mosquednd.ui.qibla.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.LogHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.sqrt

class QiblaSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll

    // True once a sensor-derived heading has been published this session. _heading starts at 0f,
    // which is NOT a real heading — on cold start no reading has arrived yet, and on warm re-entry
    // the value is stale from the previous session. QiblaScreen ignores _heading until this flips
    // so the synthetic 0.0° is never baked into the dial accumulator as its origin.
    private val _headingInitialized = MutableStateFlow(false)
    val headingInitialized: StateFlow<Boolean> = _headingInitialized

    private val _isUsingFallbackSensors = MutableStateFlow(false)
    val isUsingFallbackSensors: StateFlow<Boolean> = _isUsingFallbackSensors

    private val _fallbackCount = MutableStateFlow(0)
    val fallbackCount: StateFlow<Int> = _fallbackCount

    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _isSupported = MutableStateFlow(
        rotationVectorSensor != null || (accelSensor != null && magSensor != null)
    )
    val isSupported: StateFlow<Boolean> = _isSupported

    private val _accuracyStatus = MutableStateFlow(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    val accuracyStatus: StateFlow<Int> = _accuracyStatus

    // Preallocated sensor buffers
    private val accelerometerValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false
    
    private var isVertical = false
    private var filteredSin = 0f
    private var filteredCos = 0f
    private var isFirstReading = true

    // Preallocated intermediate math buffers to prevent 60Hz heap allocations
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val inclinationMatrix = FloatArray(9)

    // Cached declination values to avoid repeated GeomagneticField instantiations
    private var cachedDeclination = 0f
    private var lastDeclinationUpdateMillis = 0L
    private var lastLocationVersion = -1

    private val lastRotationVectorValues = FloatArray(5)
    private var lastValueChangeTime = 0L

    // Handler-based recurring Watchdog
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var startTime = 0L
    private var lastEventTime = 0L
    private var fallbackTriggered = false
    private var isRunning = false
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkWatchdog()
        }
    }

    // Corruption detection: tracks consecutive rotation vector readings with near-zero Z component.
    // A genuine rotation vector quaternion always has non-negligible Z; if it stays near zero for
    // multiple consecutive readings the hardware sensor is returning garbage and we must
    // fall back to accelerometer + magnetometer immediately.
    private var corruptReadingCount = 0
    private val corruptThreshold = 3

    // Heading variance detector: tracks consecutive large heading jumps from rotation vector.
    // On some devices the sensor returns non-zero but garbage quaternion values that pass the
    // magnitude check yet produce wildly wrong headings. If heading jumps >30° happen 5+ times
    // consecutively, the sensor is unreliable and we fall back.
    private var lastComputedAzimuth = -1f
    private var headingJumpCount = 0

    // Accel+mag reference heading state, used to validate the rotation vector path while it is
    // active. A broken rotation vector (e.g. itel S685LN) can produce non-zero-Z quaternions that
    // change every frame and pass the magnitude/frozen/variance checks yet still yield garbage
    // headings (smooth drift of 100-250°/s). Keeping accelerometer + magnetometer running in
    // parallel gives us an independent reference heading: if the two disagree persistently, the
    // rotation vector is corrupt and we fall back. The gravity-magnitude gate skips validation
    // while the device is moving, when the accel+mag reference would itself be unreliable.
    private var amReferenceSin = 0f
    private var amReferenceCos = 0f
    private var amReferenceFirst = true
    private var driftStartTime = 0L
    private var lastDriftLogTime = 0L
    private val driftThresholdDeg = 30f
    private val driftRequiredMs = 1500L

    // RV↔reference agreement gate: timestamp of the last time the RV heading and the accel+mag
    // reference agreed (diff <= driftThresholdDeg) since the reference last (re-)armed. A
    // systematic bias between the two pipelines is constant, so it evades the >35° jump check
    // and the 25° motion budget while still accumulating drift evidence (observed: 3 false
    // fallbacks in 21s at 08:51:09/23/29 on the itel S685LN before the reference was switched
    // to the same rotation-matrix pipeline). Never accuse the RV of drift before the pair has
    // actually agreed at least once; a 10s no-agreement escape keeps genuine cold-start garbage
    // detectable. 0L = no agreement since (re-)arm.
    private var lastRvRefAgreementTime = 0L
    private var lastRvRefDisagreementLogTime = 0L

    // True once the RV path has produced a heading this session. Before that, _heading.value holds
    // the initial 0f (cold start) or the previous session's stale value (warm re-entry) — comparing
    // it against the reference yields meaningless diffs during RV registration delay (observed:
    // rv=0.0° vs ref=195.3°). The drift detector ignores the reference until this flips.
    private var hasRvHeading = false

    // Accel+mag reference convergence tracking. The magnetometer emits garbage for ~1-2s after
    // (re-)registration (observed: reference swung ~267°→358.6° while the true heading stayed
    // ~268° — a false fallback blamed the RV). The drift detector stays blind until the reference
    // has enough samples, is old enough, and shows no >35° per-update raw jump.
    private var amReferenceSampleCount = 0
    private var amReferenceFirstSampleTime = 0L
    private var lastRawAmReferenceHeading = -1f
    private var amReferenceStableStartTime = 0L

    // True while gravity magnitude is outside the 8-11 m/s² stationary window (device moving).
    // While moving, an accel+mag heading is meaningless; the fallback path freezes the last
    // valid heading instead of publishing garbage.
    private var motionGated = false

    // Motion gate hysteresis + debounce. The itel S685LN's accelerometer routinely spikes
    // gravity to 11.0-12.4 m/s² while the device sits still, so the naive 8-11 window
    // froze/unfroze in rapid cycles (8 cycles in 3.5s at 08:17:33 on 2026-09-15), each freeze
    // capturing a different sample and making the heading jump between freezes. Freezing now
    // requires the out-of-band condition to persist >=500ms with >=3 consecutive samples;
    // clearing requires gravity back inside the tighter 8.8-10.2 m/s² clear band for the same
    // duration. The 8.0-8.8 and 10.2-11.0 gaps are neutral — they keep the current state
    // without resetting either candidate, so a single spike can no longer flip the gate.
    private val motionGateFreezeLow = 8.0f
    private val motionGateFreezeHigh = 11.0f
    private val motionGateClearLow = 8.8f
    private val motionGateClearHigh = 10.2f
    private val motionGateDebounceMs = 500L
    private val motionGateMinSamples = 3
    private var motionGateFreezeCandidateStart = 0L
    private var motionGateFreezeSampleCount = 0
    private var motionGateClearCandidateStart = 0L
    private var motionGateClearSampleCount = 0

    // Reference-motion budget: cumulative movement of the RAW accel+mag reference since drift
    // accumulation started. The reference can itself be the unstable party (post-registration
    // magnetometer swoops of 10.7°→332.2° observed on the itel S685LN, and slow ±35° wobbles
    // that evade the per-update >35° jump check). If the reference travels more than this
    // budget while we are accumulating drift evidence against the RV, the disagreement proves
    // nothing about the RV — the evidence is invalidated instead of triggering a false fallback
    // that swaps a working RV for a moving reference.
    private var referenceDriftMovement = 0f
    private var lastReferenceForDrift = -1f
    // Set once the convergence gate first passes — the field-log equivalent of "Reference
    // converged", confirming the drift detector is actually armed in the current session.
    private var amReferenceArmed = false

    // Fallback-path rate clamp state: caps the published accel+mag heading's per-second
    // movement so slow magnetometer wobbles (observed ±35° excursions over 11-16s on the itel
    // S685LN while the device sits still, peaking at 48°/s) drift the dial instead of visibly
    // spinning it, while real rotation below the cap passes through untouched.
    private var lastFallbackPublishTime = 0L
    private var lastPublishedFallbackHeading = -1f

    // Post-fallback watchdog: verifies heading actually changes after switching to accel+mag
    private var lastFallbackHeading = -1f
    private var lastFallbackHeadingCheckTime = 0L
    private val postFallbackCheckRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isRunning || !fallbackTriggered) return
            val headingChanged = abs(_heading.value - lastFallbackHeading) > 0.5f
            val elapsed = System.currentTimeMillis() - lastFallbackHeadingCheckTime
            LogHelper.logw("QiblaSensorManager",
                "Post-fallback check (${elapsed}ms): heading=${String.format("%.1f", _heading.value)}° " +
                "changed=$headingChanged hasAccel=$hasAccel hasMag=$hasMag " +
                "accelValues=[${String.format("%.2f", accelerometerValues[0])}, ${String.format("%.2f", accelerometerValues[1])}, ${String.format("%.2f", accelerometerValues[2])}] " +
                "magValues=[${String.format("%.2f", magneticValues[0])}, ${String.format("%.2f", magneticValues[1])}, ${String.format("%.2f", magneticValues[2])}]")
            // If the heading still has not moved, keep monitoring up to 15s — the first mag
            // event can lag seconds behind registration on this device, and the watchdog's
            // re-registration path handles a fully silent sensor. Stop once it recovers.
            if (!headingChanged && elapsed < 15000L) {
                handler.postDelayed(this, 2000L)
            }
        }
    }
    // Per-sensor event timestamps — the magnetometer on the itel S685LN can go silent just like
    // the rotation vector (same hardware fault), which would freeze the accel+mag fallback path.
    // The post-fallback watchdog uses these to detect a silent sensor and re-register it.
    private var accelLastEventTime = 0L
    private var magLastEventTime = 0L
    private var lastMagReRegisterTime = 0L
    private var magReRegisterAttempts = 0

    private var lastLogTime = 0L
    private var lastDiagnosticLogTime = 0L

    private fun checkWatchdog() {
        if (!isRunning) return
        if (rotationVectorSensor != null && !fallbackTriggered) {
            val now = System.currentTimeMillis()
            val timeSinceLastEvent = if (lastEventTime == 0L) (now - startTime) else (now - lastEventTime)
            val timeSinceLastChange = if (lastValueChangeTime == 0L) (now - startTime) else (now - lastValueChangeTime)

            // Trigger fallback if no events received within 1.5s OR events are frozen (identical values) for 3.0s
            if (timeSinceLastEvent >= 1500L || timeSinceLastChange >= 3000L) {
                fallbackToAccelMag(
                    reason = if (timeSinceLastEvent >= 1500L) "no events" else "frozen values",
                    telemetryDetail = mapOf(
                        "timeSinceLastEvent" to timeSinceLastEvent,
                        "timeSinceLastChange" to timeSinceLastChange
                    )
                )
            } else {
                // Continue recurring watchdog check every 500ms for more responsive detection
                handler.postDelayed(watchdogRunnable, 500L)
            }
        } else if (fallbackTriggered) {
            // Post-fallback: keep the accel+mag pipeline healthy. The magnetometer on the itel
            // S685LN can go silent just like the rotation vector; a silent sensor freezes the
            // fallback heading. If either stream is stale for 3s, re-register both (bounded to
            // 3 attempts, min 5s apart) to coax the hardware back to life.
            val now = System.currentTimeMillis()
            val accelStale = (now - accelLastEventTime) >= 3000L
            val magStale = (now - magLastEventTime) >= 3000L
            if ((accelStale || magStale) && magReRegisterAttempts < 3 && now - lastMagReRegisterTime >= 5000L) {
                lastMagReRegisterTime = now
                magReRegisterAttempts++
                LogHelper.logw(
                    "QiblaSensorManager",
                    "Post-fallback: sensor data stale (accel=${if (accelStale) "stale" else "ok"}, mag=${if (magStale) "stale" else "ok"}). Re-registering accel+mag (attempt $magReRegisterAttempts/3)."
                )
                sensorManager.unregisterListener(this)
                accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                resetReferenceState()
            }
            // Continue recurring watchdog check every 500ms
            handler.postDelayed(watchdogRunnable, 500L)
        }
    }

    /** Shared fallback path used by both corruption detection and the frozen-value watchdog. */
    private fun fallbackToAccelMag(reason: String, telemetryDetail: Map<String, Any> = emptyMap()) {
        fallbackTriggered = true
        _isUsingFallbackSensors.value = true
        _fallbackCount.value = _fallbackCount.value + 1
        // Reset filter state so new accel+mag heading takes effect immediately
        // instead of slowly converging from the old stuck rotation vector heading
        isFirstReading = true
        filteredSin = 0f
        filteredCos = 0f
        lastComputedAzimuth = -1f
        headingJumpCount = 0
        corruptReadingCount = 0
        amReferenceSin = 0f
        amReferenceCos = 0f
        amReferenceFirst = true
        driftStartTime = 0L
        lastDriftLogTime = 0L
        LogHelper.logw(
            "QiblaSensorManager",
            "Watchdog: Rotation vector sensor $reason (lastEvent: ${if (lastEventTime == 0L) "n/a" else "${System.currentTimeMillis() - lastEventTime}ms"}, " +
                "lastChange: ${if (lastValueChangeTime == 0L) "n/a" else "${System.currentTimeMillis() - lastValueChangeTime}ms"}). " +
                "Falling back to accelerometer and magnetometer."
        )
        LogHelper.logTelemetry(
            LogHelper.TelemetryCategory.DIAGNOSTIC,
            "QIBLA_SENSOR_WATCHDOG_TRIGGERED",
            mapOf("reason" to reason) + telemetryDetail
        )
        sensorManager.unregisterListener(this, rotationVectorSensor)
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // The freshly (re-)registered magnetometer wakes up with ~1-2s of garbage; re-arm the
        // convergence guards so a glitching reference cannot drive any later validation.
        resetReferenceState()
        lastFallbackPublishTime = 0L
        lastPublishedFallbackHeading = -1f
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.DIAGNOSTIC, "QIBLA_FALLBACK_COMPLETE", mapOf(
            "hasAccelSensor" to (accelSensor != null),
            "hasMagSensor" to (magSensor != null),
            "accelName" to (accelSensor?.name ?: "null"),
            "magName" to (magSensor?.name ?: "null")
        ))
        schedulePostFallbackCheck()
        // Keep the recurring watchdog alive in post-fallback mode so the stale-sensor branch
        // (silent magnetometer re-registration) keeps running. Without this, the RV branch stops
        // re-posting once fallbackTriggered is set and the cycle dies.
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, 500L)
    }

    private fun updateDeclination() {
        try {
            val (lat, lng) = LocationProvider.getEffectiveLocation()
            lastLocationVersion = LocationProvider.getLocationVersion()
            
            val geoField = GeomagneticField(
                lat.toFloat(),
                lng.toFloat(),
                0f,
                System.currentTimeMillis()
            )
            cachedDeclination = geoField.declination
            lastDeclinationUpdateMillis = System.currentTimeMillis()
        } catch (e: Exception) {
            LogHelper.loge("QiblaSensorManager", "Failed to update declination: ${e.message}")
            cachedDeclination = 0f
            lastDeclinationUpdateMillis = System.currentTimeMillis()
        }
    }

    fun start() {
        // Stop first to ensure clean state and avoid duplicate registration
        stop()
        
        // Re-initialize state to prevent stale or frozen reading on screen re-entry
        isRunning = true
        isFirstReading = true
        filteredSin = 0f
        filteredCos = 0f
        hasAccel = false
        hasMag = false
        isVertical = false
        lastLocationVersion = -1
        startTime = System.currentTimeMillis()
        lastEventTime = 0L
        lastValueChangeTime = 0L
        lastRotationVectorValues.fill(0f)
        fallbackTriggered = false
        handler.removeCallbacks(postFallbackCheckRunnable)
        corruptReadingCount = 0
        lastComputedAzimuth = -1f
        headingJumpCount = 0
        amReferenceSin = 0f
        amReferenceCos = 0f
        amReferenceFirst = true
        driftStartTime = 0L
        lastDriftLogTime = 0L
        hasRvHeading = false
        amReferenceSampleCount = 0
        amReferenceFirstSampleTime = 0L
        lastRawAmReferenceHeading = -1f
        amReferenceStableStartTime = 0L
        motionGated = false
        motionGateFreezeCandidateStart = 0L
        motionGateFreezeSampleCount = 0
        motionGateClearCandidateStart = 0L
        motionGateClearSampleCount = 0
        referenceDriftMovement = 0f
        lastReferenceForDrift = -1f
        amReferenceArmed = false
        lastRvRefAgreementTime = 0L
        lastRvRefDisagreementLogTime = 0L
        lastFallbackPublishTime = 0L
        lastPublishedFallbackHeading = -1f
        lastLogTime = 0L
        lastDiagnosticLogTime = 0L
        _isUsingFallbackSensors.value = false
        _headingInitialized.value = false
        
        accelerometerValues.fill(0f)
        magneticValues.fill(0f)
        rotationMatrix.fill(0f)
        remappedMatrix.fill(0f)
        orientation.fill(0f)
        inclinationMatrix.fill(0f)

        // Per-sensor staleness timestamps seed from startTime so the post-fallback watchdog
        // measures silence from the moment sensors were (re-)registered, not from a stale 0.
        accelLastEventTime = startTime
        magLastEventTime = startTime
        magReRegisterAttempts = 0
        lastMagReRegisterTime = 0L

        updateDeclination()

        LogHelper.logd("QiblaSensorManager", "Starting Qibla sensors. Support status: rotationVectorSensor=${rotationVectorSensor != null}, accelSensor=${accelSensor != null}, magSensor=${magSensor != null}")
        LogHelper.logTelemetry(
            LogHelper.TelemetryCategory.EXECUTION,
            "QIBLA_SENSORS_START",
            mapOf(
                "hasRotationVector" to (rotationVectorSensor != null),
                "hasAccel" to (accelSensor != null),
                "hasMag" to (magSensor != null)
            )
        )

        if (rotationVectorSensor != null) {
            val registered = sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
            if (registered) {
                // Register accel+mag alongside rotation vector so we can continuously cross-validate
                // the RV heading against an independent reference and detect smooth-drift corruption
                // that evades the magnitude/frozen/variance detectors.
                accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                handler.postDelayed(watchdogRunnable, 500L)
                LogHelper.logd("QiblaSensorManager", "Registered rotationVectorSensor (${rotationVectorSensor.name}) + accel/mag reference sensors, recurring watchdog scheduled (500ms check interval).")
            } else {
                LogHelper.logw("QiblaSensorManager", "Failed to register rotationVectorSensor, falling back immediately to accelerometer and magnetometer.")
                fallbackTriggered = true
                _isUsingFallbackSensors.value = true
                _fallbackCount.value = _fallbackCount.value + 1
                accelLastEventTime = System.currentTimeMillis()
                magLastEventTime = System.currentTimeMillis()
                accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                handler.postDelayed(watchdogRunnable, 500L)
            }
        } else {
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            LogHelper.logd("QiblaSensorManager", "Registered fallback accelSensor & magSensor (no rotationVectorSensor found).")
        }
    }

    fun stop() {
        LogHelper.logd("QiblaSensorManager", "Stopping Qibla sensors and cancelling watchdog.")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "QIBLA_SENSORS_STOP")
        isRunning = false
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(postFallbackCheckRunnable)
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values == null || event.values.isEmpty()) return
        lastEventTime = System.currentTimeMillis()

        // Throttle high-frequency sensor value logs to 1-second intervals using a millisecond check so we don't saturate the log stream.
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime >= 1000L) {
            lastLogTime = currentTime
            val valuesStr = event.values.joinToString(prefix = "[", postfix = "]") { String.format("%.2f", it) }
            LogHelper.logd("QiblaSensorManager", "Sensor changed: type=${event.sensor.type}, values=$valuesStr")
        }

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                if (event.values.size >= 3) {
                    // Detect corrupt rotation vector: a valid quaternion always has Z ≠ 0.
                    // On some devices (e.g. itel S685LN) the Z component is locked at 0.00,
                    // producing garbage headings that cause the compass dial to jump erratically
                    // or appear stuck. Detect this immediately and fall back to accel+mag
                    // rather than waiting for the slow frozen-value watchdog.
                    // A valid rotation vector quaternion has |Z| > some small threshold.
                    // Garbage values from broken sensors are typically < 0.01 in magnitude.
                    val zMagnitude = abs(event.values[2])
                    if (zMagnitude < 0.01f) {
                        corruptReadingCount++
                        if (corruptReadingCount >= corruptThreshold && !fallbackTriggered) {
                            LogHelper.logw("QiblaSensorManager", "Detected $corruptReadingCount consecutive corrupt rotation vector readings (|Z|=$zMagnitude < 0.01). Falling back to accelerometer and magnetometer.")
                            fallbackToAccelMag(
                                reason = "corrupt rotation vector (|Z|=$zMagnitude)",
                                telemetryDetail = mapOf("corruptCount" to corruptReadingCount, "zMagnitude" to zMagnitude)
                            )
                            return
                        }
                        // Don't feed corrupt data into the rotation matrix — skip processing
                        return
                    }

                    // Valid reading — reset corruption counter
                    if (corruptReadingCount > 0) {
                        corruptReadingCount = 0
                    }

                    // Check if values actually changed to detect frozen hardware sensor
                    var changed = false
                    for (i in 0 until minOf(event.values.size, 5)) {
                        if (abs(event.values[i] - lastRotationVectorValues[i]) > 1e-6f) {
                            changed = true
                            break
                        }
                    }

                    if (changed || lastValueChangeTime == 0L) {
                        lastValueChangeTime = System.currentTimeMillis()
                        System.arraycopy(event.values, 0, lastRotationVectorValues, 0, minOf(event.values.size, 5))
                    }

                    // A real RV quaternion is about to drive the heading — from here on the drift
                    // detector may compare _heading.value against the accel+mag reference.
                    hasRvHeading = true
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    processRotationMatrix(rotationMatrix)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (event.values.size >= 3) {
                    accelLastEventTime = System.currentTimeMillis()
                    System.arraycopy(event.values, 0, accelerometerValues, 0, 3)
                    hasAccel = true
                    if (rotationVectorSensor != null && !fallbackTriggered) {
                        validateRotationVectorAgainstAccelMag()
                    } else {
                        computeHeadingFromAccelMag()
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (event.values.size >= 3) {
                    magLastEventTime = System.currentTimeMillis()
                    System.arraycopy(event.values, 0, magneticValues, 0, 3)
                    hasMag = true
                    if (rotationVectorSensor != null && !fallbackTriggered) {
                        validateRotationVectorAgainstAccelMag()
                    } else {
                        computeHeadingFromAccelMag()
                    }
                }
            }
        }
    }

    private fun schedulePostFallbackCheck() {
        lastFallbackHeading = _heading.value
        lastFallbackHeadingCheckTime = System.currentTimeMillis()
        handler.postDelayed(postFallbackCheckRunnable, 5000L)
    }

    /**
     * Resets the accel+mag cross-validation reference convergence state. Must be called whenever
     * the accel/mag listeners are (re-)registered: on this device the magnetometer wakes up with
     * ~1-2s of garbage, and the reference must re-earn the "settled" status before the drift
     * detector trusts it again.
     */
    private fun resetReferenceState() {
        amReferenceSampleCount = 0
        amReferenceFirstSampleTime = 0L
        lastRawAmReferenceHeading = -1f
        amReferenceStableStartTime = 0L
        referenceDriftMovement = 0f
        lastReferenceForDrift = -1f
        amReferenceArmed = false
        lastRvRefAgreementTime = 0L
        lastRvRefDisagreementLogTime = 0L
    }

    /** Shortest angular distance between two headings in degrees (0..180). Wrap-aware. */
    private fun shortestAngleDeg(a: Float, b: Float): Float {
        var d = abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    /**
     * Smoothed heading derived purely from accelerometer + magnetometer, declination-corrected.
     * Uses the same getRotationMatrix -> remap -> getOrientation pipeline as the fallback
     * heading so a CORRECT rotation vector always agrees with it (see decision log); the
     * tilt-compensated atan2 trig path is kept only as a fallback when the matrix build fails.
     * Own filter state so it can run as a corruption reference while the RV path drives _heading.
     */
    private fun computeAccelMagHeading(): Float? {
        if (!hasAccel || !hasMag) return null

        val azimuth: Float
        // Use the same rotation-matrix pipeline as the authoritative fallback heading
        // (getRotationMatrix -> remap when vertical -> getOrientation -> declination), so
        // the reference agrees with a CORRECT rotation vector. The previous hand-rolled
        // tilt-compensated atan2 disagreed with the true heading by a systematic ~80° on
        // the itel S685LN (axis-convention mismatch with the device's sensor frame): at
        // 08:51 the reference read ~306-351° while both the RV and the fallback heading
        // read ~246-270°, so the drift detector repeatedly accused the RV of diverging
        // and false-fell-back (3x in 21s at 08:51:09/23/29). A constant bias also evades
        // the >35° jump check and the 25° motion budget because it does not move.
        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues)) {
            val gZ = abs(rotationMatrix[8])
            if (gZ.isNaN()) return null
            if (isVertical) {
                if (gZ > 0.75f) isVertical = false
            } else {
                if (gZ < 0.65f) isVertical = true
            }
            if (isVertical) {
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedMatrix)
            } else {
                System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, rotationMatrix.size)
            }
            SensorManager.getOrientation(remappedMatrix, orientation)
            val rawAzimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
            if (rawAzimuth.isNaN()) return null
            // Geographic (true) heading — same declination cache the RV path uses.
            azimuth = (rawAzimuth + cachedDeclination + 360f) % 360f
        } else {
            // getRotationMatrix failed (e.g. magnetometer not yet calibrated): fall back to
            // the tilt-compensated trig estimate. Less accurate on this device, but better
            // than having no reference at all while the matrix path is unavailable.
            val ax = accelerometerValues[0]; val ay = accelerometerValues[1]; val az = accelerometerValues[2]
            val mx = magneticValues[0]; val my = magneticValues[1]; val mz = magneticValues[2]
            val pitch = Math.atan2(-ax.toDouble(), Math.sqrt((ay * ay + az * az).toDouble())).toFloat()
            val roll = Math.atan2(ay.toDouble(), az.toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(pitch.toDouble()).toFloat()
            val rollDeg = Math.toDegrees(roll.toDouble()).toFloat()
            if (!pitchDeg.isNaN()) _pitch.value = pitchDeg
            if (!rollDeg.isNaN()) _roll.value = rollDeg
            val mxComp = mx * cos(roll) + mz * sin(roll)
            val myComp = mx * sin(pitch) * sin(roll) + my * cos(pitch) - mz * sin(pitch) * cos(roll)
            val trigAzimuth = Math.toDegrees(Math.atan2(myComp.toDouble(), mxComp.toDouble())).toFloat()
            azimuth = (trigAzimuth + cachedDeclination + 360f) % 360f
        }

        if (azimuth.isNaN() || azimuth.isInfinite()) return null

        val rad = Math.toRadians(azimuth.toDouble()).toFloat()
        val targetSin = sin(rad)
        val targetCos = cos(rad)
        if (targetSin.isNaN() || targetCos.isNaN()) return null

        val now = System.currentTimeMillis()
        if (amReferenceFirstSampleTime == 0L) amReferenceFirstSampleTime = now
        amReferenceSampleCount++
        if (amReferenceFirst) {
            amReferenceSin = targetSin
            amReferenceCos = targetCos
            amReferenceFirst = false
            lastRawAmReferenceHeading = -1f
            amReferenceStableStartTime = 0L
        } else {
            val alpha = 0.30f
            amReferenceSin += (targetSin - amReferenceSin) * alpha
            amReferenceCos += (targetCos - amReferenceCos) * alpha
        }
        val smoothed = (Math.toDegrees(atan2(amReferenceSin.toDouble(), amReferenceCos.toDouble())).toFloat() + 360f) % 360f
        // Reference self-stability: a large jump in the RAW heading between consecutive updates
        // means the magnetometer is glitching. While unstable, the drift detector stays blind so
        // a glitching reference cannot trigger a fallback that blames the rotation vector.
        if (!amReferenceFirst) {
            if (lastRawAmReferenceHeading >= 0f && abs(shortestAngleDeg(azimuth, lastRawAmReferenceHeading)) > 35f) {
                amReferenceStableStartTime = 0L
            } else if (amReferenceStableStartTime == 0L) {
                amReferenceStableStartTime = now
            }
            lastRawAmReferenceHeading = azimuth
        }
        return if (smoothed.isNaN() || smoothed.isInfinite()) null else smoothed
    }

    /**
     * Cross-validates the rotation-vector heading against the independent accel+mag reference.
     * If they disagree by more than [driftThresholdDeg] continuously for [driftRequiredMs], the
     * rotation vector is producing garbage that passes all other detectors (non-zero Z, changing
     * values, per-frame deltas under the variance threshold) and we fall back to accel+mag.
     * The gravity-magnitude gate prevents false triggers while the device is moving (when the
     * accel+mag reference would itself be unreliable).
     */
    private fun validateRotationVectorAgainstAccelMag() {
        val ref = computeAccelMagHeading() ?: return
        val ax = accelerometerValues[0]; val ay = accelerometerValues[1]; val az = accelerometerValues[2]
        val gravity = sqrt(ax * ax + ay * ay + az * az)
        if (gravity < 8.0f || gravity > 11.0f) {
            driftStartTime = 0L // device moving — accel+mag reference unreliable, skip validation
            return
        }
        val now = System.currentTimeMillis()
        // The RV path must have published a heading this session — before that _heading.value is
        // the initial 0f or the previous session's stale value (observed rv=0.0° vs ref=195.3°).
        if (!hasRvHeading) {
            driftStartTime = 0L
            return
        }
        // The reference must have settled before it is trusted to contradict the RV: enough
        // samples, alive long enough, and no recent magnetometer glitch (observed reference
        // swing ~267°→358.6° while the true heading stayed ~268°).
        if (amReferenceSampleCount < 10 || now - amReferenceFirstSampleTime < 1500L ||
            amReferenceStableStartTime == 0L || now - amReferenceStableStartTime < 1500L) {
            driftStartTime = 0L
            return
        }
        if (!amReferenceArmed) {
            amReferenceArmed = true
            LogHelper.logd("QiblaSensorManager", "Reference converged: ${amReferenceSampleCount} samples, ${now - amReferenceFirstSampleTime}ms old, stable for ${now - amReferenceStableStartTime}ms — drift detector armed (rv=${String.format("%.1f", _heading.value)}°).")
        }
        val rvHeading = _heading.value
        val diff = shortestAngleDeg(rvHeading, ref)
        if (diff > driftThresholdDeg) {
            // Agreement gate: the RV and reference must have actually agreed at least once since
            // the reference last (re-)armed before any drift evidence against the RV counts.
            // A systematic pipeline bias is constant — it passes the >35° jump check and the
            // 25° motion budget, so the old code re-accumulated 1500ms evidence windows and
            // false-fell-back 3x/21s at 08:51 on the itel S685LN, resetting the accumulated
            // heading each time and jarring the dial. Agreement must precede accusation.
            if (lastRvRefAgreementTime == 0L) {
                // Escape hatch: if the reference is settled and they STILL never agree 10s on,
                // the RV is genuinely garbage from cold start — then allow drift evidence so
                // that corruption is still caught (belt-and-braces; with the matched pipelines
                // the pair agrees within a few seconds whenever the RV is healthy).
                if (now - amReferenceFirstSampleTime < 10000L) {
                    if (now - lastRvRefDisagreementLogTime >= 2000L) {
                        lastRvRefDisagreementLogTime = now
                        LogHelper.logd(
                            "QiblaSensorManager",
                            "RV/ref disagreement before any agreement (rv=${String.format("%.1f", rvHeading)}° ref=${String.format("%.1f", ref)}° diff=${String.format("%.1f", diff)}°) — ignoring until they agree."
                        )
                    }
                    driftStartTime = 0L
                    referenceDriftMovement = 0f
                    lastReferenceForDrift = -1f
                    return
                }
            }
            if (driftStartTime == 0L) {
                driftStartTime = now
                // Seed the reference-motion budget at the start of each evidence window.
                referenceDriftMovement = 0f
                lastReferenceForDrift = ref
            } else if (lastReferenceForDrift >= 0f) {
                // The reference must stay still while we accuse the RV of moving. If the raw
                // accel+mag reference itself travels (magnetometer swoop/wobble), the
                // disagreement says nothing about the RV — the reference is the moving party.
                referenceDriftMovement += shortestAngleDeg(ref, lastReferenceForDrift)
                lastReferenceForDrift = ref
                if (referenceDriftMovement > 25f) {
                    LogHelper.logd("QiblaSensorManager", "Reference moved ${String.format("%.1f", referenceDriftMovement)}° during drift window (rv=${String.format("%.1f", rvHeading)}° ref=${String.format("%.1f", ref)}°) — reference unstable, invalidating drift evidence.")
                    driftStartTime = 0L
                    referenceDriftMovement = 0f
                    lastReferenceForDrift = -1f
                    return
                }
            }
            if (now - driftStartTime >= driftRequiredMs) {
                LogHelper.logw(
                    "QiblaSensorManager",
                    "Rotation vector heading diverged from accel+mag reference by ${String.format("%.1f", diff)}° for ${(now - driftStartTime)}ms. Sensor producing garbage. Falling back."
                )
                fallbackToAccelMag(
                    reason = "rotation vector drifts from accel+mag reference (${String.format("%.1f", diff)}°)",
                    telemetryDetail = mapOf("diffDeg" to String.format("%.1f", diff), "driftMs" to (now - driftStartTime), "gravity" to String.format("%.1f", gravity))
                )
                driftStartTime = 0L
            } else if (now - lastDriftLogTime >= 2000L) {
                lastDriftLogTime = now
                LogHelper.logd("QiblaSensorManager", "RV drift check: rv=${String.format("%.1f", rvHeading)}° ref=${String.format("%.1f", ref)}° diff=${String.format("%.1f", diff)}° (${(now - driftStartTime)}ms of ${driftRequiredMs}ms required)")
            }
        } else {
            driftStartTime = 0L
            referenceDriftMovement = 0f
            lastReferenceForDrift = -1f
            // Pair agrees — record it so the disagreement side knows agreement happened.
            lastRvRefAgreementTime = now
        }
    }

    private fun computeHeadingFromAccelMag() {
        if (rotationVectorSensor != null && !fallbackTriggered) return
        if (hasAccel && hasMag) {
            val now = System.currentTimeMillis()
            if (now - lastDiagnosticLogTime >= 1000L) {
                lastDiagnosticLogTime = now
                LogHelper.logd("QiblaSensorManager", "AccelMag: ax=${String.format("%.2f", accelerometerValues[0])} ay=${String.format("%.2f", accelerometerValues[1])} az=${String.format("%.2f", accelerometerValues[2])} mx=${String.format("%.2f", magneticValues[0])} my=${String.format("%.2f", magneticValues[1])} mz=${String.format("%.2f", magneticValues[2])} fallback=$fallbackTriggered")
            }
            // Motion gate (hysteresis + debounce): gravity magnitude outside the 8-11 m/s²
            // stationary window means the device is being moved, and a tilt-compensated accel+mag
            // heading computed while moving is meaningless (observed headings 11.6°→305.5°→188.2°
            // →244.9°→128.2° over 4s at gravity 3.7-22.8 m/s² while the user waved the device —
            // the dial spun wildly). Freeze the last valid heading instead of publishing garbage
            // the UI accumulator would bake in as fake rotation. Freezing requires the out-of-band
            // condition to persist >=500ms (>=3 consecutive samples); clearing requires gravity
            // back inside the tighter 8.8-10.2 m/s² clear band for the same duration. The freeze
            // and clear are logged ONCE per episode, never per flap.
            val gravityMag = sqrt(
                accelerometerValues[0] * accelerometerValues[0] +
                    accelerometerValues[1] * accelerometerValues[1] +
                    accelerometerValues[2] * accelerometerValues[2]
            )
            val outOfBand = gravityMag < motionGateFreezeLow || gravityMag > motionGateFreezeHigh
            val inClearBand = gravityMag >= motionGateClearLow && gravityMag <= motionGateClearHigh
            if (!motionGated) {
                motionGateClearCandidateStart = 0L
                motionGateClearSampleCount = 0
                if (outOfBand) {
                    if (motionGateFreezeCandidateStart == 0L) {
                        motionGateFreezeCandidateStart = now
                        motionGateFreezeSampleCount = 1
                    } else {
                        motionGateFreezeSampleCount++
                    }
                    if (now - motionGateFreezeCandidateStart >= motionGateDebounceMs &&
                        motionGateFreezeSampleCount >= motionGateMinSamples
                    ) {
                        motionGated = true
                        LogHelper.logd("QiblaSensorManager", "Motion gate: gravity=${String.format("%.1f", gravityMag)} m/s² outside 8-11 (persisted ${now - motionGateFreezeCandidateStart}ms, $motionGateFreezeSampleCount samples) — freezing heading at ${String.format("%.1f", _heading.value)}°")
                        motionGateFreezeCandidateStart = 0L
                        motionGateFreezeSampleCount = 0
                    }
                    // Still counting (or just frozen) — never publish a heading computed while
                    // gravity is out of the stationary window.
                    return
                }
                motionGateFreezeCandidateStart = 0L
                motionGateFreezeSampleCount = 0
            } else {
                motionGateFreezeCandidateStart = 0L
                motionGateFreezeSampleCount = 0
                if (inClearBand) {
                    if (motionGateClearCandidateStart == 0L) {
                        motionGateClearCandidateStart = now
                        motionGateClearSampleCount = 1
                    } else {
                        motionGateClearSampleCount++
                    }
                    if (now - motionGateClearCandidateStart >= motionGateDebounceMs &&
                        motionGateClearSampleCount >= motionGateMinSamples
                    ) {
                        motionGated = false
                        LogHelper.logd("QiblaSensorManager", "Motion gate cleared: gravity=${String.format("%.1f", gravityMag)} m/s² back in stationary window (persisted ${now - motionGateClearCandidateStart}ms, $motionGateClearSampleCount samples) — resuming accel+mag heading.")
                        motionGateClearCandidateStart = 0L
                        motionGateClearSampleCount = 0
                    }
                } else {
                    motionGateClearCandidateStart = 0L
                    motionGateClearSampleCount = 0
                }
                if (motionGated) return
            }
            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues)) {
                processRotationMatrix(rotationMatrix)
            } else {
                // Fallback: compute heading directly from raw accel+mag using tilt-compensated atan2
                // This handles devices where getRotationMatrix() returns false due to poor magnetometer calibration
                // (e.g. itel S685LN) — without this, _heading.value is never updated and the dial stays stuck.
                val ax = accelerometerValues[0]; val ay = accelerometerValues[1]; val az = accelerometerValues[2]
                val mx = magneticValues[0]; val my = magneticValues[1]; val mz = magneticValues[2]

                val pitch = Math.atan2(-ax.toDouble(), Math.sqrt((ay * ay + az * az).toDouble())).toFloat()
                val roll = Math.atan2(ay.toDouble(), az.toDouble()).toFloat()

                val mxComp = mx * cos(roll) + mz * sin(roll)
                val myComp = mx * sin(pitch) * sin(roll) + my * cos(pitch) - mz * sin(pitch) * cos(roll)

                var azimuth = Math.toDegrees(Math.atan2(myComp.toDouble(), mxComp.toDouble())).toFloat()
                azimuth = (azimuth + 360f) % 360f

                val currentTime = System.currentTimeMillis()
                val currentLocationVersion = LocationProvider.getLocationVersion()
                if (currentLocationVersion != lastLocationVersion || currentTime - lastDeclinationUpdateMillis > 1800000L) {
                    updateDeclination()
                }
                azimuth = (azimuth + cachedDeclination + 360f) % 360f

                if (!azimuth.isNaN() && !azimuth.isInfinite()) {
                    val azimuthRad = Math.toRadians(azimuth.toDouble()).toFloat()
                    val targetSin = sin(azimuthRad)
                    val targetCos = cos(azimuthRad)
                    if (!targetSin.isNaN() && !targetCos.isNaN()) {
                        if (isFirstReading) {
                            filteredSin = targetSin; filteredCos = targetCos; isFirstReading = false
                        } else {
                            val alpha = 0.30f
                            filteredSin += (targetSin - filteredSin) * alpha
                            filteredCos += (targetCos - filteredCos) * alpha
                        }
                        val smoothedAzimuth = (Math.toDegrees(atan2(filteredSin.toDouble(), filteredCos.toDouble())).toFloat() + 360f) % 360f
                        if (!smoothedAzimuth.isNaN()) {
                            LogHelper.logd("QiblaSensorManager", "Heading from direct trig: raw=${String.format("%.1f", azimuth)}° smoothed=${String.format("%.1f", smoothedAzimuth)}° (getRotationMatrix failed)")
                            publishHeading(smoothedAzimuth)
                        }
                    }
                } else {
                    LogHelper.logw("QiblaSensorManager", "Direct trig heading is NaN/Infinite. Skipping.")
                }
            }
            // Throttled heading diagnostic after both getRotationMatrix and trig paths
            val hNow = System.currentTimeMillis()
            if (hNow - lastDiagnosticLogTime >= 1000L) {
                lastDiagnosticLogTime = hNow
                LogHelper.logd("QiblaSensorManager", "HEADING_CURRENT: heading=${String.format("%.1f", _heading.value)}° fallback=$fallbackTriggered")
            }
        } else {
            LogHelper.logw("QiblaSensorManager", "computeHeadingFromAccelMag: missing data hasAccel=$hasAccel hasMag=$hasMag")
        }
    }

    /** Publishes a sensor-derived heading, marking the pipeline initialized on the first one. */
    private fun publishHeading(value: Float) {
        var out = value
        if (_isUsingFallbackSensors.value) {
            // Fallback-only rate clamp (the RV path must stay unclamped so its own smooth-drift
            // remains visible to the drift detector). Caps per-second movement at 25°/s: the
            // itel S685LN's magnetometer wobble peaks at 48°/s while the device sits still,
            // which previously spun the dial and stormed the alignment badge/haptic; real
            // rotation at or below 25°/s (the qibla-alignment use case) passes through.
            val now = System.currentTimeMillis()
            if (lastFallbackPublishTime != 0L && lastPublishedFallbackHeading >= 0f) {
                val dt = (now - lastFallbackPublishTime) / 1000f
                val maxDelta = 25f * dt + 2f
                val delta = shortestAngleDeg(value, lastPublishedFallbackHeading)
                if (delta > maxDelta) {
                    val signedDelta = ((value - lastPublishedFallbackHeading) % 360f + 540f) % 360f - 180f
                    val sign = if (signedDelta >= 0f) 1f else -1f
                    out = (lastPublishedFallbackHeading + sign * maxDelta + 360f) % 360f
                }
            }
            lastFallbackPublishTime = now
            lastPublishedFallbackHeading = out
        }
        if (!_headingInitialized.value) {
            _headingInitialized.value = true
        }
        _heading.value = out
    }

    private fun processRotationMatrix(rotationMatrix: FloatArray) {
        val gZ = abs(rotationMatrix[8])
        if (gZ.isNaN()) {
            LogHelper.logw("QiblaSensorManager", "gZ is NaN, skipping processing.")
            return
        }
        if (isVertical) {
            if (gZ > 0.75f) isVertical = false
        } else {
            if (gZ < 0.65f) isVertical = true
        }

        if (isVertical) {
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedMatrix)
        } else {
            System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, rotationMatrix.size)
        }

        SensorManager.getOrientation(remappedMatrix, orientation)
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
        if (!pitchDeg.isNaN()) _pitch.value = pitchDeg
        if (!rollDeg.isNaN()) _roll.value = rollDeg
        val rawAzimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
        
        if (rawAzimuth.isNaN()) {
            LogHelper.logw("QiblaSensorManager", "rawAzimuth is NaN, skipping processing.")
            return
        }

        // Apply magnetic declination compensation using cached values, updating only when location changes
        // or after a long interval (e.g., 30 minutes) to minimize heap allocations in high-frequency loop.
        val currentTime = System.currentTimeMillis()
        val currentLocationVersion = LocationProvider.getLocationVersion()
        
        if (currentLocationVersion != lastLocationVersion || currentTime - lastDeclinationUpdateMillis > 1800000L) {
            updateDeclination()
        }
        val trueAzimuth = (rawAzimuth + cachedDeclination + 360f) % 360f

        if (trueAzimuth.isNaN()) {
            LogHelper.logw("QiblaSensorManager", "trueAzimuth is NaN, skipping processing.")
            return
        }

        val azimuthRad = Math.toRadians(trueAzimuth.toDouble()).toFloat()
        val targetSin = sin(azimuthRad)
        val targetCos = cos(azimuthRad)

        if (targetSin.isNaN() || targetCos.isNaN()) {
            LogHelper.logw("QiblaSensorManager", "targetSin/Cos is NaN, skipping processing.")
            return
        }

        if (isFirstReading || filteredSin.isNaN() || filteredCos.isNaN()) {
            filteredSin = targetSin
            filteredCos = targetCos
            isFirstReading = false
        } else {
            // Smooth out jitter with low-pass filter (exponential moving average).
            // alpha=0.30 balances jitter reduction with responsiveness — the previous 0.15
            // caused the dial to appear stuck after fallback because the filter needed ~70
            // samples (2.3s) to converge to new values.
            val alpha = 0.30f
            filteredSin += (targetSin - filteredSin) * alpha
            filteredCos += (targetCos - filteredCos) * alpha
        }

        if (filteredSin.isNaN() || filteredCos.isNaN()) {
            LogHelper.logw("QiblaSensorManager", "filteredSin/Cos became NaN, resetting filter.")
            isFirstReading = true
            return
        }

        val smoothedAzimuth = (Math.toDegrees(atan2(filteredSin.toDouble(), filteredCos.toDouble())).toFloat() + 360f) % 360f

        if (smoothedAzimuth.isNaN()) {
            LogHelper.logw("QiblaSensorManager", "smoothedAzimuth is NaN, skipping update.")
            return
        }

        // Heading variance detector: if azimuth jumps >25° between consecutive readings 4+ times
        // in a row, the rotation vector sensor is producing garbage even with non-zero Z.
        // Jumps are wrap-aware (359° -> 1° is a 2° rotation, not 358°) and a single implausible
        // >90° per-frame jump (impossible at SENSOR_DELAY_GAME rates for real motion) falls back
        // immediately.
        if (lastComputedAzimuth >= 0f) {
            val headingJump = shortestAngleDeg(rawAzimuth, lastComputedAzimuth)
            if (headingJump > 90f && !fallbackTriggered) {
                LogHelper.logw("QiblaSensorManager", "Implausible heading jump of ${String.format("%.1f", headingJump)}° in a single frame. Sensor producing garbage. Falling back.")
                fallbackToAccelMag(
                    reason = "implausible heading jump (${String.format("%.1f", headingJump)}°)",
                    telemetryDetail = mapOf("lastJump" to String.format("%.1f", headingJump))
                )
                return
            }
            if (headingJump > 25f) {
                headingJumpCount++
                if (headingJumpCount >= 4 && !fallbackTriggered) {
                    LogHelper.logw("QiblaSensorManager", "Detected $headingJumpCount consecutive heading jumps >25° (${String.format("%.1f", headingJump)}°). Sensor producing garbage. Falling back.")
                    fallbackToAccelMag(
                        reason = "heading variance too high",
                        telemetryDetail = mapOf("jumpCount" to headingJumpCount, "lastJump" to String.format("%.1f", headingJump))
                    )
                    return
                }
            } else {
                headingJumpCount = 0
            }
        }
        lastComputedAzimuth = rawAzimuth

        // Throttled diagnostic logging (every 1 second)
        val now = System.currentTimeMillis()
        if (now - lastDiagnosticLogTime >= 1000L) {
            lastDiagnosticLogTime = now
            LogHelper.logd("QiblaSensorManager", "Diagnostic: rawAzimuth=${String.format("%.1f", rawAzimuth)}° trueAzimuth=${String.format("%.1f", trueAzimuth)}° smoothedAzimuth=${String.format("%.1f", smoothedAzimuth)}° isVertical=$isVertical gZ=${String.format("%.3f", gZ)} fallback=$fallbackTriggered")
        }

        publishHeading(smoothedAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR ||
            sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ||
            sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            _accuracyStatus.value = accuracy
        }
    }
}
