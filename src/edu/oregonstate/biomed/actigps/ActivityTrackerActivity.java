package edu.oregonstate.biomed.actigps;

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
import android.os.Bundle;
import android.os.IBinder;
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
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /* Set up tabbed view */
        setContentView(R.layout.main);
        
        TabHost tabHost=(TabHost)findViewById(R.id.tabHost);
        tabHost.setup();
        
        TabSpec spec1=tabHost.newTabSpec("Tab 1");
        spec1.setContent(R.id.dataTab);
        spec1.setIndicator("Data");
        
        TabSpec spec2=tabHost.newTabSpec("Tab 2");
        spec2.setIndicator("Settings");
        spec2.setContent(R.id.settingsTab);
        
        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        
    	/* update the ui based on saved setting on start */
        updateSettings();        
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
    
}