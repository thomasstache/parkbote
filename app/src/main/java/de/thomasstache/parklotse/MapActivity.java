package de.thomasstache.parklotse;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.views.MapView;

public class MapActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback
{
	private static final String TAG = "MapActivity";

	private static final String PREF_FILE_KEY = "de.thomasstache.parklotse.PREFERENCE_FILE";

	private static final String PREF_PARKED = "isParked";
	private static final String PREF_LOCATION_LON = "parkedLocationLongitude";
	private static final String PREF_LOCATION_LAT = "parkedLocationLatitude";

	public static final int DEFAULT_ZOOM = 15;
	public static final int DURATION_FAST_MS = 800;
	public static final int DURATION_SLOW_MS = 1500;

	private static final int PERMISSION_REQUEST_LOCATION = 128;

	private State state;

	private MapView mapView = null;
	private Marker parkingMarker = null;

	private FloatingActionButton fabLeave;
	private FloatingActionButton fabPark;
	private FloatingActionButton fabLocateMe;
	// indicates whether location services can be used/offered to user
	private boolean locationEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		final SharedPreferences preferences = getAppPreferences();

		state = loadStateFromPrefs(preferences);

		setupMapView();

		mapView.moveCamera(createCameraUpdate(state.latLng, DEFAULT_ZOOM));

		mapView.onCreate(savedInstanceState);

		setupParkButton();
		setupLeaveButton();
		setupLocateButton();

		updateFabVisibility();

		if (state.isParked)
		{
			markParkingLocationOnMap(state.latLng);
		}
	}

	private SharedPreferences getAppPreferences()
	{
		return getSharedPreferences(PREF_FILE_KEY, MODE_PRIVATE);
	}

	private State loadStateFromPrefs(SharedPreferences preferences)
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

	private boolean saveStateToPrefs(SharedPreferences preferences)
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

	private void setupMapView()
	{
		mapView = (MapView) findViewById(R.id.map);
		mapView.setStyleUrl(Style.MAPBOX_STREETS);

		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);

			mapView.setMyLocationEnabled(false);
			this.locationEnabled = false;
		}
		else
		{
			// show current user location
			mapView.setMyLocationEnabled(true);
			this.locationEnabled = true;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST_LOCATION)
		{
			if (grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED)
			{
				// TODO: 14.03.2016 test if this works on Marshmallow
				locationEnabled = true;
				//noinspection ResourceType
				mapView.setMyLocationEnabled(true);

				updateFabVisibility();
			}
		}
	}

	private void setupParkButton()
	{
		final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_parkHere);
		fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final boolean bOk = saveCurrentLocation();

				Snackbar.make(v, bOk ? "Successfully parked." : "Location not saved...", Snackbar.LENGTH_LONG)
				        .setAction("Action", null)
				        .show();

				if (bOk)
				{
					mapView.animateCamera(createCameraUpdate(state.latLng, clampZoomIn(DEFAULT_ZOOM + 1)), DURATION_FAST_MS, null);
					updateFabVisibility();
				}
			}
		});

		this.fabPark = fab;
	}

	private void setupLocateButton()
	{
		final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_locateMe);
		fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.white)));
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final Location location = mapView.getMyLocation();
				if (location != null)
					mapView.animateCamera(createCameraUpdate(new LatLng(location), clampZoomIn(DEFAULT_ZOOM)), DURATION_SLOW_MS, null);
			}
		});

		this.fabLocateMe = fab;
	}

	private void setupLeaveButton()
	{
		final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_leave);
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final LatLng oldLatLng = new LatLng(state.latLng);

				boolean bOk = clearParkingLocation();

				Snackbar.make(v, bOk ? "Parked out." : "Update not saved...", Snackbar.LENGTH_LONG)
				        .show();

				if (bOk)
				{
					mapView.animateCamera(createCameraUpdate(oldLatLng, clampZoomOut(DEFAULT_ZOOM)), DURATION_FAST_MS, null);
					updateFabVisibility();
				}
			}
		});

		this.fabLeave = fab;
	}

	/**
	 * Calculates a zoom value to zoom in at least to the desired value.
	 */
	private int clampZoomOut(final int targetZoom)
	{
		return Math.min((int) mapView.getZoom(), targetZoom);
	}

	/**
	 * Calculates a zoom value to zoom out to maximum the desired value.
	 */
	private int clampZoomIn(final int targetZoom)
	{
		return Math.max(targetZoom, (int) mapView.getZoom());
	}

	private void updateFabVisibility()
	{
		fabPark.setVisibility(!state.isParked ? View.VISIBLE : View.GONE);
		fabLeave.setVisibility(state.isParked ? View.VISIBLE : View.GONE);

		fabLocateMe.setVisibility(locationEnabled ? View.VISIBLE : View.GONE);
	}

	/**
	 * Update State with the current map center position. Save the state in SharedPreferences.
	 * @return true if saved successfully
	 */
	private boolean saveCurrentLocation()
	{
		final LatLng latLng = mapView.getLatLng();

		this.state.isParked = true;
		this.state.latLng = latLng;

		markParkingLocationOnMap(latLng);

		return saveStateToPrefs(getAppPreferences());
	}

	/**
	 * Clear the parking location in our {@code State}. Save the state in SharedPreferences.
	 * @return true if saved successfully
	 */
	private boolean clearParkingLocation()
	{
		state.isParked = false;
		state.latLng = null;

		if (parkingMarker != null)
			mapView.removeMarker(parkingMarker);

		return saveStateToPrefs(getAppPreferences());
	}

	private LatLng parseLatLng(String latitude, String longitude)
	{
		final double lat = Double.parseDouble(latitude);
		final double lon = Double.parseDouble(longitude);

		// TODO add error checking

		return new LatLng(lat, lon);
	}

	/**
	 * Returns a {@code CameraUpdate} to {@link MapView#moveCamera(CameraUpdate) move} or
	 * {@link MapView#animateCamera(CameraUpdate) smoothly fly} the {@code MapView} to a new viewport.
	 */
	private CameraUpdate createCameraUpdate(LatLng latLng, int zoom)
	{
		CameraPosition cam = new CameraPosition.Builder()
				.target(latLng)
				.zoom(zoom)
				.build();

		return CameraUpdateFactory.newCameraPosition(cam);
	}

	/**
	 * Put a Marker on the map at the specified parking location.
	 * The marker object is stored.
	 */
	private void markParkingLocationOnMap(LatLng latLng)
	{
		parkingMarker = mapView.addMarker(new MarkerOptions()
                                  .position(latLng));
	}

	/**
	 * The whole application state.
	 */
	private static class State
	{
		public boolean isParked = false;

		public LatLng latLng = new LatLng(51.063, 13.746);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		mapView.onStart();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		mapView.onStop();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mapView.onPause();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mapView.onResume();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mapView.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		mapView.onSaveInstanceState(outState);
	}
}
