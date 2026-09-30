package com.mosquednd.ui.qibla

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.mosquednd.util.LogHelper

class ComposeQiblaFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE", mapOf("fragment" to "ComposeQiblaFragment"))
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE_VIEW", mapOf("fragment" to "ComposeQiblaFragment"))
        return ComposeView(requireContext()).apply {
            setContent {
                QiblaScreen()
            }
        }
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_RESUME", mapOf("fragment" to "ComposeQiblaFragment"))
        super.onResume()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_PAUSE", mapOf("fragment" to "ComposeQiblaFragment"))
        super.onPause()
    }

    override fun onDestroyView() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY_VIEW", mapOf("fragment" to "ComposeQiblaFragment"))
        super.onDestroyView()
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY", mapOf("fragment" to "ComposeQiblaFragment"))
        super.onDestroy()
    }
}
