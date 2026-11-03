package com.mosquednd.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicReference

/**
 * TelemetryLifecycleCallbacks: Tracks application and activity lifecycle callbacks,
 * current active screen/activity, and state transitions for debugging and telemetry.
 */
object TelemetryLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    private val currentActivityRef = AtomicReference<Activity?>()

    fun getCurrentActivity(): Activity? = currentActivityRef.get()

    val currentScreenName: String?
        get() = currentActivityRef.get()?.javaClass?.simpleName

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef.set(activity)
        AgentLogger.d("Activity created: ${activity.javaClass.simpleName}", "Lifecycle")
        emitLifecycleEvent("CREATED", activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef.set(activity)
        emitLifecycleEvent("STARTED", activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef.set(activity)
        AgentLogger.i("Activity resumed (Screen): ${activity.javaClass.simpleName}", "Lifecycle")
        emitLifecycleEvent("RESUMED", activity)
    }

    override fun onActivityPaused(activity: Activity) {
        emitLifecycleEvent("PAUSED", activity)
    }

    override fun onActivityStopped(activity: Activity) {
        emitLifecycleEvent("STOPPED", activity)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef.get() == activity) {
            currentActivityRef.set(null)
        }
        AgentLogger.d("Activity destroyed: ${activity.javaClass.simpleName}", "Lifecycle")
        emitLifecycleEvent("DESTROYED", activity)
    }

    private fun emitLifecycleEvent(state: String, activity: Activity) {
        val screenName = activity.javaClass.simpleName
        AgentLogger.emitEnvelope("status", mapOf(
            "level" to "INFO",
            "message" to "Lifecycle transition: $screenName is $state",
            "tag" to "Lifecycle",
            "metrics" to mapOf(
                "screen" to screenName,
                "lifecycleState" to state
            )
        ))
    }
}
