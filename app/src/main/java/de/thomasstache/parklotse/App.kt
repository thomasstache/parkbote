package de.thomasstache.parklotse

import android.app.Application
import com.mapbox.mapboxsdk.Mapbox

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // configure Mapbox Token
        Mapbox.getInstance(applicationContext, getString(R.string.mapbox_accessToken))
    }
}
