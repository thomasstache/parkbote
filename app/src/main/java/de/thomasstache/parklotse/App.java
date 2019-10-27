package de.thomasstache.parklotse;

import android.app.Application;

import com.mapbox.mapboxsdk.Mapbox;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // configure Mapbox Token
        Mapbox.getInstance(getApplicationContext(), getString(R.string.mapbox_accessToken));
    }
}
