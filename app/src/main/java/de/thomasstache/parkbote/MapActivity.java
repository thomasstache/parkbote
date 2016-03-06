package de.thomasstache.parkbote;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.views.MapView;

public class MapActivity extends AppCompatActivity
{
	private static final String TAG = "ParkboteApp";

	private MapView mapView = null;
	private CameraUpdate homeCamera;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		Log.i(TAG, "Running onCreate()");

		setupMapView();
		setInitialCamera();
		mapView.moveCamera(homeCamera);

		mapView.onCreate(savedInstanceState);

		setupLocateMeButton();
	}

	private void setupLocateMeButton()
	{
		final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_locateMe);
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Snackbar.make(v, "I'll locate you soon!", Snackbar.LENGTH_LONG)
						.setAction("Action", null)
						.show();
			}
		});
	}

	private void setupMapView()
	{
		mapView = (MapView) findViewById(R.id.map);
		mapView.setStyleUrl(Style.MAPBOX_STREETS);
	}

	private void setInitialCamera()
	{
		CameraPosition cam = new CameraPosition.Builder()
				.target(new LatLng(51.063, 13.746))
				.zoom(14)
				.build();

		homeCamera = CameraUpdateFactory.newCameraPosition(cam);
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
