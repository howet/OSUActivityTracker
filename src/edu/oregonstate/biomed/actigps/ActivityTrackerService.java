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
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class ActivityTrackerService extends Service {
	
	/* keys for settings file */
	public static final String SETTINGS_WIFI_ENABLE_KEY  = "wifiTracking";
	public static final String SETTINGS_GPS_ENABLE_KEY   = "gpsTracking";
	public static final String SETTINGS_ACCEL_ENABLE_KEY = "accelTracking";
	public static final String SETTINGS_GYRO_ENABLE_KEY  = "gyroTracking";
	public static final String SETTINGS_PUSH_INTERVAL_KEY  = "pushInterval";
	
	/* misc application strings */
	public static final String UPLOAD_UID = "6pEJ4th7UBRuv6TH";
	public static final String PREFS_NAME = "OSUActivityTrackerPrefs";
	public static final String TAG = "ActTracker";
	public static final String UPLOAD_URL =  "http://dataserv3.elasticbeanstalk.com/upload";
	
	private int POST_PERIOD; /* how often to post data to server */
	
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
	
	private Timer mBackgroundTimer = null;
	
	private final IBinder mBinder = new TrackerBinder();
	
	@Override
	public IBinder onBind(Intent arg) {
		return mBinder;
	}
	
	@Override
	public void onCreate() {
		Toast.makeText(this, "Created service", Toast.LENGTH_LONG).show();
	}
	
	@Override
	public void onDestroy() {
		/* unregister all sensors */
		mAccelRcvr.unregister();
		mWifiRcvr.unregister();
		mGpsRcvr.unregister();
		mGyroRcvr.unregister();
		
		mBackgroundTimer.cancel();
		
		Log.i(TAG, "Stopped Activity Tracker Service.");
		Toast.makeText(this, "Destroyed service", Toast.LENGTH_LONG).show();
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
		POST_PERIOD = settings.getInt(SETTINGS_PUSH_INTERVAL_KEY, 30);
		
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
        
        mBackgroundTimer.scheduleAtFixedRate( new TimerTask() {
            public void run() {
    			
    			/* number of times we have failed to post to server */
    			int post_fails = 0;
    			
    			Log.d(TAG, "Posting accelerometer data");
    			
    			//TODO: Fix problem where data would get deleted that was received during HTTP POST
    			
    			if(doHttpPost(mAccelRcvr.getDataString(), mAccelRcvr.getChannelName()))
    				mAccelRcvr.clearData(); /* clear all the data we just posted */
    			else
    				post_fails++;
    			
    			Log.d(TAG, "Posting gyro data");
    			
    			if(doHttpPost(mGyroRcvr.getDataString(), mGyroRcvr.getChannelName()))
    				mGyroRcvr.clearData(); /* clear all the data we just posted */
    			else
    				post_fails++;
    			
    			Log.d(TAG, "Posting rssi data");
    			
    			if(doHttpPost(mWifiRcvr.getDataString(), mWifiRcvr.getChannelName()))
    				mAccelRcvr.clearData(); /* clear all the data we just posted */
    			else
    				post_fails++;
    			
    			Log.d(TAG, "Posting GPS data");
    			
    			if(doHttpPost(mGpsRcvr.getDataString(), mGpsRcvr.getChannelName()))
    				mGpsRcvr.clearData(); /* clear all the data we just posted */
    			else
    				post_fails++;
    			
    			if(post_fails > 1)
    				postNotification("Data Post Fail. Check Network Connection.");
             }
          }, POST_PERIOD*1000, POST_PERIOD*1000);
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
	
	private boolean doHttpPost(String data, String channel) {			
		
		/* if there was no data, just report that we posted successfully */
		if(data.length() == 0)
			return true;

		Log.i(TAG, "Trying to post data of length: " + data.length());
		
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(UPLOAD_URL);
		
		try {
			List<BasicNameValuePair> nvp = new ArrayList<BasicNameValuePair>(2);
			
			nvp.add(new BasicNameValuePair("user", UPLOAD_UID));
			nvp.add(new BasicNameValuePair("channel", channel));
			nvp.add(new BasicNameValuePair("data", data));
			httppost.setEntity(new UrlEncodedFormEntity(nvp));
			
			HttpResponse r = httpclient.execute(httppost);
			Log.d(TAG,"Http response: " + r.getStatusLine().toString());
			
			if( r.getStatusLine().toString().contains("200 OK"))
				return true;
			else
				return false;
		}
		catch (IOException e) {
			Log.e(TAG, "Exception Occurred on HTTP post: " + e.toString());
		}
		return false;
	}
}
