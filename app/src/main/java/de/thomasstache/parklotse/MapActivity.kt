package de.thomasstache.parklotse

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import de.thomasstache.parklotse.databinding.ActivityMapBinding
import kotlin.math.max
import kotlin.math.min

class MapActivity :
    AppCompatActivity(),
    OnRequestPermissionsResultCallback,
    OnMapReadyCallback {
    private lateinit var mState: State

    private lateinit var binding: ActivityMapBinding

    private lateinit var mapView: MapView
    private lateinit var fabLeave: FloatingActionButton
    private lateinit var fabPark: FloatingActionButton
    private lateinit var mapboxMap: MapboxMap

    private var parkingMarker: Marker? = null

    private lateinit var fadeInAnimation: Animation
    private lateinit var fadeOutAnimation: Animation

    // indicates whether location services can be used/offered to user
    private var isLocationEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.map
        fabLeave = binding.fabLeave
        fabPark = binding.fabParkHere

        fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.crosshair_fade_in)
        fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.crosshair_fade_out)

        val preferences = getAppPreferences()
        mState = State.createFromPrefs(preferences)

        checkLocationPermissions()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fabPark.setOnClickListener { onClickPark() }
        fabLeave.setOnClickListener { onClickLeave() }
        binding.fabLocateMe.setOnClickListener { onClickLocate() }

        updateControlsVisibility(false)
    }

    private fun getAppPreferences(): SharedPreferences = getSharedPreferences(PREF_FILE_KEY, MODE_PRIVATE)

    override fun onMapReady(map: MapboxMap) {
        mapboxMap = map

        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style: Style ->
            enableLocationComponent(style)

            mapboxMap.moveCamera(createCameraUpdate(mState.latLng, DEFAULT_ZOOM))

            if (mState.isParked) {
                markParkingLocationOnMap(mState.latLng)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        if (!isLocationEnabled) return

        mapboxMap.locationComponent.apply {
            val activationOptions =
                LocationComponentActivationOptions
                    .builder(this@MapActivity, style)
                    .build()

            activateLocationComponent(activationOptions)

            renderMode = RenderMode.COMPASS
            isLocationComponentEnabled = true
        }
    }

    /**
     * Checks for location access permissions, and eventually requests them.
     */
    private fun checkLocationPermissions() {
        val hasPermission =
            ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_LOCATION,
            )

            isLocationEnabled = false
        } else {
            // show current user location
            isLocationEnabled = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                isLocationEnabled = true
                mapboxMap.getStyle { style: Style ->
                    enableLocationComponent(style)
                }

                binding.fabLocateMe.show()
            }
        }
    }

    private fun onClickLocate() {
        mapboxMap.locationComponent.lastKnownLocation?.let {
            animateCamera(
                createCameraUpdate(LatLng(it), clampZoomIn(DEFAULT_ZOOM)),
                DURATION_SLOW_MS,
            )
        }
    }

    private fun onClickPark() {
        saveCurrentLocation()

        animateCamera(mState.latLng, currentZoom)
        updateControlsVisibility(true)
    }

    private fun onClickLeave() {
        val oldLatLng = LatLng(mState.latLng)

        clearParkingLocation()

        animateCamera(oldLatLng, currentZoom)
        updateControlsVisibility(true)
    }

    /**
     * Animates the map's camera, but ensuring the map is ready.
     */
    private fun animateCamera(
        latLng: LatLng?,
        zoom: Int,
    ) {
        val cameraUpdate = createCameraUpdate(latLng, zoom)
        animateCamera(cameraUpdate, DURATION_FAST_MS)
    }

    private fun animateCamera(
        cameraUpdate: CameraUpdate,
        duration: Int,
    ) {
        mapboxMap.animateCamera(cameraUpdate, duration, null)
    }

    /**
     * Calculates a zoom value to zoom in at least to the desired value.
     */
    private fun clampZoomOut(targetZoom: Int): Int = min(currentZoom.toDouble(), targetZoom.toDouble()).toInt()

    /**
     * Calculates a zoom value to zoom out to maximum the desired value.
     */
    private fun clampZoomIn(targetZoom: Int): Int = max(targetZoom.toDouble(), currentZoom.toDouble()).toInt()

    private val currentZoom: Int
        get() = mapboxMap.cameraPosition.zoom.toInt()

    private fun updateControlsVisibility(bAnimate: Boolean) {
        binding.crossHair.isVisible = !mState.isParked

        if (bAnimate) {
            binding.crossHair.startAnimation(if (mState.isParked) fadeOutAnimation else fadeInAnimation)

            if (mState.isParked) {
                swapFABsWithAnimation(fabLeave, fabPark)
            } else {
                swapFABsWithAnimation(fabPark, fabLeave)
            }
        } else {
            // TODO sets GONE, was INVISIBLE in Java impl.
            fabPark.isVisible = !mState.isParked
            fabLeave.isVisible = mState.isParked
        }
    }

    private fun swapFABsWithAnimation(
        fabIncoming: FloatingActionButton,
        fabOutgoing: FloatingActionButton,
    ) {
        // layer the incoming button on top of the visible one
        fabOutgoing.translationZ = -1f
        fabIncoming.translationZ = 1f

        fabIncoming.show()
        fabOutgoing.hide()
    }

    private fun updateLocateMeButtonVisibility() {
        isLocationEnabled = ActivityCompat.checkSelfPermission(
            this,
            ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        binding.fabLocateMe.isVisible = isLocationEnabled
    }

    /**
     * Update State with the current map center position. Save the state in SharedPreferences.
     * @return true if saved successfully
     */
    private fun saveCurrentLocation() {
        val latLng = mapboxMap.cameraPosition.target

        mState.isParked = true
        mState.latLng = latLng

        markParkingLocationOnMap(latLng)

        mState.saveToPrefs(getAppPreferences())
    }

    /**
     * Clear the parking location in our `State`. Save the state in SharedPreferences.
     * @return true if saved successfully
     */
    private fun clearParkingLocation() {
        mState.isParked = false
        mState.latLng = null

        parkingMarker?.let {
            mapboxMap.removeMarker(it)
        }

        mState.saveToPrefs(getAppPreferences())
    }

    /**
     * Put a Marker on the map at the specified parking location.
     * The marker object is stored.
     */
    private fun markParkingLocationOnMap(latLng: LatLng?) {
        check(::mapboxMap.isInitialized) { "lateinit var mapboxMap is not initialized!" }

        parkingMarker =
            mapboxMap.addMarker(
                MarkerOptions().position(latLng),
            )
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        updateLocateMeButtonVisibility()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    companion object {
        private const val TAG = "MapActivity"

        private const val PREF_FILE_KEY = "de.thomasstache.parklotse.PREFERENCE_FILE"

        const val DEFAULT_ZOOM: Int = 15
        const val DURATION_FAST_MS: Int = 800
        const val DURATION_SLOW_MS: Int = 1500

        private const val PERMISSION_REQUEST_LOCATION = 128

        /**
         * Returns a `CameraUpdate` to [move][MapboxMap.moveCamera] or
         * [smoothly fly][MapboxMap.animateCamera] the `MapView` to a new viewport.
         */
        private fun createCameraUpdate(
            latLng: LatLng?,
            zoom: Int,
        ): CameraUpdate {
            val cam =
                CameraPosition
                    .Builder()
                    .target(latLng)
                    .zoom(zoom.toDouble())
                    .build()

            return CameraUpdateFactory.newCameraPosition(cam)
        }
    }
}
