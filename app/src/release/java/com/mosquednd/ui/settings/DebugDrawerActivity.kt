package com.mosquednd.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Release stub for DebugDrawerActivity.
 * In release builds, attempting to launch this activity or referencing it falls back safely.
 */
class DebugDrawerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }

    companion object {
        fun launch(context: Context) {
            // no-op in release
        }
    }
}
