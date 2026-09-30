package com.mosquednd.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.ui.MainActivity
import com.mosquednd.util.LogHelper

class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OnboardingActivity"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"

        fun start(context: Context) {
            val intent = Intent(context, OnboardingActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var dotIndicator: LinearLayout
    private lateinit var btnSkip: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var bottomBar: LinearLayout

    private val slideData = listOf(
        SlideData(
            titleRes = R.string.onboarding_prayer_title,
            descRes = R.string.onboarding_prayer_desc,
            iconRes = R.drawable.ic_home
        ),
        SlideData(
            titleRes = R.string.onboarding_qibla_title,
            descRes = R.string.onboarding_qibla_desc,
            iconRes = R.drawable.ic_compass
        ),
        SlideData(
            titleRes = R.string.onboarding_mosque_title,
            descRes = R.string.onboarding_mosque_desc,
            iconRes = R.drawable.ic_mosque
        ),
        SlideData(
            titleRes = R.string.onboarding_dnd_title,
            descRes = R.string.onboarding_dnd_desc,
            iconRes = R.drawable.ic_notification
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_CREATE", mapOf("activity" to "OnboardingActivity"))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewFlipper = findViewById(R.id.view_flipper)
        dotIndicator = findViewById(R.id.dot_indicator)
        btnSkip = findViewById(R.id.btn_skip)
        btnNext = findViewById(R.id.btn_next)
        bottomBar = findViewById(R.id.bottom_bar)

        inflateSlides()
        buildDotIndicators()
        updateButtons()
        setupSwipeNavigation()

        btnSkip.setOnClickListener {
            Log.d(TAG, "Skip tapped — jumping to last slide")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "Skip", "activity" to "OnboardingActivity"))
            goToSlide(slideData.lastIndex)
        }

        btnNext.setOnClickListener {
            Log.d(TAG, "Next tapped — current index: ${viewFlipper.displayedChild}")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "Next", "activity" to "OnboardingActivity", "currentIndex" to viewFlipper.displayedChild))
            if (viewFlipper.displayedChild >= slideData.lastIndex) {
                finishOnboarding()
            } else {
                viewFlipper.showNext()
                updateButtons()
                updateDots()
            }
            markSeen()
        }
    }

    private fun inflateSlides() {
        val inflater = LayoutInflater.from(this)
        for (data in slideData) {
            val slide = inflater.inflate(R.layout.slide_onboarding, viewFlipper, false)
            slide.findViewById<TextView>(R.id.slide_title).setText(data.titleRes)
            slide.findViewById<TextView>(R.id.slide_desc).setText(data.descRes)
            slide.findViewById<ImageView>(R.id.illustration_icon).setImageResource(data.iconRes)
            viewFlipper.addView(slide)
        }
    }

    private fun buildDotIndicators() {
        dotIndicator.removeAllViews()
        val dotSize = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        val dotMargin = resources.getDimensionPixelSize(R.dimen.onboarding_dot_margin)
        val dotActive = R.drawable.dot_active
        val dotInactive = R.drawable.dot_inactive

        repeat(slideData.size) { index ->
            val dot = ImageView(this).apply {
                setImageResource(if (index == 0) dotActive else dotInactive)
                val params = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                layoutParams = params
                contentDescription = if (index == 0) {
                    getString(R.string.onboarding_page_desc, index + 1, slideData.size)
                } else {
                    null
                }
            }
            dotIndicator.addView(dot)
        }
    }

    private fun updateDots() {
        val currentIndex = viewFlipper.displayedChild
        for (i in 0 until dotIndicator.childCount) {
            val dot = dotIndicator.getChildAt(i) as ImageView
            dot.setImageResource(if (i == currentIndex) R.drawable.dot_active else R.drawable.dot_inactive)
            dot.contentDescription = if (i == currentIndex) {
                getString(R.string.onboarding_page_desc, i + 1, slideData.size)
            } else {
                null
            }
        }
    }

    private fun setupSwipeNavigation() {
        var downX = 0f
        var downY = 0f
        var downTime = 0L

        findViewById<View>(R.id.onboarding_root).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    false // let children handle the rest of the gesture
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val dt = (System.currentTimeMillis() - downTime).coerceAtLeast(1)
                    val vx = dx / dt * 1000 // px/s
                    val vy = dy / dt * 1000 // px/s

                    // Only trigger on horizontal-dominant, fast enough, far enough
                    if (kotlin.math.abs(vx) > kotlin.math.abs(vy)
                        && kotlin.math.abs(vx) >= 1000
                        && kotlin.math.abs(dx) >= 120
                    ) {
                        if (vx < 0) {
                            viewFlipper.showNext()
                        } else {
                            viewFlipper.showPrevious()
                        }
                        updateButtons()
                        updateDots()
                    }
                    false // return false so children still consume vertical scrolls
                }
                else -> false
            }
        }
    }

    private fun updateButtons() {
        val isLastSlide = viewFlipper.displayedChild >= slideData.lastIndex
        btnNext.text = if (isLastSlide) getString(R.string.onboarding_done) else getString(R.string.onboarding_next)
        btnSkip.visibility = if (isLastSlide) View.GONE else View.VISIBLE
    }

    private fun goToSlide(index: Int) {
        val direction = if (index > viewFlipper.displayedChild) {
            viewFlipper.outAnimation
        } else {
            viewFlipper.inAnimation
        }
        viewFlipper.setOutAnimation(direction)
        viewFlipper.setInAnimation(direction)
        viewFlipper.displayedChild = index
        updateButtons()
        updateDots()
    }

    private fun markSeen() {
        try {
            MosqueDNDApp.prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).commit()
            Log.d(TAG, "onboarding_seen set to true")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist onboarding_seen", e)
        }
    }

    private fun finishOnboarding() {
        markSeen()
        Log.d(TAG, "Onboarding complete — returning to MainActivity")
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_RESUME", mapOf("activity" to "OnboardingActivity"))
        super.onResume()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_PAUSE", mapOf("activity" to "OnboardingActivity"))
        super.onPause()
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_DESTROY", mapOf("activity" to "OnboardingActivity"))
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Treat back press during onboarding as "skip to done"
        Log.d(TAG, "Back pressed during onboarding — finishing")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BACK_PRESSED", mapOf("activity" to "OnboardingActivity"))
        finishOnboarding()
    }
}

private data class SlideData(
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int
)