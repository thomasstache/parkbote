package de.thomasstache.parklotse

import android.content.SharedPreferences

import com.mapbox.mapboxsdk.geometry.LatLng

/**
 * The whole application state.
 */
class State {

    /**
     * Indicates whether we are currently parked, and { latLng} has a valid value.
     */
    var isParked: Boolean = false

    // default location at Dresden Albertplatz
    var latLng: LatLng? = LatLng(51.063, 13.746)

    companion object {
        private const val PREF_PARKED = "isParked"
        private const val PREF_LOCATION_LON = "parkedLocationLongitude"
        private const val PREF_LOCATION_LAT = "parkedLocationLatitude"

        @JvmStatic
        fun createFromPrefs(preferences: SharedPreferences): State {
            val state = State()

            state.isParked = preferences.getBoolean(PREF_PARKED, false)
            if (state.isParked) {
                // LatLng is only valid if we were parked
                val latitude = preferences.getString(PREF_LOCATION_LAT, "")
                val longitude = preferences.getString(PREF_LOCATION_LON, "")

                if (!latitude!!.isEmpty() && !longitude!!.isEmpty()) {
                    state.latLng = parseLatLng(latitude, longitude)
                }
            }

            return state
        }

        @JvmStatic
        fun saveToPrefs(state: State, preferences: SharedPreferences): Boolean {
            val editor = preferences.edit()

            val latitude: String
            val longitude: String

            if (state.latLng != null) {
                latitude = state.latLng!!.latitude.toString()
                longitude = state.latLng!!.longitude.toString()
            } else {
                latitude = ""
                longitude = ""
            }

            editor.putBoolean(PREF_PARKED, state.isParked)
                    .putString(PREF_LOCATION_LAT, latitude)
                    .putString(PREF_LOCATION_LON, longitude)

            return editor.commit()
        }

        private fun parseLatLng(latitude: String, longitude: String): LatLng {
            val lat = java.lang.Double.parseDouble(latitude)
            val lon = java.lang.Double.parseDouble(longitude)

            // TODO add error checking

            return LatLng(lat, lon)
        }
    }
}
