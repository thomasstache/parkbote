package de.thomasstache.parklotse

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mapbox.mapboxsdk.geometry.LatLng
import java.lang.Double.parseDouble

/**
 * The whole application state.
 */
class State {
    /**
     * Indicates whether we are currently parked, and [latLng] has a valid value.
     */
    var isParked: Boolean = false

    // default location at Dresden Albertplatz
    var latLng: LatLng? = LatLng(51.063, 13.746)

    fun saveToPrefs(preferences: SharedPreferences) {
        val latitude: String = latLng?.latitude?.toString() ?: ""
        val longitude: String = latLng?.longitude?.toString() ?: ""

        preferences.edit {
            putBoolean(PREF_PARKED, isParked)
            putString(PREF_LOCATION_LAT, latitude)
            putString(PREF_LOCATION_LON, longitude)
        }
    }

    companion object {
        private const val PREF_PARKED = "isParked"
        private const val PREF_LOCATION_LON = "parkedLocationLongitude"
        private const val PREF_LOCATION_LAT = "parkedLocationLatitude"

        fun createFromPrefs(preferences: SharedPreferences): State {
            val state = State()

            state.isParked = preferences.getBoolean(PREF_PARKED, false)
            if (state.isParked) {
                // LatLng is only valid if we were parked
                val latitude = preferences.getString(PREF_LOCATION_LAT, "")!!
                val longitude = preferences.getString(PREF_LOCATION_LON, "")!!

                if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
                    state.latLng = parseLatLng(latitude, longitude)
                }
            }

            return state
        }

        private fun parseLatLng(
            latitude: String,
            longitude: String,
        ): LatLng {
            val lat = parseDouble(latitude)
            val lon = parseDouble(longitude)

            // TODO add error checking

            return LatLng(lat, lon)
        }
    }
}
