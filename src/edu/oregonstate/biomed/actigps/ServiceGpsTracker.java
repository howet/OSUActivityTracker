package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
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

public class ServiceGpsTracker extends Service implements SensorEventListener {
	
	private final IBinder mBinder = new TrackerBinder();
	private LinkedList<Float> accelx = new LinkedList<Float>();
	private LinkedList<Float> accely = new LinkedList<Float>();
	private LinkedList<Float> accelz = new LinkedList<Float>();
	private LinkedList<Long> accelt = new LinkedList<Long>();
	private SensorManager mSensorManager = null;
	private Sensor mAccelerometer = null;
	private long lastUpdateTime = 0;
	//note that this will happen according to the sensor update period
	private final long LOG_PERIOD_MIN = 1;
	private final long LOG_PERIOD_MS = LOG_PERIOD_MIN*(1*1000); //every 1 second or so 
	
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
		mSensorManager.unregisterListener(this);
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        lastUpdateTime = System.currentTimeMillis();
        Toast.makeText(this, "Time: " + lastUpdateTime, Toast.LENGTH_LONG).show();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	public class TrackerBinder extends Binder {
		ServiceGpsTracker getTracker() {
			return ServiceGpsTracker.this;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//We will ignore this for now... but post a log message about it
		//Log.i("ServiceGpsAccelTracker", "Sensor accuracy change");
	}
	
	private long timeoffset = 0;
	private long timeoffset_start = 0;
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			accelx.addLast(event.values[0]);
			accely.addLast(event.values[1]);
			accelz.addLast(event.values[2]);
			if (timeoffset == 0) {
				//timeoffset = System.currentTimeMillis();
				timeoffset = (new Date()).getTime();
				timeoffset_start = event.timestamp;
			}
			
			//build the timestamp using the nanotime stamp from the sensor and the
			//offset from the RTC, keep the result in microseconds
			accelt.addLast(timeoffset*1000+(event.timestamp - timeoffset_start)/1000);			
			
			Intent i = new Intent("PHONE_LOCATION_UPDATE");
			Bundle b = new Bundle();
			b.putString("x", Float.toString(event.values[0]));
			b.putString("y", Float.toString(event.values[1]));
			b.putString("z", Float.toString(event.values[2]));
			b.putString("t", accelt.getLast().toString());
			i.putExtras(b);
			sendBroadcast(i);
			
			break;
		case Sensor.TYPE_GYROSCOPE:
			
			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			
			break;
		}
		
