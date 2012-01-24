package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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
	
	public static final String TAG = "ActTracker";
	public static final String UPLOAD_URL = "http://dataserv.basementserver.org/upload";
	public static final int POST_PERIOD = 30; //post every 30 seconds
	
	/* Managers */
	SensorManager sensors;
	LocationManager location;
	WifiManager wifi;

	private SensorReceiver mSensorRcvr = null;
	private WiFiScanReceiver mWifiRcvr = null;
	private Timer mBackgroundTimer = null;
	
	private AsyncHttpPoster httpPoster = null;
	
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
		Toast.makeText(this, "Destroyed service", Toast.LENGTH_LONG).show();
		if( mSensorRcvr != null )
			mSensorRcvr.unregister();
		if( mWifiRcvr != null )
			mWifiRcvr.unregister();
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		Log.i(TAG, "Starting Activity Tracker Service.");

		/* get system services */
		sensors = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		location = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		
        /* register the sensor receiver */
		if (mSensorRcvr == null)
			mSensorRcvr = new SensorReceiver(this);		
        
        /* register the wifi scan receiver */
		if (mWifiRcvr == null)
			mWifiRcvr = new WiFiScanReceiver(this);
		
		/* start http post timer */
        mBackgroundTimer = new Timer();
        httpPoster = new AsyncHttpPoster();
        
        mBackgroundTimer.scheduleAtFixedRate( new TimerTask() {
            public void run() {
            	Log.i(TAG, "timer expired");
    			String data = "";
    			data = mSensorRcvr.getDataString();
    			if(data.length() > 0)
    				httpPoster.execute(data);
    			
    			data = mWifiRcvr.getDataString();
    			if(data.length() > 0)
    				httpPoster.execute(data);
    			Log.i(TAG, "done with timer routine");
             }
          }, 0, POST_PERIOD*1000);
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
	
	protected Handler mHandler = new Handler();
	
	protected class AsyncHttpPoster extends AsyncTask<String, String, String> {
		
		public AsyncHttpPoster() {} /* empty constructor */
		
		String doHttpPost(String data) {			
			
			String domain = "Chunk1";

			Log.i(TAG, "Trying to post data: " + data);
			
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(UPLOAD_URL);
			
			try {
				List<NameValuePair> nvp = new ArrayList<NameValuePair>(2);
				
				nvp.add(new BasicNameValuePair("domain", domain));
				nvp.add(new BasicNameValuePair("data", data));
				httppost.setEntity(new UrlEncodedFormEntity(nvp));
				
				//HttpResponse r = httpclient.execute(httppost);
				//Log.i(TAG,"Http response: " + r.getStatusLine().toString());
				return "";//r.getStatusLine().toString();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			return "Unsuccessful in HTTP POST.";
		}
		
		
		@Override
		protected String doInBackground(String... params) {
			if(params.length > 0)
			{
				Log.i(TAG, "Post HTTP");
				return doHttpPost(params[0]);
			}
			else return "";
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
		}
		
	}
}
