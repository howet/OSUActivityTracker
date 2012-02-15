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
import android.widget.TextView;
import android.widget.Toast;

public class ActivityGpsTrackerActivity extends Activity {
    /** Called when the activity is first created. */
	private DataUpdateReceiver dataUpdateReceiver;
	//private Intent serviceIntent = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        final EditText patientIdEdit = (EditText)findViewById(R.id.patientId);
        
        //serviceIntent = new Intent(this, ActivityTrackerService.class);
        
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
        
        doBindService();
        
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
//		if( serviceIntent != null )
//			startService(serviceIntent);
		startService(new Intent(this, ActivityTrackerService.class));
	}
	
	public void onClickStopService(View v) {
		try {
//			if( serviceIntent != null )
//				stopService(serviceIntent);
			unbindService(mConnection);
			stopService(new Intent(this, ActivityTrackerService.class));
    	}
    	catch (IllegalArgumentException ex) {
    		// catch if the receiver is not registered
    	}
		
		stopService(new Intent(this, ActivityTrackerService.class));
	}
	
	public void onClickGetData(View v) {
		if (serv != null) {
			/*LinkedList<Long> d = serv.getData();
			long t = d.getFirst();
			Toast.makeText(ActivityGpsTrackerActivity.this, "Got start time of: " + t,
					Toast.LENGTH_SHORT).show();*/
		}
	}
	
	public void onClickCommitSettings(View v) {
		/* stop the current service before applying settings */
		onClickStopService(v);
		
		boolean wifienabled = ((CheckBox)findViewById(R.id.chkbox_wifiscan)).isChecked();
		
		/* setup the new settings */
	    SharedPreferences settings = getSharedPreferences(ActivityTrackerService.PREFS_NAME, 0);
	    SharedPreferences.Editor editor = settings.edit();
	    
	    //TODO: only commit changes and restart service if settings changed */
	    editor.putBoolean(ActivityTrackerService.SETTINGS_WIFI_ENABLE_KEY, wifienabled);
	    
	    /* commit changes to settings */
	    editor.commit();
	    
	    onClickStartService(v);
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