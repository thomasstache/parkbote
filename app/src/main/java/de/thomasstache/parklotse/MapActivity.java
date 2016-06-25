package de.thomasstache.parklotse;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MapActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback, OnMapReadyCallback
{
	private static final String TAG = "MapActivity";

	private static final String PREF_FILE_KEY = "de.thomasstache.parklotse.PREFERENCE_FILE";

	public static final int DEFAULT_ZOOM = 15;
	public static final int DURATION_FAST_MS = 800;
	public static final int DURATION_SLOW_MS = 1500;

	private static final int PERMISSION_REQUEST_LOCATION = 128;

	private State mState;

	@Bind(R.id.map)
	MapView mMapView = null;
	MapboxMap mMap = null;
	private Marker parkingMarker = null;

	@Bind(R.id.fab_leave)
	FloatingActionButton fabLeave;
	@Bind(R.id.fab_parkHere)
	FloatingActionButton fabPark;
	@Bind(R.id.fab_locateMe)
	FloatingActionButton fabLocateMe;

	@Bind(R.id.cross_hair)
	ImageView ivCrossHair;

	private Animation fadeInAnimation;
	private Animation fadeOutAnimation;

	// indicates whether location services can be used/offered to user
	private boolean mLocationEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.crosshair_fade_in);
		fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.crosshair_fade_out);

		ButterKnife.bind(this);

		final SharedPreferences preferences = getAppPreferences();

		mState = State.createFromPrefs(preferences);

		checkLocationPermissions();

		mMapView.onCreate(savedInstanceState);
		mMapView.getMapAsync(this);

		setupParkButton();
		setupLeaveButton();
		setupLocateButton();

		updateControlsVisibility(false);
		updateLocateMeButtonVisibility();
	}

	private SharedPreferences getAppPreferences()
	{
		return getSharedPreferences(PREF_FILE_KEY, MODE_PRIVATE);
	}

	@Override
	public void onMapReady(@NonNull MapboxMap mapboxMap)
	{
		mMap = mapboxMap;

		mMap.moveCamera(createCameraUpdate(mState.latLng, DEFAULT_ZOOM));
		mMap.setMyLocationEnabled(mLocationEnabled);

		if (mState.isParked)
		{
			markParkingLocationOnMap(mState.latLng);
		}
	}

	/**
	 * Checks for location access permissions, and eventually requests them.
	 */
	private void checkLocationPermissions()
	{
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);

			mLocationEnabled = false;
		}
		else
		{
			// show current user location
			mLocationEnabled = true;
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
				mLocationEnabled = true;
				//noinspection ResourceType
				if (mMap != null)
					mMap.setMyLocationEnabled(true);

				fabLocateMe.show();
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

				if (bOk)
				{
					animateCameraSafely(createCameraUpdate(mState.latLng, clampZoomIn(DEFAULT_ZOOM + 1)));
					updateControlsVisibility(true);
				}
				else
				{
					Snackbar.make(v, "Location not saved...", Snackbar.LENGTH_LONG).show();
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
				if (mMap == null) return;

				final Location location = mMap.getMyLocation();
				if (location != null)
					animateCameraSafely(createCameraUpdate(new LatLng(location), clampZoomIn(DEFAULT_ZOOM)), DURATION_SLOW_MS);
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
				final LatLng oldLatLng = new LatLng(mState.latLng);

				boolean bOk = clearParkingLocation();

				if (bOk)
				{
					animateCameraSafely(createCameraUpdate(oldLatLng, clampZoomOut(DEFAULT_ZOOM)));
					updateControlsVisibility(true);
				}
				else
				{
					Snackbar.make(v, "Update not saved...", Snackbar.LENGTH_LONG).show();
				}
			}
		});
	}

	/**
	 * Animates the map's camera, but ensuring the map is ready.
	 */
	private void animateCameraSafely(CameraUpdate cameraUpdate)
	{
		animateCameraSafely(cameraUpdate, DURATION_FAST_MS);
	}

	private void animateCameraSafely(CameraUpdate cameraUpdate, int duration)
	{
		if (mMap != null)
			mMap.animateCamera(cameraUpdate, duration, null);
	}

	/**
	 * Calculates a zoom value to zoom in at least to the desired value.
	 */
	private int clampZoomOut(final int targetZoom)
	{
		return Math.min((int) mMap.getCameraPosition().zoom, targetZoom);
	}

	/**
	 * Calculates a zoom value to zoom out to maximum the desired value.
	 */
	private int clampZoomIn(final int targetZoom)
	{
		return Math.max(targetZoom, (int) mMap.getCameraPosition().zoom);
	}

	private void updateControlsVisibility(boolean bAnimate)
	{
		ivCrossHair.setVisibility(!mState.isParked ? View.VISIBLE : View.GONE);

		if (bAnimate)
		{
			ivCrossHair.startAnimation(mState.isParked ? fadeOutAnimation : fadeInAnimation);

			if (mState.isParked)
				swapFABsWithAnimation(fabLeave, fabPark);
			else
				swapFABsWithAnimation(fabPark, fabLeave);
		}
		else
		{
			fabPark.setVisibility(!mState.isParked ? View.VISIBLE : View.INVISIBLE);
			fabLeave.setVisibility(mState.isParked ? View.VISIBLE : View.INVISIBLE);
		}
	}

	private void swapFABsWithAnimation(@NonNull final FloatingActionButton fabIncoming,
	                                   @NonNull final FloatingActionButton fabOutgoing)
	{
		// layer the incoming button on top of the visible one
		fabOutgoing.setTranslationZ(-1f);
		fabIncoming.setTranslationZ(1f);

		fabIncoming.show();
		fabOutgoing.hide();
	}

	private void updateLocateMeButtonVisibility()
	{
		fabLocateMe.setVisibility(mLocationEnabled ? View.VISIBLE : View.GONE);
	}

	/**
	 * Update State with the current map center position. Save the state in SharedPreferences.
	 * @return true if saved successfully
	 */
	private boolean saveCurrentLocation()
	{
		final LatLng latLng = mMap.getCameraPosition().target;

		mState.isParked = true;
		mState.latLng = latLng;

		markParkingLocationOnMap(latLng);

		return State.saveToPrefs(mState, getAppPreferences());
	}

	/**
	 * Clear the parking location in our {@code State}. Save the state in SharedPreferences.
	 * @return true if saved successfully
	 */
	private boolean clearParkingLocation()
	{
		mState.isParked = false;
		mState.latLng = null;

		if (parkingMarker != null)
			mMap.removeMarker(parkingMarker);

		return State.saveToPrefs(mState, getAppPreferences());
	}

	/**
	 * Returns a {@code CameraUpdate} to {@link MapboxMap#moveCamera(CameraUpdate) move} or
	 * {@link MapboxMap#animateCamera(CameraUpdate) smoothly fly} the {@code MapView} to a new viewport.
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
		if (mMap == null)
			return;

		parkingMarker = mMap.addMarker(new MarkerOptions()
				                                  .position(latLng));
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mMapView.onPause();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mMapView.onResume();
		// TODO check location permissions are still available
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mMapView.onDestroy();
	}

	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		mMapView.onLowMemory();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		mMapView.onSaveInstanceState(outState);
	}
}
