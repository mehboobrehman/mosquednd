package com.mosquednd.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mosquednd.R
import com.mosquednd.service.GeofenceService
import com.mosquednd.util.LogHelper
import com.mosquednd.data.Mosque
import com.mosquednd.data.MosqueRepository
import com.mosquednd.databinding.FragmentMosquesBinding
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.PlaceDetector
import kotlinx.coroutines.launch

class MosquesFragment : Fragment() {

    private var _binding: FragmentMosquesBinding? = null
    private val binding get() = _binding!!
    
    private val TAG = "MosquesFragment"

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            LogHelper.logd(TAG, "Location permission granted via launcher")
            fetchAndShowNearbyMosques()
        } else {
            LogHelper.logw(TAG, "Location permission denied via launcher")
            Toast.makeText(requireContext(), R.string.error_permissions_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMosquesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE", mapOf("fragment" to "MosquesFragment"))

        binding.btnAddMosqueEmpty.setOnClickListener { 
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "btnAddMosqueEmpty", "fragment" to "MosquesFragment"))
            hasAttemptedAutoFetch = true
            checkLocationPermissionAndFetch()
        }

        binding.btnDetectNearbyEmpty.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "btnDetectNearbyEmpty", "fragment" to "MosquesFragment"))
            hasAttemptedAutoFetch = true
            checkLocationPermissionAndFetch()
        }
    }

    private var hasAttemptedAutoFetch = false

    override fun onResume() {
        super.onResume()
        refreshMosquesList()
        
        // Auto-fetch nearby mosques on first load if permission is granted and list is empty
        if (!hasAttemptedAutoFetch && MosqueRepository.getAllMosques().isEmpty()) {
            hasAttemptedAutoFetch = true
            if (LocationProvider.hasLocationPermission()) {
                fetchAndShowNearbyMosques()
            }
        }
    }

    private fun checkLocationPermissionAndFetch() {
        if (!LocationProvider.hasLocationPermission()) {
            Toast.makeText(requireContext(), R.string.error_permissions_denied, Toast.LENGTH_SHORT).show()
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

    private fun refreshMosquesList() {
        val mosques = MosqueRepository.getAllMosques()
        if (mosques.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerMosques.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerMosques.visibility = View.VISIBLE
            // Update adapter if needed
        }
    }

    private fun fetchAndShowNearbyMosques() {
        binding.progressBar.visibility = View.VISIBLE
        LocationProvider.requestFreshLocation(requireContext()) { lat, lng ->
            val geoPoint = com.mapbox.mapboxsdk.geometry.LatLng(lat, lng)
            lifecycleScope.launch {
                try {
                    val places = PlaceDetector.findNearbyMosques(geoPoint)
                    
                    // Handle granular Overpass error states
                    when (PlaceDetector.lastError) {
                        PlaceDetector.OverpassError.TIMEOUT -> {
                            if (isAdded) Toast.makeText(requireContext(), R.string.error_api_timeout, Toast.LENGTH_SHORT).show()
                        }
                        PlaceDetector.OverpassError.RATE_LIMITED -> {
                            if (isAdded) Toast.makeText(requireContext(), R.string.error_rate_limited, Toast.LENGTH_SHORT).show()
                        }
                        PlaceDetector.OverpassError.SERVER_UNAVAILABLE -> {
                            if (isAdded) Toast.makeText(requireContext(), R.string.error_server_unavailable, Toast.LENGTH_SHORT).show()
                        }
                        PlaceDetector.OverpassError.NETWORK_IO -> {
                            if (isAdded) Toast.makeText(requireContext(), R.string.error_network_io, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }

                    if (places.isEmpty()) {
                        if (isAdded && PlaceDetector.lastError == PlaceDetector.OverpassError.NONE) {
                            Toast.makeText(requireContext(), R.string.map_no_mosques_found, Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    
                    val names = places.map { placeItem -> placeItem.name }.toTypedArray()
                    val checkedItems = BooleanArray(places.size) { false }
                    
                    if (isAdded) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.map_select_mosques_title)
                            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                                checkedItems[which] = isChecked
                            }
                            .setPositiveButton("Save") { _, _ ->
                                var savedCount = 0
                                for (i in places.indices) {
                                    if (checkedItems[i]) {
                                        val place = places[i]
                                        place.latLng?.let { mosqueLoc ->
                                            val mosque = Mosque(
                                                id = Mosque.generateId(),
                                                name = place.name,
                                                latitude = mosqueLoc.latitude,
                                                longitude = mosqueLoc.longitude,
                                                address = place.address
                                            )
                                            MosqueRepository.addMosque(mosque)
                                            GeofenceService.enqueueAddGeofence(requireContext(), mosque)
                                            savedCount++
                                        }
                                    }
                                }
                                if (savedCount > 0) {
                                    Toast.makeText(requireContext(), "Saved $savedCount mosque(s)", Toast.LENGTH_SHORT).show()
                                    refreshMosquesList()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finding nearby mosques", e)
                    if (isAdded) {
                        Toast.makeText(requireContext(), R.string.error_network_io, Toast.LENGTH_SHORT).show()
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
