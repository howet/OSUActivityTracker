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
	
	public static final String PREFS_NAME = "OSUActivityTrackerPrefs";
	public static final String TAG = "ActTracker";
	public static final String UPLOAD_URL =  "http://dataserv.basementserver.org/upload";
	
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
	
	private SensorReceiver mSensorRcvr = null;
	private WiFiScanReceiver mWifiRcvr = null;
	private GpsReceiver mGpsRcvr = null;
	
	private Timer mBackgroundTimer = null;
	
	private final IBinder mBinder = new TrackerBinder();
	
	int patientId = 1008;
	
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
		if( mSensorRcvr != null )
			mSensorRcvr.unregister();
		if( mWifiRcvr != null )
			mWifiRcvr.unregister();
		if( mGpsRcvr != null )
			mGpsRcvr.unregister();
		if( mBackgroundTimer != null )
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
		
		mSensorRcvr = new SensorReceiver(this);	
		
        /* register the accelerometer receiver */
		if (accel_enable == true)
			mSensorRcvr.registerAccelerometer();
		
        /* register the gyro receiver */
		if (gyro_enable == true)
			mSensorRcvr.registerGyroscope();
        
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
    			String data = "";
    			
    			Log.d(TAG, "Posting accelerometer/gyro data");
    			data = mSensorRcvr.getDataString();
    			
    			if(data.length() > 0 &&	doHttpPost(data))
    				mSensorRcvr.clearData(); /* clear all the data we just received */
    			
    			data = "";
    			
    			Log.d(TAG, "Posting rssi data");
    			data = mWifiRcvr.getDataString();
    			
    			if(data.length() > 0 && doHttpPost(data))
    				mSensorRcvr.clearData(); /* clear all the data we just received */
    			
    			data = "";
    			
    			Log.d(TAG, "Posting GPS data");
    			data = mGpsRcvr.getDataString();
    			
    			if(data.length() > 0 && doHttpPost(data))
    				mGpsRcvr.clearData(); /* clear all the data we just received */

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
	
	public int getPatientId() {
		return patientId;
	}

	public void setPatientId(int patientId) {
		this.patientId = patientId;
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
	
	private boolean doHttpPost(String data) {			
		
		String domain = "Chunk1";

		Log.i(TAG, "Trying to post data of length: " + data.length());
		
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(UPLOAD_URL);
		
		try {
			List<NameValuePair> nvp = new ArrayList<NameValuePair>(2);
			
			nvp.add(new BasicNameValuePair("domain", domain));
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
