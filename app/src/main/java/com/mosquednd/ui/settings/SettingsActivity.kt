package com.mosquednd.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mosquednd.R
import com.mosquednd.databinding.ActivitySettingsBinding
import com.mosquednd.ui.MainActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                when (item.itemId) {
                    R.id.nav_home -> putExtra(MainActivity.EXTRA_NAVIGATE_TO, "home")
                    R.id.nav_mosques -> putExtra(MainActivity.EXTRA_NAVIGATE_TO, "mosques")
                    R.id.nav_prayer_times -> putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
                    R.id.nav_qibla -> putExtra(MainActivity.EXTRA_NAVIGATE_TO, "qibla")
                    else -> return@setOnItemSelectedListener false
                }
            }
            startActivity(intent)
            finish()
            true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
