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
	private SubsamplingScaleImageView mMapView; //view where the map is drawn


	private int mStepValue; //how many steps have been taken
	private float mDistanceValue; //how far had the pedometer recorded our travel

	/*TESTING PURPOSES ONLY*/
	private float mPreviousDistance; //how far we've gone since last we updated the map
	/*TESTING PURPOSES ONLY*/

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

		mMapView = (SubsamplingScaleImageView) findViewById(R.id.subSampleImageView);
		mMapID = getResources().getIdentifier("map", "drawable", getPackageName());

		mMapView.setImage(ImageSource.bitmap(BitmapFactory.decodeResource(getResources(), mMapID)));
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
			mMapView.setImage(ImageSource.bitmap(CompositeProgress()));

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
						mMapView.setImage(ImageSource.bitmap(CompositeProgress()));
						count = 0;
					}
					/*TESTING PURPOSES ONLY*/

					break;
				default:
					super.handleMessage(msg);
			}
		}
	};

	//position and rotation of each footstep graphic to be composited onto the map background.
	private Matrix[] footstepsM = new Matrix[56];

	//filled with testing code. For example, hobbit feet should be based on date from start, not distance traveled.
	protected Bitmap CompositeProgress() //float progress
	{
		Log.i(TAG, "Composite Image of Map.");

		int feetOutlineID = getResources().getIdentifier("hobbitfeetoutline", "drawable", getPackageName());
		int feetFillID = getResources().getIdentifier("whitefillfeettextured", "drawable", getPackageName());

		Bitmap map = BitmapFactory.decodeResource(getResources(), mMapID);
		Bitmap feetOutline = BitmapFactory.decodeResource(getResources(), feetOutlineID);
		Bitmap feetFill = BitmapFactory.decodeResource(getResources(), feetFillID);
		Bitmap feetPercent = feetFill.copy(feetFill.getConfig(), true);

		Bitmap result = Bitmap.createBitmap(map.getWidth(), map.getHeight(), Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(result);
		canvas.drawBitmap(map, 0f, 0f, null);

		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		ColorFilter filter = new LightingColorFilter(Color.rgb(255, 194, 31), 0);
		paint.setColorFilter(filter);

		/* CURRENTLY WE ASSUME EACH SET OF FEET IS 20 MILES. */

		//How many complete steps have we traveled so far.
		int steps = Math.round((mDistanceValue + .99f) / 20); //add .99 then round down, this effectivly forces the number to round up
		//What percent are we through the current foot
		float percent = (mDistanceValue % 20f) / 20f;


		//How far along are we in the 'current' feet.
		percent = Math.round((feetPercent.getHeight() * percent) + .5f); //add .5f so when we floor our number, it rounds up correctly.

		for (int x = 0; x < feetPercent.getWidth(); ++x) {
			for (int y = feetPercent.getHeight()-1; y > 0; --y)
			{
				//check how high we've gotten along the Y axis in the bitmap.
				if (y < feetPercent.getHeight() - (percent + 1))
				{
					feetPercent.setPixel(x, y, Color.TRANSPARENT);
				}
			}
		}

		//We cannot go farther than all the way.
		if (steps > footstepsM.length)
			steps = footstepsM.length;

		//Draw the feet on top of the map image.
		for (int i = 0; i < steps; ++i)
		{
			footstepsM[i] = new Matrix();
			footstepsM[i].setRotate(FellowshipPathData.rot[i], feetOutline.getWidth() / 2, feetOutline.getHeight() / 2); //rotate around the center, not a corner;

			Log.i(TAG, "foot positions are: " + (FellowshipPathData.pos[i][0] + 10) + "x " + (FellowshipPathData.pos[i][1] + 10) + "y.");
			footstepsM[i].postTranslate(FellowshipPathData.pos[i][0] + 10, FellowshipPathData.pos[i][1] + 10);

			if (i == steps - 1)
			{
				//this is the current step, we need to draw the percentage
				canvas.drawBitmap(feetPercent, footstepsM[i], paint);
			}
			else
			{
				canvas.drawBitmap(feetFill, footstepsM[i], paint);
			}

			canvas.drawBitmap(feetOutline, footstepsM[i], paint);
		}

		mPreviousDistance = mDistanceValue;
		return result;
	}
}