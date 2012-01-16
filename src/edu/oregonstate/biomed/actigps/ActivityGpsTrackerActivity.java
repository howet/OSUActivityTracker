package edu.oregonstate.biomed.actigps;

import java.util.LinkedList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class ActivityGpsTrackerActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        doBindService();
        
    }
    
    
    /*
     * Service related things
     */
    private ServiceGpsTracker serv = null;
    
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder binder) {
			serv = ((ServiceGpsTracker.TrackerBinder) binder).getTracker();
			Toast.makeText(ActivityGpsTrackerActivity.this, "Connected to service",
					Toast.LENGTH_SHORT).show();
		}

		public void onServiceDisconnected(ComponentName className) {
			serv = null;
		}
	};
	
	private void doBindService() {
		bindService(new Intent(this, ServiceGpsTracker.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}
	
	public void onClickStartService(View v) {
		startService(new Intent(this, ServiceGpsTracker.class));
	}
	
	public void onClickStopService(View v) {
		stopService(new Intent(this, ServiceGpsTracker.class));
	}
	
	public void onClickGetData(View v) {
		if (serv != null) {
			/*LinkedList<Long> d = serv.getData();
			long t = d.getFirst();
			Toast.makeText(ActivityGpsTrackerActivity.this, "Got start time of: " + t,
					Toast.LENGTH_SHORT).show();*/
		}
	}
    
}