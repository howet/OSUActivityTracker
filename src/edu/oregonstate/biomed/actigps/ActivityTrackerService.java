package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.io.IOException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

public class ActivityTrackerService extends Service {
	
	/* keys for settings file */
	public static final String SETTINGS_WIFI_ENABLE_KEY  = "wifiTracking";
	public static final String SETTINGS_GPS_ENABLE_KEY   = "gpsTracking";
	public static final String SETTINGS_ACCEL_ENABLE_KEY = "accelTracking";
	public static final String SETTINGS_GYRO_ENABLE_KEY  = "gyroTracking";
	public static final String SETTINGS_USER_ID_KEY  = "userID";
	public static final String SETTINGS_CALIBRATE_KEY  = "calibrateVal";
	public static final String SETTINGS_TRACKED_USERS_KEY  = "trackedUsers";
	
	/* misc application strings */
	private String mUserId = "";
	public static final String UPLOAD_UID = "6pEJ4th7UBRuv6TH";
	public static final String PREFS_NAME = "OSUActivityTrackerPrefs";
	public static final String TAG = "ActTracker";
	public static final String UPLOAD_URL =  "http://dataserv3.elasticbeanstalk.com/upload";
	public static final String DOWNLOAD_HOST =  "dataserv3.elasticbeanstalk.com";
	public static final String DOWNLOAD_PATH = "/download";
	
	private static final int POST_PERIOD = 30; /* how often to post data to server */
	
	/* Managers */
	SensorManager sensors;
	LocationManager location;
	WifiManager wifi;
	
	/* sensor enable flags */
	private boolean accel_enable;
	private boolean gps_enable;
	private boolean wifi_enable;
	private boolean gyro_enable;
	
	private AccelReceiver mAccelRcvr = null;
	private WiFiScanReceiver mWifiRcvr = null;
	private GpsReceiver mGpsRcvr = null;
	private GyroReceiver mGyroRcvr = null;
	
	private WakeLock mWakeLock;
	
	private float mCalibrationLevel;
	
	private Timer mBackgroundTimer = null;
	
	private final IBinder mBinder = new TrackerBinder();
	
	@Override
	public IBinder onBind(Intent arg) {
		return mBinder;
	}
	
	@Override
	public void onCreate() { 
		Log.i(TAG, "Started Activity Tracker Service.");
	}
	
	@Override
	public void onDestroy() {
		/* unregister all sensors */
		mAccelRcvr.unregister();
		mWifiRcvr.unregister();
		mGpsRcvr.unregister();
		mGyroRcvr.unregister();
		
		mAccelRcvr = null;
		mWifiRcvr = null;
		mGpsRcvr = null;
		mGyroRcvr = null;
		
		mBackgroundTimer.cancel();
		mBackgroundTimer = null;
		
		mWakeLock.release();
		
		Log.i(TAG, "Stopped Activity Tracker Service.");
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.i(TAG, "Starting Activity Tracker Service.");
		
		/* restore preferences */
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		wifi_enable = settings.getBoolean(SETTINGS_WIFI_ENABLE_KEY, true);
		gps_enable = settings.getBoolean(SETTINGS_GPS_ENABLE_KEY, true);
		accel_enable = settings.getBoolean(SETTINGS_ACCEL_ENABLE_KEY, true);
		gyro_enable = settings.getBoolean(SETTINGS_GYRO_ENABLE_KEY, true);
		
		mUserId = settings.getString(SETTINGS_USER_ID_KEY, "");
		
		/* default value is high so no values are recorded if calibration has not completed */
		mCalibrationLevel = settings.getFloat(SETTINGS_CALIBRATE_KEY, 1000);
		
		/* get system services */
		sensors = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		location = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		
		
		/* enable all the necessary hardware */
		wifi.setWifiEnabled(true);
		
		mAccelRcvr = new AccelReceiver(this);	
		
        /* register the accelerometer receiver */
		if (accel_enable == true)
			mAccelRcvr.register();
		
		mGyroRcvr = new GyroReceiver(this);
		
        /* register the gyro receiver */
		if (gyro_enable == true)
			mGyroRcvr.register();
        
		mWifiRcvr = new WiFiScanReceiver(this);
		
        /* register the wifi scan receiver */
		if (wifi_enable == true)
			mWifiRcvr.register();
		
		mGpsRcvr = new GpsReceiver(this);
		
		/* register the gps receiver */
		if (gps_enable == true)
			mGpsRcvr.register();
		
		/* start http post timer */
        mBackgroundTimer = new Timer();
        
        mBackgroundTimer.scheduleAtFixedRate( new HttpPostTimerTask(), POST_PERIOD*1000, POST_PERIOD*1000);
        
        /* keep the CPU running while this service is active */
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();
	}
	
