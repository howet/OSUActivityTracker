package edu.oregonstate.biomed.actigps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.Toast;

public class ActivityTrackerActivity extends Activity {
	
	private Timer mBackgroundTimer;
	
	/* chart variables */
	private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();
	private XYSeries mCurrentSeries;
	private XYSeriesRenderer mCurrentRenderer;
	private String mDateFormat;
	private GraphicalView mChartView;
	
	  private static final long HOUR = 3600 * 1000;

	  private static final long DAY = HOUR * 24;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /* Set up tabbed view */
        setContentView(R.layout.main);
        
        TabHost tabHost=(TabHost)findViewById(R.id.tabHost);
        tabHost.setup();
        
        TabSpec spec1=tabHost.newTabSpec("Tab 1");
        spec1.setIndicator("Visualization");
        spec1.setContent(R.id.chart);
        
        TabSpec spec2=tabHost.newTabSpec("Tab 2");
        spec2.setIndicator("Settings");
        spec2.setContent(R.id.settingsTab);

        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        
        /* create chart stuff */
        setChartSettings("Activity Data", "Timestamp", "Activity Level", new Date().getTime() - (7 * DAY), 
        		new Date().getTime() - (7 * DAY), -5, 30, Color.LTGRAY, Color.LTGRAY);

        XYSeries series = new XYSeries("Data");
        mDataset.addSeries(series);
        
        mCurrentSeries = series;
        
        XYSeriesRenderer renderer = new XYSeriesRenderer();
        renderer.setPointStyle(PointStyle.CIRCLE);
        renderer.setFillPoints(true);

        mRenderer.addSeriesRenderer(renderer);
        
        mCurrentRenderer = renderer;

        if (mChartView != null) {
          mChartView.repaint();
        }
        
    	/* update the ui based on saved setting on start */
        updateSettings();  
        
        mBackgroundTimer = new Timer();
        
        mBackgroundTimer.scheduleAtFixedRate( new HttpGetTimerTask(), 0, 30*1000);
        
