package com.mosquednd.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mosquednd.R
import com.mosquednd.config.RemoteConfigManager
import com.mosquednd.data.AgentLogDao
import com.mosquednd.data.MosqueDatabase
import com.mosquednd.util.AgentLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugDrawerActivity : AppCompatActivity() {

    private lateinit var dao: AgentLogDao
    private lateinit var cbForce401: CheckBox
    private lateinit var cbVerboseLogging: CheckBox
    private lateinit var tvConfigStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_drawer)

        dao = MosqueDatabase.getInstance(this).agentLogDao()

        findViewById<TextView>(R.id.telemetry_status).text = "Telemetry Server: ${AgentLogger.getServerIp(this) ?: "Disconnected"}"

        cbForce401 = findViewById(R.id.cb_force_401)
        cbVerboseLogging = findViewById(R.id.cb_verbose_logging)
        tvConfigStatus = findViewById(R.id.tv_config_status)

        // Set initial state from RemoteConfigManager
        cbForce401.isChecked = RemoteConfigManager.force401Error
        cbVerboseLogging.isChecked = RemoteConfigManager.enableVerboseLogging
        tvConfigStatus.text = "Sync interval: ${RemoteConfigManager.syncIntervalMinutes}m"

        cbForce401.setOnCheckedChangeListener { _, isChecked ->
            RemoteConfigManager.setFeatureFlag(this, "force401Error", isChecked)
            Toast.makeText(this, "Force 401 set to $isChecked", Toast.LENGTH_SHORT).show()
        }

        cbVerboseLogging.setOnCheckedChangeListener { _, isChecked ->
            RemoteConfigManager.setFeatureFlag(this, "enableVerboseLogging", isChecked)
            Toast.makeText(this, "Verbose logging set to $isChecked", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_fetch_config).setOnClickListener {
            tvConfigStatus.text = "Fetching /api/config..."
            RemoteConfigManager.fetchConfig(this) { success, error ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (success) {
                        tvConfigStatus.text = "Config fetched successfully (${RemoteConfigManager.syncIntervalMinutes}m)"
                        cbForce401.isChecked = RemoteConfigManager.force401Error
                        cbVerboseLogging.isChecked = RemoteConfigManager.enableVerboseLogging
                        Toast.makeText(this@DebugDrawerActivity, "Config updated from server", Toast.LENGTH_SHORT).show()
                    } else {
                        tvConfigStatus.text = "Fetch failed: ${error ?: "unknown"}"
                        Toast.makeText(this@DebugDrawerActivity, "Failed: ${error ?: "unknown"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_clear_data).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val logs = dao.getAllLogs()
                dao.deleteByIds(logs.map { it.id })
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DebugDrawerActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
                    loadLogs()
                }
            }
        }

        findViewById<Button>(R.id.btn_trigger_sync).setOnClickListener {
            AgentLogger.i("Sync triggered from Debug Drawer")
            Toast.makeText(this, "Sync triggered", Toast.LENGTH_SHORT).show()
        }

        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logs = dao.getAllLogs().takeLast(50)
            val logText = logs.joinToString("\n\n") { "${it.timestamp} [${it.level}]: ${it.message}" }
            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.logs_view).text = logText.ifEmpty { "No logs available" }
            }
        }
    }
}
