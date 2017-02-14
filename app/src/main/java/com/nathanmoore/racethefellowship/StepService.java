package com.nathanmoore.racethefellowship;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application.  The {StepServiceController}
 * and {StepServiceBinding} classes show how to interact with the
 * service.
 *
 * <p>Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 */
public class StepService extends Service
{
	private static final String TAG = "pedometer.StepService";
	private SharedPreferences mSettings;
	private PedometerSettings mPedometerSettings;
	private SharedPreferences mState;
	private SharedPreferences.Editor mStateEditor;
	private Utils mUtils;
	private SensorManager mSensorManager;
	private Sensor mSensor;
	private StepDetector mStepDetector;
	private StepDisplayer mStepDisplayer;
	private DistanceNotifier mDistanceNotifier;

	private PowerManager.WakeLock wakeLock;
	private NotificationManager mNM;

	private int mSteps;
	private float mDistance;

	/**
	 * Class for clients to access.  Because we know this service always
	 * runs in the same process as its clients, we don't need to deal with
	 * IPC.
	 */
	public class StepBinder extends Binder {
		StepService getService() {
			return StepService.this;
		}
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "[SERVICE] StepService onCreate");
		super.onCreate();

		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		// Load settings
		mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mPedometerSettings = new PedometerSettings(mSettings);
		mState = getSharedPreferences("state", 0);

		mUtils = Utils.getInstance();
		mUtils.setService(this);

		acquireWakeLock();

		// Start detecting
		mStepDetector = new StepDetector();
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		registerDetector();

		// Register our receiver for the ACTION_SCREEN_OFF action. This will make our receiver
		// code be called whenever the phone enters standby mode.
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mReceiver, filter);

		mStepDisplayer = new StepDisplayer(mPedometerSettings, mUtils);
		mStepDisplayer.setSteps(mSteps = mState.getInt("steps", 0));
		mStepDisplayer.addListener(mStepListener);
		mStepDetector.addStepListener(mStepDisplayer);

		mDistanceNotifier = new DistanceNotifier(mDistanceListener, mPedometerSettings, mUtils);
		mDistanceNotifier.setDistance(mDistance = mState.getFloat("distance", 0));
		mStepDetector.addStepListener(mDistanceNotifier);

		// Start voice
		reloadSettings();

		// Tell the user we started.
		Toast.makeText(this, getText(R.string.started), Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "[SERVICE] StepService  onDestroy");

		// Unregister our receiver.
		unregisterReceiver(mReceiver);
		unregisterDetector();

		mStateEditor = mState.edit();
		mStateEditor.putInt("steps", mSteps);
		mStateEditor.putFloat("distance", mDistance);
		mStateEditor.commit();

		mNM.cancel(R.string.app_name);

		wakeLock.release();

		super.onDestroy();

		// Stop detecting
		mSensorManager.unregisterListener(mStepDetector);

		// Tell the user we stopped.
		Toast.makeText(this, getText(R.string.stopped), Toast.LENGTH_SHORT).show();
	}

	private void registerDetector()
	{
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER );
		mSensorManager.registerListener(mStepDetector, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
	}

	private void unregisterDetector()
	{
		mSensorManager.unregisterListener(mStepDetector);
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		Log.i(TAG, "[SERVICE] StepService  onBind");
		return mBinder;
	}

	/**
	 * Receives messages from activity.
	 */
	private final IBinder mBinder = new StepBinder();

	public interface ICallback
	{
		public void stepsChanged(int value);
		public void distanceChanged(float value);
	}

	private ICallback mCallback;

	public void registerCallback(ICallback cb)
	{
		mCallback = cb;
	}

	public void reloadSettings()
	{
		mSettings = PreferenceManager.getDefaultSharedPreferences(this);

		if (mStepDetector != null)
		{
			mStepDetector.setSensitivity(Float.valueOf(mSettings.getString("sensitivity", "10")));
		}

		if (mStepDisplayer    != null) mStepDisplayer.reloadSettings();
		if (mDistanceNotifier != null) mDistanceNotifier.reloadSettings();
	}

	public void resetValues()
	{
		mStepDisplayer.setSteps(0);
		mDistanceNotifier.setDistance(0);
	}

	/**
	 * Forwards pace values from PaceNotifier to the activity.
	 */
	private StepDisplayer.Listener mStepListener = new StepDisplayer.Listener()
	{
		public void stepsChanged(int value)
		{
			mSteps = value;
			passValue();
		}
		public void passValue()
		{
			if (mCallback != null)
			{
				mCallback.stepsChanged(mSteps);
			}
		}
	};

	/**
	 * Forwards distance values from DistanceNotifier to the activity.
	 */
	private DistanceNotifier.Listener mDistanceListener = new DistanceNotifier.Listener()
	{
		public void valueChanged(float value)
		{
			mDistance = value;
			passValue();
		}
		public void passValue()
		{
			if (mCallback != null)
			{
				mCallback.distanceChanged(mDistance);
			}
		}
	};

	// BroadcastReceiver for handling ACTION_SCREEN_OFF.
	private BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			// Check action just to be on the safe side.
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF))
			{
				// Unregisters the listener and registers it again.
				StepService.this.unregisterDetector();
				StepService.this.registerDetector();
				if (mPedometerSettings.wakeAggressively())
				{
					wakeLock.release();
					acquireWakeLock();
				}
			}
		}
	};

	private void acquireWakeLock() {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		int wakeFlags;
		if (mPedometerSettings.wakeAggressively())
		{
			wakeFlags = PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
		}
		else if (mPedometerSettings.keepScreenOn())
		{
			wakeFlags = PowerManager.SCREEN_DIM_WAKE_LOCK;
		}
		else
		{
			wakeFlags = PowerManager.PARTIAL_WAKE_LOCK;
		}
		wakeLock = pm.newWakeLock(wakeFlags, TAG);
		wakeLock.acquire();
	}

}