        /* start the background service when the app is launched */
        startService();
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedState) {
      super.onRestoreInstanceState(savedState);
      mDataset = (XYMultipleSeriesDataset) savedState.getSerializable("dataset");
      mRenderer = (XYMultipleSeriesRenderer) savedState.getSerializable("renderer");
      mCurrentSeries = (XYSeries) savedState.getSerializable("current_series");
      mCurrentRenderer = (XYSeriesRenderer) savedState.getSerializable("current_renderer");
      mDateFormat = savedState.getString("date_format");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
      super.onSaveInstanceState(outState);
      outState.putSerializable("dataset", mDataset);
      outState.putSerializable("renderer", mRenderer);
      outState.putSerializable("current_series", mCurrentSeries);
      outState.putSerializable("current_renderer", mCurrentRenderer);
      outState.putString("date_format", mDateFormat);
    }
    
    @Override
    public void onResume() {
    	super.onRestart();
    	
    	/* create chartview */
        if (mChartView == null) {
            LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
            mChartView = ChartFactory.getLineChartView(this, mDataset, mRenderer);
            mRenderer.setClickEnabled(true);
            mRenderer.setSelectableBuffer(100);
            mChartView.setOnClickListener(new View.OnClickListener() {
              @Override
              public void onClick(View v) { }
            });

            layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
          } else {
            mChartView.repaint();
          }
    	
    	
    	Toast.makeText(ActivityTrackerActivity.this, "Resumed",
				Toast.LENGTH_SHORT).show();
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
	
	
	/**
	 * Checks to see if a service is running by parsing the service list
	 * @return True if the background service is running; False otherwise
	 */
	private boolean isMyServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (ActivityTrackerService.class.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	/* button handler */
	public void onClickStartService(View v) {
		startService();
	}
	
	private void startService()
	{
		/* only create a new service if one did not already exist */
		if(isMyServiceRunning() == false)
		{
	        doBindService();
			startService(new Intent(this, ActivityTrackerService.class));
		}
	}
	
	/* button handler */
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
	
	  /**
	   * Sets a few of the series renderer settings.
	   * 
	   * @param renderer the renderer to set the properties to
	   * @param title the chart title
	   * @param xTitle the title for the X axis
	   * @param yTitle the title for the Y axis
	   * @param xMin the minimum value on the X axis
	   * @param xMax the maximum value on the X axis
	   * @param yMin the minimum value on the Y axis
	   * @param yMax the maximum value on the Y axis
	   * @param axesColor the axes color
	   * @param labelsColor the labels color
	   */
	  protected void setChartSettings(String title, String xTitle,
	      String yTitle, double xMin, double xMax, double yMin, double yMax, int axesColor,
	      int labelsColor) {
		  
			mRenderer.setApplyBackgroundColor(true);
			mRenderer.setBackgroundColor(Color.argb(100, 50, 50, 50));
			mRenderer.setAxisTitleTextSize(16);
			mRenderer.setChartTitleTextSize(20);
			mRenderer.setLabelsTextSize(15);
			mRenderer.setLegendTextSize(15);
			mRenderer.setMargins(new int[] { 20, 30, 15, 0 }); 
			mRenderer.setZoomButtonsVisible(true);
			mRenderer.setPointSize(10);
			    
			mRenderer.setChartTitle(title);
			mRenderer.setXTitle(xTitle);
			mRenderer.setYTitle(yTitle);
			
			setChartAxes(xMin, xMax, -5, 30, 5);

			mRenderer.setAxesColor(axesColor);
			mRenderer.setLabelsColor(labelsColor);
			mRenderer.setShowGrid(true);
	  }
	  
	  private void setChartAxes(double xMin, double xMax, double yMin, double yMax, int count)
	  {
		  mRenderer.setXAxisMin(xMin);
		  mRenderer.setXAxisMax(xMax);
	      mRenderer.setYAxisMin(yMin);
		  mRenderer.setYAxisMax(yMax);
		  
		  mRenderer.setXLabels(count);
		  mRenderer.setYLabels(count);
		  mRenderer.setXLabelsAlign(Align.CENTER);
		  mRenderer.setYLabelsAlign(Align.RIGHT);
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
              	
              	new HttpGetTask().execute(100);
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
		 
		protected void onPostExecute(ArrayList<DataPoint> result) 
		{
			double x;
			double y;
			
			double minX = -1;
			double maxX = 0;
			double minY = -1;
			double maxY = 0;
	        
			mCurrentSeries.clear();
			
			for(DataPoint val : result)
			{
				x = val.getX();
				
				/* keep track of max and min x for setting axis */
				if( x < minX || minX < 0)
					minX = x;
				else if (x > maxX)
					maxX = x;
				
				y = val.getY();
				/* keep track of max and min y for setting axis */
				if( y < minY || minY < 0)
					minY = y;
				else if (y > maxX)
					maxX = y;
				
		        mCurrentSeries.add(x, y);
			}

			setChartAxes(minX, maxX, minY - 1, maxY + 1, 5);
	        
	        if (mChartView != null) {
	            mChartView.repaint();
	        }
		}
	}
	
	
	/**
	 * Perform the HTTP Get routine and return 
	 * @return
	 */
	private ArrayList<DataPoint> doHttpGetAccelData(int limit) {			
		ArrayList<DataPoint> fetchedData = new ArrayList<DataPoint>();
		
		Log.i(ActivityTrackerService.TAG, "Trying to get accel data");
		
		/* get all queries in the past day.  TODO: get rid of this once queries can be sorted by timestamp */
		long sincetime = ((new Date()).getTime() - HOUR);
		String query = "user=" + ActivityTrackerService.UPLOAD_UID + "&channel=Activity_Data&limit=" + limit + "&since=" + sincetime;
		
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
	            	Log.d(ActivityTrackerService.TAG, "Time: " + parsed[0] + "    Value: " + parsed[1]);
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