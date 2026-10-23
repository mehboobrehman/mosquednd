package com.mosquednd.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mosquednd.R
import com.mosquednd.data.Mosque
import com.mosquednd.data.MosqueRepository
import com.mosquednd.databinding.FragmentMosquesBinding
import com.mosquednd.service.GeofenceService
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.LogHelper
import com.mosquednd.util.PlaceDetector
import kotlinx.coroutines.launch

class MosquesFragment : Fragment() {

    private var _binding: FragmentMosquesBinding? = null
    private val binding get() = _binding!!
    private lateinit var mosqueAdapter: MosqueAdapter
    private var hasAttemptedAutoDetect = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            fetchAndShowNearbyMosques()
        } else {
            Toast.makeText(requireContext(), R.string.map_location_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

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
            hasAttemptedAutoDetect = false
            checkAndPerformAutoDiscovery()
        }
        binding.btnDetectNearbyEmpty.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "btnDetectNearbyEmpty", "fragment" to "MosquesFragment"))
            hasAttemptedAutoDetect = false
            checkAndPerformAutoDiscovery()
        }
        binding.layoutInstructionsToggle.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "layoutInstructionsToggle", "fragment" to "MosquesFragment"))
            toggleInstructions() 
        }
    }

    private fun checkAndPerformAutoDiscovery() {
        if (hasAttemptedAutoDetect) return
        hasAttemptedAutoDetect = true

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fetchAndShowNearbyMosques()
        }
    }

    private fun fetchAndShowNearbyMosques() {
        binding.progressBar.visibility = View.VISIBLE
        LocationProvider.requestFreshLocation { lat, lng ->
            val geoPoint = LatLng(lat, lng)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val places = PlaceDetector.findNearbyMosques(geoPoint, forceRefresh = false)
                    if (!isAdded) return@launch
                    
                    if (places.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.map_no_mosques_found, Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val names = places.map { it.name }.toTypedArray()
                    val checkedItems = BooleanArray(places.size) { false }

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.map_select_mosques_title)
                        .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                            checkedItems[which] = isChecked
                        }
                        .setPositiveButton("Save") { _, _ ->
                            val currentCount = MosqueRepository.getAllMosques().size
                            if (currentCount >= 100) {
                                Toast.makeText(requireContext(), "Geofence limit reached ($currentCount/100)", Toast.LENGTH_SHORT).show()
                                return@setPositiveButton
                            }

                            var savedCount = 0
                            for (i in places.indices) {
                                if (checkedItems[i]) {
                                    val place = places[i]
                                    place.latLng?.let { mosqueLocation ->
                                        val mosque = Mosque(
                                            id = Mosque.generateId(),
                                            name = place.name,
                                            latitude = mosqueLocation.latitude,
                                            longitude = mosqueLocation.longitude,
                                            address = place.address ?: ""
                                        )
                                        MosqueRepository.addMosque(mosque)
                                        GeofenceService.enqueueAddGeofence(requireContext(), mosque)
                                        savedCount++
                                    }
                                }
                            }

                            if (savedCount > 0) {
                                Toast.makeText(requireContext(), "Saved $savedCount mosque(s)", Toast.LENGTH_SHORT).show()
                                loadMosques()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error finding nearby mosques", e)
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error finding nearby mosques: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    if (isAdded) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun launchMapActivity() {
        val intent = Intent(requireContext(), MapActivity::class.java)
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
        
        if (isEmpty) {
            checkAndPerformAutoDiscovery()
        }

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
