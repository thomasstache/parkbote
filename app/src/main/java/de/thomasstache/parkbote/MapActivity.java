package de.thomasstache.parkbote;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class MapActivity extends AppCompatActivity
{
	private static final String TAG = "ParkboteApp";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		Log.i(TAG, "Running onCreate()");
	}
}
