package edu.oregonstate.biomed.actigps;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityGpsTrackerActivity extends Activity {
    /** Called when the activity is first created. */
	private DataUpdateReceiver dataUpdateReceiver;
	private boolean serviceRunning;
	
	
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
        
        /* we have not started the service yet */
        serviceRunning = false;
        
        
        final EditText patientIdEdit = (EditText)findViewById(R.id.patientId);
        
        patientIdEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
					SharedPreferences.Editor editor = settings.edit();
					
					Toast.makeText(ActivityGpsTrackerActivity.this, "DONE",
							Toast.LENGTH_SHORT).show();
					
					int patientId = getPatientIdContent();
					
					serv.setPatientId(patientId);
					editor.putInt("lastPatientId", patientId);
					editor.commit();
					
					if (serv != null) {
						serv.setPatientId(patientId);
					}
				}
				
				return false;
			}
		});
        
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        
        int nextPatientId = settings.getInt("lastPatientId", 1008) + 1;
        patientIdEdit.setText(""+nextPatientId);        
    }
    
    @Override
    public void onResume() {
    	super.onRestart();
    	
    	if (dataUpdateReceiver == null) dataUpdateReceiver = new DataUpdateReceiver();
    	IntentFilter intentFilter = new IntentFilter("PHONE_ACCEL_UPDATE");
    	registerReceiver(dataUpdateReceiver, intentFilter);
    	intentFilter = new IntentFilter("PHONE_GPS_UPDATE");
    	registerReceiver(dataUpdateReceiver, intentFilter);
    	
    	Toast.makeText(ActivityGpsTrackerActivity.this, "Resumed",
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
    
    private int getPatientIdContent() {
        final EditText patientIdEdit = (EditText)findViewById(R.id.patientId);
        return Integer.parseInt(patientIdEdit.getText().toString());
    }
    
    
    /*
     * Service related things
     */
    private ActivityTrackerService serv = null;
    
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder binder) {
			serv = ((ActivityTrackerService.TrackerBinder) binder).getTracker();
			
			Toast.makeText(ActivityGpsTrackerActivity.this, "Connected to service",
					Toast.LENGTH_SHORT).show();
			
			serv.setPatientId(getPatientIdContent());
		}

		public void onServiceDisconnected(ComponentName className) {
			serv = null;
		}
	};
	
	private void doBindService() {
		bindService(new Intent(this, ActivityTrackerService.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}
	
	
	
	public void onClickStartService(View v) {
        doBindService();
		startService(new Intent(this, ActivityTrackerService.class));
		serviceRunning = true;
	}
	
	public void onClickStopService(View v) {
		try {
			unbindService(mConnection);
			stopService(new Intent(this, ActivityTrackerService.class));
			serviceRunning = false;
    	}
    	catch (IllegalArgumentException ex) {
    		// catch if the receiver is not registered
    	}
	}

	
	public void onClickCommitSettings(View v) {
		/* save service running state */
		boolean wasRunning = serviceRunning;
		
		/* stop the current service before applying settings */
		onClickStopService(v);
		
		boolean wifienabled = ((CheckBox)findViewById(R.id.chkbox_wifiscan)).isChecked();
		boolean gpsenabled = ((CheckBox)findViewById(R.id.chkbox_gpsscan)).isChecked();
		boolean accelenabled = ((CheckBox)findViewById(R.id.chkbox_accel)).isChecked();
		
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
		
		/* update the checked state of the checkboxes */
		((CheckBox)findViewById(R.id.chkbox_wifiscan)).setChecked(wifienabled);
		((CheckBox)findViewById(R.id.chkbox_gpsscan)).setChecked(gpsenabled);
		((CheckBox)findViewById(R.id.chkbox_accel)).setChecked(accelenabled);
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
			if (intent.getAction().equals("PHONE_GPS_UPDATE")) {
				Bundle data = intent.getExtras();
				String s = data.getString("x")+","+data.getString("y");
				TextView txt = (TextView) findViewById(R.id.gps_data);
				txt.setText(s);
			}
		}
		
	}
    
}