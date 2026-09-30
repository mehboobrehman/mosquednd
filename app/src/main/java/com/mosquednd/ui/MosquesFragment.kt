package com.mosquednd.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mosquednd.R
import com.mosquednd.data.Mosque
import com.mosquednd.data.MosqueRepository
import com.mosquednd.databinding.FragmentMosquesBinding
import com.mosquednd.service.GeofenceService
import com.mosquednd.util.LogHelper

class MosquesFragment : Fragment() {

    private var _binding: FragmentMosquesBinding? = null
    private val binding get() = _binding!!
    private lateinit var mosqueAdapter: MosqueAdapter

    companion object {
        private const val TAG = "MosquesFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE", mapOf("fragment" to "MosquesFragment"))
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE_VIEW", mapOf("fragment" to "MosquesFragment"))
        _binding = FragmentMosquesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Apply FLAG_SECURE to prevent screenshots/screen recording of sensitive mosque details (data security, Play Store compliance)
        requireActivity().window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        mosqueAdapter = MosqueAdapter(
            onDeleteClick = { mosque -> 
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "DELETE_MOSQUE_INIT", mapOf("mosqueId" to mosque.id, "name" to mosque.name))
                confirmDeleteMosque(mosque) 
            },
            onItemClick = { mosque -> 
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "MOSQUE_ITEM_CLICK", mapOf("mosqueId" to mosque.id, "name" to mosque.name))
                openMosqueOnMap(mosque) 
            }
        )

        binding.recyclerMosques.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMosques.adapter = mosqueAdapter

        binding.fabAddMosque.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "fabAddMosque", "fragment" to "MosquesFragment"))
            launchMapActivity() 
        }
        binding.btnAddMosqueEmpty.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "btnAddMosqueEmpty", "fragment" to "MosquesFragment"))
            launchMapActivity() 
        }
        binding.btnDetectNearbyHeader.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "btnDetectNearbyHeader", "fragment" to "MosquesFragment"))
            launchMapActivityWithAutoDetect() 
        }
        binding.btnDetectNearbyEmpty.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "btnDetectNearbyEmpty", "fragment" to "MosquesFragment"))
            launchMapActivityWithAutoDetect() 
        }
        binding.layoutInstructionsToggle.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "layoutInstructionsToggle", "fragment" to "MosquesFragment"))
            toggleInstructions() 
        }
    }

    private fun launchMapActivity() {
        val intent = Intent(requireContext(), MapActivity::class.java)
        startActivity(intent)
    }

    private fun launchMapActivityWithAutoDetect() {
        val intent = Intent(requireContext(), MapActivity::class.java).apply {
            putExtra("auto_detect", true)
        }
        startActivity(intent)
    }

    private var instructionsExpanded = false

    private fun toggleInstructions() {
        instructionsExpanded = !instructionsExpanded
        val visibility = if (instructionsExpanded) View.VISIBLE else View.GONE
        binding.textInstructions.visibility = visibility
        binding.btnInstructionsToggle.rotation = if (instructionsExpanded) 180f else 0f
        binding.btnInstructionsToggle.contentDescription = getString(
            if (instructionsExpanded) R.string.mosques_instructions_collapse_desc else R.string.mosques_instructions_expand_desc
        )
        binding.textInstructions.announceForAccessibility(
            getString(if (instructionsExpanded) R.string.mosques_instructions_expanded else R.string.mosques_instructions_collapsed)
        )
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_RESUME", mapOf("fragment" to "MosquesFragment"))
        super.onResume()
        loadMosques()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_PAUSE", mapOf("fragment" to "MosquesFragment"))
        super.onPause()
    }

    override fun onDestroyView() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY_VIEW", mapOf("fragment" to "MosquesFragment"))
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY", mapOf("fragment" to "MosquesFragment"))
        super.onDestroy()
    }

    private fun loadMosques() {
        val mosques = MosqueRepository.getAllMosques()
        mosqueAdapter.submitList(mosques)
        val isEmpty = mosques.isEmpty()
        binding.recyclerMosques.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        // FAB always visible — persistent primary action per Material Design
        instructionsExpanded = false
        binding.textInstructions.visibility = View.GONE
        binding.btnInstructionsToggle.rotation = 0f
        binding.btnInstructionsToggle.contentDescription = getString(R.string.mosques_instructions_expand_desc)
    }

    private fun confirmDeleteMosque(mosque: Mosque) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_mosque_title)
            .setMessage(getString(R.string.remove_mosque_message, mosque.name))
            .setPositiveButton(R.string.remove) { _: android.content.DialogInterface, _: Int ->
                MosqueRepository.removeMosque(mosque.id)
                GeofenceService.enqueueRemoveGeofence(requireContext(), mosque.id)
                loadMosques()
                Toast.makeText(requireContext(), getString(R.string.mosque_removed), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openMosqueOnMap(mosque: Mosque) {
        val intent = Intent(requireContext(), MapActivity::class.java).apply {
            putExtra("mosque_lat", mosque.latitude)
            putExtra("mosque_lng", mosque.longitude)
            putExtra("mosque_name", mosque.name)
            putExtra("mosque_radius", mosque.geofenceRadius)
        }
        startActivity(intent)
    }
}
