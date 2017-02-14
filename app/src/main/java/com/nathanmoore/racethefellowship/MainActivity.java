package com.nathanmoore.racethefellowship;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.nathanmoore.racethefellowship.subscaleview.ImageSource;
import com.nathanmoore.racethefellowship.subscaleview.SubsamplingScaleImageView;

import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity
{
	private static final String TAG = "MainActivity";
	private SharedPreferences mSettings;
	private PedometerSettings mPedometerSettings;

	private int mMapID; //resource ID for the map


	private int mStepValue; //how many steps have been taken
	private float mDistanceValue; //how far had the pedometer recorded our travel

	private boolean mIsMetric; //is this in metric
	private boolean mQuitting = false; // Set when user selected Quit from menu, can be used by onPause, onStop, onDestroy

	//public static final SimpleDateFormat parser = new SimpleDateFormat("EEEE, MMMM d 'at' hh:mm a 'in the year' yyyy G");

	private DBHandler mDBHandler;
	/**
	 * True, when service is running.
	 */
	private boolean mIsRunning;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "[ACTIVITY] Pedometer onCreate");
		super.onCreate(savedInstanceState);

		mStepValue = 0;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (!prefs.getBoolean("firstTime", false))
		{
			// <---- run your one time code here
			CreateDatabase();

			// mark first time has runned.
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean("firstTime", true);
			editor.commit();
		}

		setContentView(R.layout.activity_main);
	}

	protected void CreateDatabase()
	{
		//TODO: make sure that the database is
		mDBHandler = new DBHandler(this);
		mDBHandler.Fill();
	}

	@Override
	protected void onStart()
	{
		Log.i(TAG, "[ACTIVITY] Pedometer  onStart");
		super.onStart();

		mMapID = getResources().getIdentifier("map", "drawable", getPackageName());
		CompositeProgress();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "[ACTIVITY]  Pedometer onResume");
		super.onResume();

		mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mPedometerSettings = new PedometerSettings(mSettings);

		// Read from preferences if the service was running on the last onPause
		mIsRunning = mPedometerSettings.isServiceRunning();

		// Start the service if this is considered to be an application start (last onPause was long ago)
		if (!mIsRunning && mPedometerSettings.isNewStart()) {
			startStepService();
			bindStepService();
		} else if (mIsRunning) {
			bindStepService();
		}

		mPedometerSettings.clearServiceRunning();

		if (mDistanceValue > 0)
			CompositeProgress();
			//mMapView.setImage(ImageSource.bitmap(CompositeProgress()));

		mIsMetric = mPedometerSettings.isMetric(); //check settings to see metric or english units
	}

	@Override
	protected void onPause() {
		Log.i(TAG, "[ACTIVITY]  Pedometer onPause");
		if (mIsRunning)
		{
			unbindStepService();
		}

		if (mQuitting)
		{
			mPedometerSettings.saveServiceRunningWithNullTimestamp(mIsRunning);
		}
		else
		{
			mPedometerSettings.saveServiceRunningWithTimestamp(mIsRunning);
		}

		super.onPause();
	}

	@Override
	protected void onStop()
	{
		Log.i(TAG, "[ACTIVITY]  Pedometer onStop");
		super.onStop();
	}

	protected void onDestroy()
	{
		Log.i(TAG, "[ACTIVITY] Pedometer  onDestroy");
		super.onDestroy();
	}

	protected void onRestart()
	{
		Log.i(TAG, "[ACTIVITY] Pedometer  onRestart");
		super.onDestroy();
	}

	private StepService mService;
	private ServiceConnection mConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			mService = ((StepService.StepBinder) service).getService();

			mService.registerCallback(mCallback);
			mService.reloadSettings();

		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	private void startStepService()
	{
		if (!mIsRunning)
		{
			Log.i(TAG, "[SERVICE] Start");
			mIsRunning = true;
			startService(new Intent(getApplicationContext(), StepService.class));
		}
	}

	private void bindStepService()
	{
		Log.i(TAG, "[SERVICE] Bind");
		bindService(new Intent(getApplicationContext(), StepService.class), mConnection, Context.BIND_AUTO_CREATE + Context.BIND_DEBUG_UNBIND);
	}

	private void unbindStepService()
	{
		Log.i(TAG, "[SERVICE] Unbind");
		unbindService(mConnection);
	}

	private void stopStepService()
	{
		Log.i(TAG, "[SERVICE] Stop");
		if (mService != null) {
			Log.i(TAG, "[SERVICE] stopService");
			stopService(new Intent(getApplicationContext(), StepService.class));
		}
		mIsRunning = false;
	}

	// TODO: unite all into 1 type of message
	private StepService.ICallback mCallback = new StepService.ICallback()
	{
		public void stepsChanged(int value)
		{
			mHandler.sendMessage(mHandler.obtainMessage(STEPS_MSG, value, 0));
		}

		public void distanceChanged(float value)
		{
			mHandler.sendMessage(mHandler.obtainMessage(DISTANCE_MSG, (int) (value * 1000), 0));
		}
	};

	/*TESTING PURPOSES ONLY*/
	private int count = 1; //Use to delay the update of the map.
	/*TESTING PURPOSES ONLY*/

	private static final int STEPS_MSG = 1;
	private static final int DISTANCE_MSG = 2;

	private Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case STEPS_MSG:
					mStepValue = (int) msg.arg1;
					Log.i(TAG, "Steps taken: " + mStepValue);
					break;
				case DISTANCE_MSG:
					mDistanceValue = ((int) msg.arg1) / 1f; //was 1000f not 1f
					Log.i(TAG, "Distance traveled: " + mDistanceValue);

					/*TESTING PURPOSES ONLY*/
					count++;
					if (count > 20) //if (mDistanceValue - mPreviousDistance > .1)
					{
						//mMapView.setImage(ImageSource.bitmap(CompositeProgress()));
						CompositeProgress();
						count = 0;
					}
					/*TESTING PURPOSES ONLY*/

					break;
				default:
					super.handleMessage(msg);
			}
		}
	};

	private void CompositeProgress()
	{
		int feetOutlineID = getResources().getIdentifier("hobbitfeetoutline", "drawable", getPackageName());
		int feetFillID = getResources().getIdentifier("whitefillfeettextured", "drawable", getPackageName());

		Bitmap map = BitmapFactory.decodeResource(getResources(), mMapID);
		Bitmap feetOutline = BitmapFactory.decodeResource(getResources(), feetOutlineID);
		Bitmap feetFill = BitmapFactory.decodeResource(getResources(), feetFillID);

		Log.i(TAG, "Update map image to distance of: " + mDistanceValue);
		new CompositeMapImageTask(findViewById(R.id.subSampleImageView) , map, feetOutline, feetFill, mDistanceValue).execute();
	}
}