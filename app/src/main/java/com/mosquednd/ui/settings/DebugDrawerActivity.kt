package com.mosquednd.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mosquednd.R
import com.mosquednd.data.AgentLogDao
import com.mosquednd.data.MosqueDatabase
import com.mosquednd.util.AgentLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugDrawerActivity : AppCompatActivity() {

    private lateinit var dao: AgentLogDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_drawer)

        dao = MosqueDatabase.getInstance(this).agentLogDao()

        findViewById<TextView>(R.id.telemetry_status).text = "Telemetry Server: ${AgentLogger.getServerIp(this) ?: "Disconnected"}"
        
        findViewById<Button>(R.id.btn_clear_data).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                // Delete all logs
                // Note: The DAO didn't have a clear all, so we'll delete based on logic or a simple trick.
                // Assuming AgentLogDao has no delete all, I'll delete all logs.
                // Re-reading DAO, no deleteAll. I will add one if needed, but I can fetch all and delete by IDs.
                val logs = dao.getAllLogs()
                dao.deleteByIds(logs.map { it.id })
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DebugDrawerActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
                    loadLogs()
                }
            }
        }

        findViewById<Button>(R.id.btn_trigger_sync).setOnClickListener {
            // Need to flush logs. AgentLogger.flushPendingLogs() is private.
            // I should use AgentLogger.i() or similar or just check connectivity.
            // Wait, AgentLogger.i() internally calls logToAgent which flushes if server is there.
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
