package com.mosquednd.geofence

import android.location.Location
import android.util.Log

/**
 * Pre-processing layer for location data before it reaches the geofence state machine.
 *
 * Provides three stages of filtering:
 * 1. **Accuracy Gate** â€” rejects locations with horizontal accuracy worse than the threshold.
 * 2. **Moving Average Filter** â€” smooths location samples using a rolling weighted centroid.
 * 3. **Min Consecutive Consensus** â€” requires multiple samples to agree before a fence
 *    transition is considered stable.
 *
 * All public methods are [Synchronized] since locations may arrive from different threads
 * (e.g. [GeofenceBroadcastReceiver] vs [LocationMonitorService]).
 *
 * @param config tunable parameters for all filter stages; defaults to [GeofenceConfig] defaults
 */
class GeofenceFilter(private val config: GeofenceConfig = GeofenceConfig()) {

    companion object {
        private const val TAG = "GeofenceFilter"

        @Volatile
        private var instance: GeofenceFilter? = null

        fun getInstance(): GeofenceFilter {
            return instance ?: synchronized(this) {
                instance ?: GeofenceFilter().also { instance = it }
            }
        }
    }

    // Rolling window of recent location samples accepted by the accuracy gate.
    private val window = mutableListOf<Location>()

    // Per-fence consensus tracking: fenceId -> nullable Boolean ring buffer.
    private val fenceHistory = mutableMapOf<String, Array<Boolean?>>()

    /**
     * Filter a raw location through the accuracy gate and moving average smoother.
     *
     * 1. If [location.accuracy] exceeds [GeofenceConfig.gpsAccuracyThresholdMeters],
     *    the sample is rejected and `null` is returned.
     * 2. The sample is added to the rolling window (oldest evicted if at capacity).
     * 3. A weighted centroid is computed from all samples currently in the window:
     *    - Most recent sample weight = 0.5
     *    - All previous samples share the remaining 0.5 equally
     *
     * @param location the raw [Location] from GPS or fused provider
     * @return a smoothed [Location] with averaged coordinates, or `null` if accuracy was too poor
     */
    @Synchronized
    fun filter(location: Location): Location? {
        // Stage 1: Accuracy Gate
        if (location.accuracy > config.gpsAccuracyThresholdMeters) {
            Log.w(TAG, "Rejected: accuracy=${location.accuracy} threshold=${config.gpsAccuracyThresholdMeters}")
            return null
        }

        // Stage 2: Rolling window
        window.add(Location(location))
        if (window.size > config.noiseFilterWindowSize) {
            window.removeAt(0)
        }

        // Stage 3: Weighted centroid
        val smoothed = computeWeightedCentroid()
        Log.d(TAG, "Filtered: (${smoothed.latitude}, ${smoothed.longitude}) accuracy=${smoothed.accuracy} " +
                "from ${window.size} samples")
        return smoothed
    }

    /**
     * Check whether enough consecutive location samples agree on a fence state
     * to declare a stable transition.
     *
     * Maintains a per-fence ring buffer of the last [GeofenceConfig.noiseFilterWindowSize]
     * distance results. Returns `true` when [GeofenceConfig.minConsecutiveSamples]
     * consecutive entries (counting backwards from the latest) all match [isInside].
     *
     * Call this **after** [filter] for each fence whose proximity is being evaluated.
     *
     * @param fenceId unique identifier for the geofence
     * @param isInside whether the current filtered location is inside this fence
     * @return `true` when consensus is reached and a transition may be fired
     */
    @Synchronized
    fun checkConsensus(fenceId: String, isInside: Boolean): Boolean {
        val history = fenceHistory.getOrPut(fenceId) {
            arrayOfNulls(config.noiseFilterWindowSize)
        }

        // Shift all entries left, append the latest result at the end
        for (i in 0 until history.size - 1) {
            history[i] = history[i + 1]
        }
        history[history.size - 1] = isInside

        // Count consecutive entries matching isInside, starting from the most recent
        var consecutive = 0
        for (i in history.size - 1 downTo 0) {
            val value = history[i] ?: break
            if (value == isInside) {
                consecutive++
            } else {
                break
            }
        }

        val consensus = consecutive >= config.minConsecutiveSamples
        Log.d(TAG, "Fence $fenceId: inside=$isInside consecutive=$consecutive " +
                "threshold=${config.minConsecutiveSamples} consensus=$consensus")
        return consensus
    }

    /**
     * Computes a weighted centroid from all samples currently in the rolling window.
     *
     * Weighting scheme:
     * - Most recent sample: 0.5
     * - All older samples share 0.5 equally
     *
     * Accuracy is set to the best (lowest) accuracy in the window.
     */
    private fun computeWeightedCentroid(): Location {
        val size = window.size
        if (size == 1) {
            return Location(window[0])
        }

        val latestLat = window.last().latitude
        val latestLng = window.last().longitude

        val latestWeight = 0.5
        val olderWeight = 0.5 / (size - 1)

        var avgLat = latestLat * latestWeight
        var avgLng = latestLng * latestWeight

        for (i in 0 until size - 1) {
            avgLat += window[i].latitude * olderWeight
            avgLng += window[i].longitude * olderWeight
        }

        val result = Location(window.last())
        result.latitude = avgLat
        result.longitude = avgLng
        // Report the best accuracy seen across the window
        result.accuracy = window.minOf { it.accuracy }
        return result
    }
}
