package de.thomasstache.parklotse;

import android.content.SharedPreferences;

import com.mapbox.mapboxsdk.geometry.LatLng;

/**
 * The whole application state.
 */
public class State
{
	private static final String PREF_PARKED = "isParked";
	private static final String PREF_LOCATION_LON = "parkedLocationLongitude";
	private static final String PREF_LOCATION_LAT = "parkedLocationLatitude";

	public boolean isParked = false;

	// default location at Dresden Albertplatz
	public LatLng latLng = new LatLng(51.063, 13.746);

	public static State createFromPrefs(SharedPreferences preferences)
	{
		final State state = new State();

		state.isParked = preferences.getBoolean(PREF_PARKED, false);
		if (state.isParked)
		{
			// LatLng is only valid if we were parked
			String latitude = preferences.getString(PREF_LOCATION_LAT, "");
			String longitude = preferences.getString(PREF_LOCATION_LON, "");

			if (!latitude.isEmpty() && !longitude.isEmpty())
			{
				state.latLng = parseLatLng(latitude, longitude);
			}
		}

		return state;
	}

	public static boolean saveToPrefs(State state, SharedPreferences preferences)
	{
		final SharedPreferences.Editor editor = preferences.edit();

		final String latitude;
		final String longitude;

		if (state.latLng != null)
		{
			latitude = Double.toString(state.latLng.getLatitude());
			longitude = Double.toString(state.latLng.getLongitude());
		}
		else
		{
			latitude = "";
			longitude = "";
		}

		editor.putBoolean(PREF_PARKED, state.isParked)
		      .putString(PREF_LOCATION_LAT, latitude)
		      .putString(PREF_LOCATION_LON, longitude);

		return editor.commit();
	}

	private static LatLng parseLatLng(String latitude, String longitude)
	{
		final double lat = Double.parseDouble(latitude);
		final double lon = Double.parseDouble(longitude);

		// TODO add error checking

		return new LatLng(lat, lon);
	}
}