		//check if we need to post an update
		if (System.currentTimeMillis() - lastUpdateTime > LOG_PERIOD_MS) {
			lastUpdateTime = System.currentTimeMillis();
			GpsTrackerAsync gta = new GpsTrackerAsync(accelx, accely, accelz, accelt);
			gta.execute("");
			lastAsync = gta;
			
			//we've written the data, clear from memory
			accelx = new LinkedList<Float>();
			accely = new LinkedList<Float>();
			accelz = new LinkedList<Float>();
			accelt = new LinkedList<Long>();
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

		Intent notificationIntent = new Intent(ServiceGpsTracker.this, ServiceGpsTracker.class);
		PendingIntent contentIntent = PendingIntent.getActivity(ServiceGpsTracker.this, 0, notificationIntent, 0);

		// the next two lines initialize the Notification, using the configurations above
		Notification notification = new Notification(icon, tickerText, when);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.defaults |= (Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
		
		
		NotificationManager mNotify = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mNotify.notify(0, notification);
	}
	
	protected Handler mHandler = new Handler();
	
	GpsTrackerAsync lastAsync = null;
	
	protected class GpsTrackerAsync extends AsyncTask<String, String, String> {
		private LinkedList<Float> accelx;
		private LinkedList<Float> accely;
		private LinkedList<Float> accelz;
		private LinkedList<Long> accelt;
		
		public GpsTrackerAsync(LinkedList<Float> x, LinkedList<Float> y, LinkedList<Float> z, LinkedList<Long> t) {
			accelx = x;
			accely = y;
			accelz = z;
			accelt = t;
		}
		
		void writeFile() {
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
			
			File file = new File(Environment.getExternalStorageDirectory(), "TrackerData/data_accel_" + accelt.getLast() + ".csv");
			File dirs = new File(Environment.getExternalStorageDirectory(), "TrackerData");
			try {
				dirs.mkdirs();
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				postNotification("Failed to aquire external storage. Please report this failure!");
				return;
			}
			try {
				OutputStream out = new FileOutputStream(file);
				String w = "Timestamp, ";
				out.write(w.getBytes());
				for (Long l : accelt) {
					w = "" + l + ","; 
					out.write(w.getBytes());
				}
				w = "\nAccelX (g), ";
				out.write(w.getBytes());
				for (Float f : accelx) {
					w = "" + f + ",";
					out.write(w.getBytes());
				}
				w = "\nAccelY (g),";
				out.write(w.getBytes());
				for (Float f : accely) {
					w = "" + f + ",";
					out.write(w.getBytes());
				}
				w = "\nAccelZ (g),";
				out.write(w.getBytes());
				for (Float f : accelz) {
					w = "" + f + ",";
					out.write(w.getBytes());
				}
				w = "\n";
				out.write(w.getBytes());
				out.close();
			}
			catch (IOException e) {
				e.printStackTrace();
				postNotification("Failed to aquire external storage. Please report this failure!");
				return;
			}
		}
		
		void doHttpPost() {
			/*final UUID xUUID = UUID.fromString("c924844d-b86e-4f34-a0e4-5cf115471bcc");
			final UUID yUUID = UUID.fromString("2e0d2893-f6d0-4b0c-bfcc-3ac7f4df3557");
			final UUID zUUID = UUID.fromString("a5614559-5a25-486a-aaac-fd6ac985e897");*/
			
			final UUID xUUID = UUID.fromString("2a26c468-bb61-4981-8ccb-24a40e85b41e");
			final UUID yUUID = UUID.fromString("1fc8a314-ca47-4376-8aa4-bf77237dd809");
			final UUID zUUID = UUID.fromString("b792a01e-94e7-4a14-bdff-a5d82a893802");
			
			//final int pid = 997;
			final int pid = getPatientId();
			
			String domain = "Chunk1";
			//String url = "http://aws1-suzdj8btkn.elasticbeanstalk.com/upload";
			//String url = "http://192.168.2.104/~taj/log-data.php";
			//String url = "http://192.168.2.32:8080/upload";
			String url = "http://dataserv.basementserver.org/upload";
			Log.i("ServiceGpsAccelTracker", "Trying to post data");
			
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(url);
			
			String data = "";
			data += buildDataString(accelx, xUUID, pid);
			data += buildDataString(accely, yUUID, pid);
			data += buildDataString(accelz, zUUID, pid);
			
			//data = buildDataStringNew(accelx, accely, accelz);
			
			try {
				List<NameValuePair> nvp = new ArrayList<NameValuePair>(2);
				
				nvp.add(new BasicNameValuePair("domain", domain));
				nvp.add(new BasicNameValuePair("data", data));
				httppost.setEntity(new UrlEncodedFormEntity(nvp));
				
				HttpResponse r = httpclient.execute(httppost);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		String buildDataStringNew(LinkedList<Float> x, LinkedList<Float> y, LinkedList<Float> z) {
			String data = "";
			
			ListIterator<Float> xi = x.listIterator();
			ListIterator<Float> yi = y.listIterator();
			ListIterator<Float> zi = z.listIterator();
			ListIterator<Long>  t = accelt.listIterator();
			
			while (t.hasNext()) {
				float xc = xi.next();
				float yc = yi.next();
				float zc = zi.next();
				long tc = t.next();
				data += "" + tc + "," + xc + "," + yc + "," + zc + "\n";
			}
			
			return data;
		}
		
		String buildDataString(LinkedList<Float> l, UUID u, int pid) {
			//build the data string
			String data = "";
			ListIterator<Float> a = l.listIterator();
			ListIterator<Long>  t = accelt.listIterator();
			while (a.hasNext()) {
				float ac = a.next();
				long tc = t.next();
				data += "" + tc/1000 + " " + pid + " " + u + " " + ac + "\r\n";
			}
			return data;
		}
		
		@Override
		protected String doInBackground(String... params) {
			//Attempt to write the data to the file
			writeFile();
			//do the http post
			doHttpPost();
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			//destroy references to allow garbage collection
			accelx = null;
			accely = null;
			accelz = null;
			accelt = null;
			
			super.onPostExecute(result);
		}
		
	}
}
