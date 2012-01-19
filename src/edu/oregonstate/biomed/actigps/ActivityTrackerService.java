package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
import android.hardware.SensorManager;
import android.location.*;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
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
	
	public static final String UPLOAD_URL = "http://dataserv.basementserver.org/upload";
	public static final int POST_PERIOD = 30; //post every 30 seconds
	
	private final IBinder mBinder = new TrackerBinder();
	
	/* Managers */
	WifiManager wifi;
	SensorManager sensors;
	LocationManager location;
	
	private SensorReceiver mSensorRcvr = null;
	private WiFiScanReceiver mWifiRcvr = null;
	
	private Timer mBackgroundTimer = null;
	private long lastUpdateTime = 0;
	
	private int patientId = 1008;
	
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
		/* get system services */
		sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
		
        /* register the sensor receiver */
		if (mSensorRcvr == null)
			mSensorRcvr = new SensorReceiver(this);
		
        location = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        
        wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        
        /* register the wifi scan receiver */
		if (mWifiRcvr == null)
			mWifiRcvr = new WiFiScanReceiver(this);
        
        /* set criteria for GPS signal */
//        Criteria cri = new Criteria(); 
//        
//        cri.setAccuracy(Criteria.ACCURACY_FINE); 
//        cri.setAltitudeRequired(false); 
//        cri.setBearingRequired(false); 
//        cri.setCostAllowed(true);
//        cri.setPowerRequirement(Criteria.POWER_LOW); 
//        String provider = locationManager.getBestProvider(cri, true); 
//
//        Location location = locationManager.getLastKnownLocation(provider);
//        //updateWithNewLocation(location); 
//        
//        /* Update location every 30 seconds, or when user moves 5 meters,
//         * whichever comes first
//         */
//        locationManager.requestLocationUpdates(provider, 30000, 5, locationListener); 
 
        lastUpdateTime = System.currentTimeMillis();
 
        mBackgroundTimer = new Timer();
        
        mBackgroundTimer.scheduleAtFixedRate( new TimerTask() {
            public void run() {
    			AsyncHttpPoster ahp = new AsyncHttpPoster();
    			String data;
    			data = mSensorRcvr.getDataString();
    			if(data.length() > 0)
    				ahp.execute(data);
    			
    			data = mWifiRcvr.getDataString();
    			if(data.length() > 0)
    				ahp.execute(data);
             }
          }, 0, POST_PERIOD*1000);
		
        Toast.makeText(this, "Time: " + lastUpdateTime, Toast.LENGTH_LONG).show();
	}


//	private final LocationListener locationListener = new LocationListener() 
//	{ 
//	
//		@Override 
//		public void onLocationChanged(Location location) 
//		{ 
//			//updateWithNewLocation(location); 
//		} 
//		
//		@Override 
//		public void onProviderDisabled(String provider) 
//		{ 
//			//updateWithNewLocation(null); 
//		} 
//		
//		@Override 
//		public void onProviderEnabled(String provider) 
//		{ } 
//		
//		@Override 
//		public void onStatusChanged(String provider, int status, Bundle extras) 
//		{ } 
//	
//	};
	
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
	
	protected class AsyncHttpPoster extends AsyncTask<String, String, String> {
		
		public AsyncHttpPoster() {} /* empty constructor */
		
		void writeFile(String data) {
			boolean mExternalStorageAvailable = false;
			boolean mExternalStorageWriteable = false;
			String state = Environment.getExternalStorageState();

			if (Environment.MEDIA_MOUNTED.equals(state)) {
			    // We can read and write the media
			    mExternalStorageAvailable = mExternalStorageWriteable = true;
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			    // We can only read the media
			    mExternalStorageAvailable = true;
			    mExternalStorageWriteable = false;
			} else {
			    // Something else is wrong. It may be one of many other states, but all we need
			    //  to know is we can neither read nor write
			    mExternalStorageAvailable = mExternalStorageWriteable = false;
			}
			if (!mExternalStorageAvailable || !mExternalStorageWriteable) {
				//BAD
				postNotification("Failed to aquire external storage. Please report this failure!");
				return;
			}
			
			int num = 1;
			
			String filename; 
			File extern_path = Environment.getExternalStorageDirectory();
			File file; 
			File dirs = new File(extern_path, "TrackerData");
			
			try {
				dirs.mkdirs();
			}
			catch(SecurityException e) {
				postNotification("Write permissions not enabled.");
			}
			
			/* loop while a file by the same name already exists */
			do {
				filename = "TrackerData/data_accel_" + num + ".csv";
				file = new File(extern_path, filename);
				num++;
			} while(file.exists());
			
			try {
				if (!file.createNewFile())
				{
					postNotification("Failed to create new file.");
					return;
				}
			}
			catch (IOException e) {
				postNotification("Failed to create file.");
			}
			catch (SecurityException e) {
				postNotification("Write permissions not enabled.");
			}
			
			/* write the actual data */
			try {
				OutputStream out = new FileOutputStream(file);
				out.write(data.getBytes());
				out.close();
			}
			catch (IOException e) {
				postNotification("Failed to write to file.");
				return;
			}
		}
		
		void doHttpPost(String data) {			
			
			String domain = "Chunk1";

			Log.i("ServiceGpsAccelTracker", "Trying to post data");
			
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(UPLOAD_URL);
			
			try {
				List<NameValuePair> nvp = new ArrayList<NameValuePair>(2);
				
				nvp.add(new BasicNameValuePair("domain", domain));
				nvp.add(new BasicNameValuePair("data", data));
				httppost.setEntity(new UrlEncodedFormEntity(nvp));
				
				HttpResponse r = httpclient.execute(httppost);
				postNotification("Http response: " + r.getStatusLine().toString());
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
		@Override
		protected String doInBackground(String... params) {
			//Attempt to write the data to the file
			if(params.length > 0)
			{
				writeFile(params[0]);
				//do the http post
				doHttpPost(params[0]);
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
		}
		
	}
}
