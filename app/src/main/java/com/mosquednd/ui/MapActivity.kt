package com.mosquednd.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.Polygon
import com.mapbox.mapboxsdk.annotations.PolygonOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mosquednd.R
import com.mosquednd.service.GeofenceService
import com.mosquednd.util.LogHelper
import com.mosquednd.data.Mosque
import com.mosquednd.data.MosqueRepository
import com.mosquednd.databinding.ActivityMapBinding
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.PlaceDetector
import com.mosquednd.util.PlaceDetectorCache
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mapboxMap: MapboxMap? = null
    
    private var selectedLocation: LatLng? = null
    private var currentMarker: Marker? = null
    private var currentPolygon: Polygon? = null
    private var currentRadius: Float = 200f

    private var lastDetectionGeoPoint: LatLng? = null
    private var hasAutoDetected = false
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            enableMyLocation()
        } else {
            Toast.makeText(this, R.string.error_permissions_denied, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_CREATE", mapOf("activity" to "MapActivity"))
        super.onCreate(savedInstanceState)
        // Apply FLAG_SECURE to prevent screenshots/screen recording of sensitive map data (data security, Play Store compliance)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupMap()
        setupUI()
        
        handleIncomingMosqueLocation()
    }
    
    private fun setupMap() {
        mapView = findViewById(R.id.mapView)
        
        mapView.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(mapboxMap: MapboxMap) {
                this@MapActivity.mapboxMap = mapboxMap
                mapboxMap.setStyle("https://tiles.openfreemap.org/styles/liberty", object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(style: Style) {
                        checkLocationPermission()
                    }
                })
                
                val (defaultLat, defaultLng) = LocationProvider.getLastKnownLocation()
                val defaultCenter = LatLng(defaultLat, defaultLng)
                mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 14.0))
                
                mapboxMap.addOnMapClickListener { point ->
                    LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "MAP_CLICK", mapOf("lat" to point.latitude, "lng" to point.longitude))
                    selectLocation(point)
                    true
                }
                
                handleIncomingMosqueLocation()
            }
        })
    }
    
    private fun handleIncomingMosqueLocation() {
        if (mapboxMap == null) return
        
        val lat = intent.getDoubleExtra("mosque_lat", -1.0)
        val lng = intent.getDoubleExtra("mosque_lng", -1.0)
        val name = intent.getStringExtra("mosque_name") ?: "Mosque"
        val radius = intent.getFloatExtra("mosque_radius", 200f)
        
        if (lat != -1.0 && lng != -1.0) {
            val geoPoint = LatLng(lat, lng)
            mapboxMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(geoPoint, 16.0))
            
            selectedLocation = geoPoint
            currentRadius = radius
            
            currentMarker?.let { mapboxMap?.removeMarker(it) }
            currentMarker = mapboxMap?.addMarker(
                MarkerOptions()
                    .position(geoPoint)
                    .title(name)
            )
            
            updateCircle()
            
            binding.editMosqueName.setText(name)
            binding.layoutDetails.visibility = View.VISIBLE
            binding.textRadiusValue.text = getString(R.string.radius_meters_format, radius)
            binding.sliderRadius.progress = (radius - 50).toInt()
            
            mapView.invalidate()
        }
    }
    
    private fun setupUI() {
        binding.sliderRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentRadius = (progress + 50).toFloat()
                binding.textRadiusValue.text = getString(R.string.radius_meters_format, currentRadius)
                updateCircle()
                LogHelper.logThrottled("map_radius_change", 500L, "DEBUG", "MapActivity", "[ACTION] Event: MAP_RADIUS_CHANGED | radius=$currentRadius")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        binding.btnSave.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "Save", "activity" to "MapActivity"))
            saveMosque()
        }

        binding.btnSaveHelp.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "SaveHelp", "activity" to "MapActivity"))
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_map_save_title)
                .setMessage(R.string.help_map_save_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        
        binding.fabDetectNearby.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "DetectNearbyFab", "activity" to "MapActivity"))
            performDetection(forceRefresh = false)
        }

        binding.btnDetectNearby.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "DetectNearbyBtn", "activity" to "MapActivity"))
            performDetection(forceRefresh = false)
        }
        
        binding.btnRefreshData.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "RefreshData", "activity" to "MapActivity"))
            performDetection(forceRefresh = true)
        }
        
        binding.fabMyLocation.setOnClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "BUTTON_CLICK", mapOf("button" to "MyLocation", "activity" to "MapActivity"))
            goToMyLocation()
        }
    }
    
    private fun performDetection(forceRefresh: Boolean = false) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.error_permissions_denied), Toast.LENGTH_SHORT).show()
            checkLocationPermission()
            return
        }
        
        try {
            val target = mapboxMap?.cameraPosition?.target ?: return
            val geoPoint = LatLng(target.latitude, target.longitude)
            lastDetectionGeoPoint = geoPoint
            
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    val places = PlaceDetector.findNearbyMosques(geoPoint, forceRefresh = forceRefresh)
                    
                    // Handle granular Overpass error states
                    when (PlaceDetector.lastError) {
                        PlaceDetector.OverpassError.TIMEOUT -> {
                            Toast.makeText(this@MapActivity, R.string.error_api_timeout, Toast.LENGTH_SHORT).show()
                        }
                        PlaceDetector.OverpassError.RATE_LIMITED -> {
                            Toast.makeText(this@MapActivity, R.string.error_rate_limited, Toast.LENGTH_SHORT).show()
                        }
                        PlaceDetector.OverpassError.SERVER_UNAVAILABLE -> {
                            Toast.makeText(this@MapActivity, R.string.error_server_unavailable, Toast.LENGTH_SHORT).show()
                        }
                        PlaceDetector.OverpassError.NETWORK_IO -> {
                            Toast.makeText(this@MapActivity, R.string.error_network_io, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }

                    if (places.isEmpty()) {
                        if (PlaceDetector.lastError == PlaceDetector.OverpassError.NONE) {
                            Toast.makeText(this@MapActivity, getString(R.string.map_no_mosques_found), Toast.LENGTH_SHORT).show()
                        }
                        updateCacheInfo(geoPoint)
                        return@launch
                    }
                    
                    val names = places.map { it.name }.toTypedArray()
                    val checkedItems = BooleanArray(places.size) { false }
                    
                    MaterialAlertDialogBuilder(this@MapActivity)
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
                                        GeofenceService.enqueueAddGeofence(this@MapActivity, mosque)
                                        savedCount++
                                    }
                                }
                            }
                            if (savedCount > 0) {
                                Toast.makeText(this@MapActivity, "Saved $savedCount mosque(s)", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    
                    updateCacheInfo(geoPoint)
                } catch (e: Exception) {
                    Log.e("MapActivity", "Error detecting nearby mosques", e)
                    Toast.makeText(this@MapActivity, R.string.error_network_io, Toast.LENGTH_SHORT).show()
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Error in performDetection", e)
            binding.progressBar.visibility = View.GONE
        }
    }
    
    private fun updateCacheInfo(geoPoint: LatLng) {
        val cacheKey = PlaceDetectorCache.calculateCacheKey(geoPoint.latitude, geoPoint.longitude, 5000)
        val timestamp = PlaceDetectorCache.getCacheTimestamp(cacheKey) ?: 0L
        if (timestamp > 0) {
            val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
            binding.textCacheDate.text = getString(R.string.map_data_from, dateStr)
            binding.layoutCacheInfo.visibility = View.VISIBLE
            binding.btnRefreshData.visibility = View.VISIBLE
        } else {
            binding.layoutCacheInfo.visibility = View.GONE
            binding.btnRefreshData.visibility = View.GONE
        }
    }

    private fun checkLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            enableMyLocation()
        }
    }
    
    private fun enableMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val style = mapboxMap?.style
            if (style != null) {
                try {
                    mapboxMap?.locationComponent?.activateLocationComponent(
                        LocationComponentActivationOptions.builder(this, style).build()
                    )
                    mapboxMap?.locationComponent?.isLocationComponentEnabled = true
                } catch (e: Exception) {
                    Log.e("MapActivity", "Error enabling location component", e)
                }
            }

            if (intent.getBooleanExtra("auto_detect", false) && !hasAutoDetected) {
                hasAutoDetected = true
                Toast.makeText(this, R.string.error_waiting_for_gps, Toast.LENGTH_SHORT).show()
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        mapboxMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14.0))
                    }
                    performDetection(forceRefresh = false)
                }.addOnFailureListener {
                    Toast.makeText(this, R.string.error_api_timeout, Toast.LENGTH_SHORT).show()
                    performDetection(forceRefresh = false)
                }
            }
        }
    }
    
    private fun selectLocation(latLng: LatLng) {
        selectedLocation = latLng
        
        currentMarker?.let { mapboxMap?.removeMarker(it) }
        currentMarker = mapboxMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Mosque Location")
        )
        
        updateCircle()
        
        binding.editMosqueName.setText(getLocationName(latLng))
        binding.layoutDetails.visibility = View.VISIBLE
        binding.sliderRadius.progress = (currentRadius - 50).toInt()
        binding.textRadiusValue.text = getString(R.string.radius_meters_format, currentRadius)
        
        mapView.invalidate()
    }
    
    private fun updateCircle() {
        currentPolygon?.let { mapboxMap?.removePolygon(it) }
        selectedLocation?.let { location ->
            val circlePoints = mutableListOf<LatLng>()
            val numPoints = 64
            val radiusMeters = currentRadius.toDouble()
            val earthRadius = 6371000.0
            val latRad = Math.toRadians(location.latitude)
            val dLat = radiusMeters / earthRadius
            val dLon = radiusMeters / (earthRadius * Math.cos(latRad))
            for (i in 0 until numPoints) {
                val angle = 2 * Math.PI * i / numPoints
                val lat = location.latitude + Math.toDegrees(dLat * Math.cos(angle))
                val lon = location.longitude + Math.toDegrees(dLon * Math.sin(angle))
                circlePoints.add(LatLng(lat, lon))
            }
            
            currentPolygon = mapboxMap?.addPolygon(
                PolygonOptions()
                    .addAll(circlePoints)
                    .fillColor(0x332196F3)
                    .strokeColor(0xFF2196F3.toInt())
            )
        }
    }
    
    private fun getLocationName(latLng: LatLng): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                addresses[0].getAddressLine(0) ?: "Mosque"
            } else {
                "Mosque"
            }
        } catch (e: Exception) {
            "Mosque"
        }
    }
    
    private fun saveMosque() {
        val location = selectedLocation
        if (location == null) {
            Toast.makeText(this, getString(R.string.map_select_location_prompt), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!checkGeofenceLimit()) return
        
        val name = binding.editMosqueName.text.toString().ifEmpty { "Mosque" }
        val address = getLocationName(location)
        
        val mosque = Mosque(
            id = Mosque.generateId(),
            name = name,
            latitude = location.latitude,
            longitude = location.longitude,
            address = address,
            geofenceRadius = currentRadius
        )
        
        MosqueRepository.addMosque(mosque)
        GeofenceService.enqueueAddGeofence(this, mosque)

        Toast.makeText(this, getString(R.string.map_mosque_added), Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun checkGeofenceLimit(): Boolean {
        val count = MosqueRepository.getAllMosques().size
        return when {
            count >= 100 -> {
                Toast.makeText(this, "Geofence limit reached ($count/100)", Toast.LENGTH_SHORT).show()
                false
            }
            count >= 95 -> {
                Toast.makeText(this, "Approaching geofence limit ($count/100)", Toast.LENGTH_SHORT).show()
                true
            }
            else -> true
        }
    }
    
    private fun goToMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission()
            return
        }
        
        Toast.makeText(this, R.string.error_waiting_for_gps, Toast.LENGTH_SHORT).show()
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location: Location? ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    mapboxMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16.0))
                    selectLocation(latLng)
                } ?: run {
                    Toast.makeText(this, R.string.error_api_timeout, Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, R.string.error_api_timeout, Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.error_permissions_denied), Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    
    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_RESUME", mapOf("activity" to "MapActivity"))
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_PAUSE", mapOf("activity" to "MapActivity"))
        super.onPause()
        mapView.onPause()
    }
    
    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }
    
    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_DESTROY", mapOf("activity" to "MapActivity"))
        super.onDestroy()
        mapView.onDestroy()
    }
}