	private class HttpPostTimerTask extends TimerTask {
    	private Handler mHandler = new Handler(Looper.getMainLooper());

        @Override
        public void run() {
           mHandler.post(new Runnable() {
              public void run() {
              	/* run accelerometer and gyro on own threads: these take awhile */
              	Log.d(TAG, "Starting HTTP Posts.");

      			new HttpPostTask().execute(mAccelRcvr,mGpsRcvr);
      			new HttpPostTask().execute(mGyroRcvr,mWifiRcvr);
              }
           });
         }
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	public class TrackerBinder extends Binder {
		ActivityTrackerService getTracker() {
			return ActivityTrackerService.this;
		}
	}

	void postNotification(String text) {
		int icon = R.drawable.icon;        // icon from resources
		CharSequence tickerText = text;              // ticker-text
		long when = System.currentTimeMillis();         // notification time
		Context context = getApplicationContext();      // application Context
		CharSequence contentTitle = "Activity Tracker Failure";  // message title
		CharSequence contentText = text;      // message text

		Intent notificationIntent = new Intent(ActivityTrackerService.this, ActivityTrackerService.class);
		PendingIntent contentIntent = PendingIntent.getActivity(ActivityTrackerService.this, 0, notificationIntent, 0);

		// the next two lines initialize the Notification, using the configurations above
		Notification notification = new Notification(icon, tickerText, when);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.defaults |= (Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
		
		NotificationManager mNotify = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mNotify.notify(0, notification);
	}
	
	/**
	 * Get the level of calibration for the accelerometer, as read from the settings
	 * @return Calibration level
	 */
	public float getCalibrationLevel()
	{
		return mCalibrationLevel;
	}
	
	/*----------------------------------------
	 * HTTP Stuff Below Here
	 *---------------------------------------*/
	
	/* create an async task to handle queuing of HTTP POSTs on separate threads */
	private class HttpPostTask extends AsyncTask<ActivitySensor, Integer, Boolean> {
		protected Boolean doInBackground(ActivitySensor... sensors) {
			int count = sensors.length;
			boolean result = true;
			 
			for (int i = 0; i < count; i++) {
				boolean newres = doHttpPost(sensors[i]);
				result = result && newres;
			}
			return result;
		}
		 
		protected void onPostExecute(Boolean result) {
			if( result == false && isNetworkAvailable() == false )
				postNotification("Data Post Fail. Check Network Connection.");
		}
	}
	
	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager 
	          = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null;
	}
	

	
	private int postcount = 0;
	
	private boolean doHttpPost(ActivitySensor sensor) {			
		
		String data = sensor.getDataString();
		String channel = sensor.getChannelName();
		boolean success = false;
		
		/* if there was no data, just report that we posted successfully */
		if(data.length() == 0)
		{
			success = true;
		}
		else
		{
			success = false;
			HttpPost httppost = new HttpPost(UPLOAD_URL);
			postcount++;
			String id = String.valueOf(postcount);
			
			Log.i(TAG, id + " Trying to post " + channel + " of length: " + data.length());
			
			try {
				List<BasicNameValuePair> nvp = new ArrayList<BasicNameValuePair>(2);
				
				nvp.add(new BasicNameValuePair("user", UPLOAD_UID));
				nvp.add(new BasicNameValuePair("channel", channel + "_" + mUserId));
				nvp.add(new BasicNameValuePair("data", data));
				httppost.setEntity(new UrlEncodedFormEntity(nvp));
				
				HttpClient client = HttpClientFactory.getThreadSafeClient();
				
				HttpResponse r = client.execute(httppost);
				Log.d(TAG, id + " Http response: " + r.getStatusLine().toString());
				
				if( r.getStatusLine().toString().contains("200 OK"))
					success = true;
				
				/* clear the data if we successfully posted, otherwise restore it to data list and post later */
				if(success)
					sensor.clearData();
				else
					sensor.restoreData();
			}
			catch (IOException e) {
				Log.e(TAG, id + " Exception Occurred on HTTP post: " + e.toString());
				/* restore it to data list and post later */
				sensor.restoreData();
			}
		}
		
		return success;
	}

}
