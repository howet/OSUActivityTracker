package edu.oregonstate.biomed.actigps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;
import com.androidplot.Plot;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityTrackerActivity extends Activity {
    /** Called when the activity is first created. */
	private DataUpdateReceiver dataUpdateReceiver;
	
	private Timer mBackgroundTimer;
	private XYPlot dataPlot; 
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /* Set up tabbed view */
        setContentView(R.layout.main);
        
        dataPlot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
        
        TabHost tabHost=(TabHost)findViewById(R.id.tabHost);
        tabHost.setup();
        
        TabSpec spec0=tabHost.newTabSpec("Tab 0");
        spec0.setIndicator("Visualization");
        spec0.setContent(R.id.visualTab);
        
        TabSpec spec1=tabHost.newTabSpec("Tab 1");
        spec1.setContent(R.id.dataTab);
        spec1.setIndicator("Data");
        
        TabSpec spec2=tabHost.newTabSpec("Tab 2");
        spec2.setIndicator("Settings");
        spec2.setContent(R.id.settingsTab);
        
        tabHost.addTab(spec0);
        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        
    	/* update the ui based on saved setting on start */
        updateSettings();  
        
        mBackgroundTimer = new Timer();
        
        mBackgroundTimer.scheduleAtFixedRate( new HttpGetTimerTask(), 0, 30*1000);
    }
    
    @Override
    public void onResume() {
    	super.onRestart();
    	
    	if (dataUpdateReceiver == null) dataUpdateReceiver = new DataUpdateReceiver();
    	IntentFilter intentFilter = new IntentFilter("PHONE_ACCEL_UPDATE");
    	registerReceiver(dataUpdateReceiver, intentFilter);
    	intentFilter = new IntentFilter("PHONE_GPS_UPDATE");
    	registerReceiver(dataUpdateReceiver, intentFilter);
    	intentFilter = new IntentFilter("PHONE_GYRO_UPDATE");
    	registerReceiver(dataUpdateReceiver, intentFilter);
    	
    	Toast.makeText(ActivityTrackerActivity.this, "Resumed",
				Toast.LENGTH_SHORT).show();
    }
    
    public void onPause() {
    	super.onPause();
    	try {
    		if (dataUpdateReceiver != null) unregisterReceiver(dataUpdateReceiver);
    	}
    	catch (IllegalArgumentException ex) {
    		// catch if the receiver is not registered
    	}
    }
    
    public void onDestroy() {
    	super.onDestroy();
    	try {
    		if (dataUpdateReceiver != null) unregisterReceiver(dataUpdateReceiver);
    	}
    	catch (IllegalArgumentException ex) {
    		// catch if the receiver is not registered
    	}
    }    
    
    /*
     * Service related things
     */
    
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder binder) {
			Toast.makeText(ActivityTrackerActivity.this, "Connected to service",
					Toast.LENGTH_SHORT).show();
		}

		public void onServiceDisconnected(ComponentName className) {
			Toast.makeText(ActivityTrackerActivity.this, "Disconnected from service",
					Toast.LENGTH_SHORT).show();
		}
	};
	
	private void doBindService() {
		bindService(new Intent(this, ActivityTrackerService.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}
	
	private boolean isMyServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (ActivityTrackerService.class.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	
	public void onClickStartService(View v) {
		/* only create a new service if one did not already exist */
		if(isMyServiceRunning() == false)
		{
	        doBindService();
			startService(new Intent(this, ActivityTrackerService.class));
		}
	}
	
	public void onClickStopService(View v) {
		if(isMyServiceRunning())
		{
			try {
				unbindService(mConnection);
	    	}
	    	catch (IllegalArgumentException ex) {
	    		// catch if the receiver is not registered
	    	}
	    	
	    	/* even if we were not bound to service, it might still be running, so kill it. */
			stopService(new Intent(this, ActivityTrackerService.class));
		}
	}

	
	public void onClickCommitSettings(View v) {
		/* save service running state */
		boolean wasRunning = isMyServiceRunning();
		
		/* stop the current service before applying settings */
		onClickStopService(v);
		
		boolean wifienabled = ((CheckBox)findViewById(R.id.chkbox_wifiscan)).isChecked();
		boolean gpsenabled = ((CheckBox)findViewById(R.id.chkbox_gpsscan)).isChecked();
		boolean accelenabled = ((CheckBox)findViewById(R.id.chkbox_accel)).isChecked();
		boolean gyroenabled = ((CheckBox)findViewById(R.id.chkbox_gyro)).isChecked();
		
		/* get the text from the textbox, covert it to a positive int representing push interval */
		int pushinterval = Math.abs(Integer.parseInt(((EditText)findViewById(R.id.txt_pushInterval)).getText().toString()));
		
		/* setup the new settings */
	    SharedPreferences settings = getSharedPreferences(ActivityTrackerService.PREFS_NAME, 0);
	    SharedPreferences.Editor editor = settings.edit();
	    
	    /* check if the setting already was set to the correct value.  
	     * Default value is the opposite of desired value, 
	     * so setting will be written if no setting was present. */
	    if(settings.getBoolean(ActivityTrackerService.SETTINGS_WIFI_ENABLE_KEY, !wifienabled) != wifienabled)
	    	editor.putBoolean(ActivityTrackerService.SETTINGS_WIFI_ENABLE_KEY, wifienabled);
	    
	    if(settings.getBoolean(ActivityTrackerService.SETTINGS_GPS_ENABLE_KEY, !gpsenabled) != gpsenabled)
	    	editor.putBoolean(ActivityTrackerService.SETTINGS_GPS_ENABLE_KEY, gpsenabled);
	    
	    if(settings.getBoolean(ActivityTrackerService.SETTINGS_ACCEL_ENABLE_KEY, !accelenabled) != accelenabled)
	    	editor.putBoolean(ActivityTrackerService.SETTINGS_ACCEL_ENABLE_KEY, accelenabled);
	    
	    if(settings.getBoolean(ActivityTrackerService.SETTINGS_GYRO_ENABLE_KEY, !gyroenabled) != gyroenabled)
	    	editor.putBoolean(ActivityTrackerService.SETTINGS_GYRO_ENABLE_KEY, gyroenabled);
	    
	    if(settings.getInt(ActivityTrackerService.SETTINGS_PUSH_INTERVAL_KEY, -1) != pushinterval)
	    	editor.putInt(ActivityTrackerService.SETTINGS_PUSH_INTERVAL_KEY, pushinterval);
	    
	    /* commit changes to settings */
	    editor.commit();
	    
	    /* only start the service if it was running before */
	    if( wasRunning == true )
	    	onClickStartService(v);
	}

	private void updateSettings(){
		SharedPreferences settings = getSharedPreferences(ActivityTrackerService.PREFS_NAME, 0);
		boolean wifienabled = settings.getBoolean(ActivityTrackerService.SETTINGS_WIFI_ENABLE_KEY, true);
		boolean gpsenabled = settings.getBoolean(ActivityTrackerService.SETTINGS_GPS_ENABLE_KEY, true);
		boolean accelenabled = settings.getBoolean(ActivityTrackerService.SETTINGS_ACCEL_ENABLE_KEY, true);
		boolean gyroenabled = settings.getBoolean(ActivityTrackerService.SETTINGS_GYRO_ENABLE_KEY, true);
		int pushinterval = settings.getInt(ActivityTrackerService.SETTINGS_PUSH_INTERVAL_KEY, 30);
		
		/* update the checked state of the checkboxes */
		((CheckBox)findViewById(R.id.chkbox_wifiscan)).setChecked(wifienabled);
		((CheckBox)findViewById(R.id.chkbox_gpsscan)).setChecked(gpsenabled);
		((CheckBox)findViewById(R.id.chkbox_accel)).setChecked(accelenabled);
		((CheckBox)findViewById(R.id.chkbox_gyro)).setChecked(gyroenabled);
		
		/* update textboxes */
		((EditText)findViewById(R.id.txt_pushInterval)).setText(String.valueOf(pushinterval));
	}
	
	private class DataUpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context ctx, Intent intent) {
			if (intent.getAction().equals("PHONE_ACCEL_UPDATE")) {
				Bundle data = intent.getExtras();
				String s = data.getString("x")+","+data.getString("y")+","+data.getString("z");
				String t = data.getString("t");
				TextView txt = (TextView) findViewById(R.id.accel_data);
				TextView time = (TextView) findViewById(R.id.time_data);
				txt.setText(s);
				time.setText(t);
			}
			else if (intent.getAction().equals("PHONE_GPS_UPDATE")) {
				Bundle data = intent.getExtras();
				String s = data.getString("x")+","+data.getString("y");
				TextView txt = (TextView) findViewById(R.id.gps_data);
				txt.setText(s);
			}
			else if (intent.getAction().equals("PHONE_GYRO_UPDATE")) {
				Bundle data = intent.getExtras();
				String s = data.getString("x")+","+data.getString("y")+","+data.getString("z");
				TextView txt = (TextView) findViewById(R.id.gyro_data);
				txt.setText(s);
			}
		}
		
	}
	
	/* HTTP related things */
	private class HttpGetTimerTask extends TimerTask {
    	private Handler mHandler = new Handler(Looper.getMainLooper());

        @Override
        public void run() {
           mHandler.post(new Runnable() {
              public void run() {
              	/* run accelerometer and gyro on own threads: these take awhile */
              	Log.d(ActivityTrackerService.TAG, "Starting HTTP Posts.");
              	
              	new HttpGetTask().execute(20);
              }
           });
         }
	}
	
	/* create an async task to handle queuing of HTTP GETs on separate threads */
	private class HttpGetTask extends AsyncTask<Integer, Integer, ArrayList<DataPoint>> {
		protected ArrayList<DataPoint> doInBackground(Integer... limit) {
			int count = limit.length;
			
			if(count == 0)
				return new ArrayList<DataPoint>();

			return doHttpGetAccelData(limit[0]);
		}
		 
		protected void onPostExecute(ArrayList<DataPoint> result) {
	 
			Log.i(ActivityTrackerService.TAG, "Starting graph layout");
	        // create our series from our array of nums:
	        ActivityXYSeries series2 = new ActivityXYSeries(result);
	 
	        dataPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.WHITE);
	        dataPlot.getGraphWidget().getGridLinePaint().setColor(Color.BLACK);
	        dataPlot.getGraphWidget().getGridLinePaint().setPathEffect(new DashPathEffect(new float[]{1,1}, 1));
	        dataPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
	        dataPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
	 
	        dataPlot.setBorderStyle(Plot.BorderStyle.SQUARE, null, null);
	        dataPlot.getBorderPaint().setStrokeWidth(1);
	        dataPlot.getBorderPaint().setAntiAlias(false);
	        dataPlot.getBorderPaint().setColor(Color.WHITE);
	 
	        // setup our line fill paint to be a slightly transparent gradient:
	        Paint lineFill = new Paint();
	        lineFill.setAlpha(200);
	        lineFill.setShader(new LinearGradient(0, 0, 0, 250, Color.WHITE, Color.GREEN, Shader.TileMode.MIRROR));
	 
	        LineAndPointFormatter formatter  = new LineAndPointFormatter(Color.rgb(0, 0,0), Color.BLUE, Color.RED);
	        formatter.setFillPaint(lineFill);
	        dataPlot.getGraphWidget().setPaddingRight(2);
	        dataPlot.addSeries(series2, formatter);
	 
	        // draw a domain tick for each year:
	        dataPlot.setDomainStep(XYStepMode.SUBDIVIDE, result.size());
	 
	        // customize our domain/range labels
	        dataPlot.setDomainLabel("Year");
	        dataPlot.setRangeLabel("# of Sightings");
	 
	        // get rid of decimal points in our range labels:
	        dataPlot.setRangeValueFormat(new DecimalFormat("0"));
	 
	        dataPlot.setDomainValueFormat(new MyDateFormat());
	 
	        // by default, AndroidPlot displays developer guides to aid in laying out your plot.
	        // To get rid of them call disableAllMarkup():
	        dataPlot.disableAllMarkup();
		}
	}
	
	
	private class MyDateFormat extends Format {
		  
		// create a simple date format that draws on the year portion of our timestamp.
          // see http://download.oracle.com/javase/1.4.2/docs/api/java/text/SimpleDateFormat.html
          // for a full description of SimpleDateFormat.
          private SimpleDateFormat dateFormat = new SimpleDateFormat("MM/yyyy");
          
          @Override
          public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
              long timestamp = ((Number) obj).longValue();
              Date date = new Date(timestamp);
              return dateFormat.format(date, toAppendTo, pos);
          }

          @Override
          public Object parseObject(String source, ParsePosition pos) {
              return null;

          }

  }
	
	
	/**
	 * Perform the HTTP Get routine and return 
	 * @return
	 */
	private ArrayList<DataPoint> doHttpGetAccelData(int limit) {			
		ArrayList<DataPoint> fetchedData = new ArrayList<DataPoint>();
		
		Log.i(ActivityTrackerService.TAG, "Trying to get accel data");
		
		String query = "user=" + ActivityTrackerService.UPLOAD_UID + "&channel=Activity_Data&limit=" + limit;// + "&since=" + (new Date()).getTime();
		
		HttpGet httpget;
		try
		{
			httpget = new HttpGet(new URI("http", ActivityTrackerService.DOWNLOAD_HOST, ActivityTrackerService.DOWNLOAD_PATH, query, null));
			

		} catch (URISyntaxException e1)
		{
			return fetchedData;
		}

		
		try {
	
			HttpClient client = HttpClientFactory.getThreadSafeClient();
			HttpResponse r = client.execute(httpget);
			
			Log.d(ActivityTrackerService.TAG, "Http response: " + r.getStatusLine().toString());
			
			if( r.getStatusLine().toString().contains("200 OK"))
			{
				BufferedReader in = new BufferedReader(new InputStreamReader(r.getEntity().getContent()));
	            String line = "";
	            String[] parsed;
	            
	            /* parse each line of response for timestamp:value tuples */
	            while ((line = in.readLine()) != null) {
	            	parsed = line.split(":");
	            	fetchedData.add(new DataPoint(Long.parseLong(parsed[0]), Double.parseDouble(parsed[1])));
	            }
	            in.close();
			}
		}
		catch (IOException e) 
		{
			Log.e(ActivityTrackerService.TAG, "Exception Occurred on HTTP post: " + e.toString());
		}
		
		return fetchedData;
	}
}