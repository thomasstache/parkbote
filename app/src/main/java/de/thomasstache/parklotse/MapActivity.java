package de.thomasstache.parklotse;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

import butterknife.Bind;
import butterknife.ButterKnife;

public class MapActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback
{
	private static final String TAG = "MapActivity";

	private static final String PREF_FILE_KEY = "de.thomasstache.parklotse.PREFERENCE_FILE";

	public static final int DEFAULT_ZOOM = 15;
	public static final int DURATION_FAST_MS = 800;
	public static final int DURATION_SLOW_MS = 1500;

	private static final int PERMISSION_REQUEST_LOCATION = 128;

	private State state;

	@Bind(R.id.map)
	MapView mapView = null;
	private Marker parkingMarker = null;

	@Bind(R.id.fab_leave)
	FloatingActionButton fabLeave;
	@Bind(R.id.fab_parkHere)
	FloatingActionButton fabPark;
	@Bind(R.id.fab_locateMe)
	FloatingActionButton fabLocateMe;

	// indicates whether location services can be used/offered to user
	private boolean locationEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		ButterKnife.bind(this);

		final SharedPreferences preferences = getAppPreferences();

		state = State.createFromPrefs(preferences);

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

	private void setupMapView()
	{
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
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST_LOCATION)
		{
			if (grantResults.length > 0
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED)
			{
				locationEnabled = true;
				//noinspection ResourceType
				mapView.setMyLocationEnabled(true);

				updateFabVisibility();
			}
		}
	}

	private void setupParkButton()
	{
		fabPark.setOnClickListener(new View.OnClickListener()
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
	}

	private void setupLocateButton()
	{
		fabLocateMe.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final Location location = mapView.getMyLocation();
				if (location != null)
					mapView.animateCamera(createCameraUpdate(new LatLng(location), clampZoomIn(DEFAULT_ZOOM)), DURATION_SLOW_MS, null);
			}
		});
	}

	private void setupLeaveButton()
	{
		fabLeave.setOnClickListener(new View.OnClickListener()
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

		return State.saveToPrefs(state, getAppPreferences());
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

		return State.saveToPrefs(state, getAppPreferences());
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
