package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.message.BasicNameValuePair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.*;
import android.util.Log;

/**
 * Receiver that handles wifi scan completions and 
 * saves data for those scan events
 */
public class WiFiScanReceiver extends BroadcastReceiver implements ActivitySensor {

	private static final int SCAN_PERIOD = 5; /* scan every 5 seconds */
	private static final UUID rssiUUID = UUID.fromString("8f7aa070-464d-11e1-b86c-0800200c9a66");
	
	private ReentrantLock dataLock = new ReentrantLock();
	
	private ArrayList<BasicNameValuePair> rssiVals = null;
	private ArrayList<Long> rssiTimes = null;
	
	private Timer scanTimer = null;
	
	private boolean isScanning;
	
	private WifiManager mWifiManager = null;
	private ActivityTrackerService parentService;
	
	/**
	 * Creates a new wifiScanReceiver linked to the specified service
	 * @param serv Service to link to this receiver
	 */
	public WiFiScanReceiver(ActivityTrackerService serv) {
		parentService = serv;
		mWifiManager = serv.wifi;
		
		isScanning = false;
		
		rssiVals = new ArrayList<BasicNameValuePair>();
		rssiTimes = new ArrayList<Long>();
		
		parentService.registerReceiver(this, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		
		/* start wifi scan timer */
		scanTimer = new Timer();
        
		/* initiate a new scan every X seconds */
		scanTimer.scheduleAtFixedRate( new TimerTask() {
            public void run() {
        		/* request a wifi scan if one is not already requested. */
            	if(isScanning == false) 
            	{
	        		mWifiManager.startScan();
	        		isScanning = true;
            	}
             }
          }, 0, SCAN_PERIOD*1000);
	}

	@Override
	public void onReceive(Context c, Intent intent) {
		List<ScanResult> results = mWifiManager.getScanResults();
		isScanning = false;
		
		dataLock.lock(); /* acquire data lock */
		
		/* add each scan results RSSI value to the list */
		for(ScanResult sr : results)
		{
			BasicNameValuePair bnvp = new BasicNameValuePair(sr.BSSID, Integer.toString(sr.level));
			rssiVals.add(bnvp);
			rssiTimes.add((new Date()).getTime());
			Log.i(ActivityTrackerService.TAG, "Received RSSI with BSSID: " + bnvp.getName() + ", Value: " + bnvp.getValue());
		}
		
		dataLock.unlock(); /* release data lock */
	}

	@Override
	public String getDataString()
	{
		final int pid = parentService.getPatientId();
		String data = buildDataString(rssiVals, rssiUUID, pid);
		
		return data;
	}

	@Override
	public void clearData()
	{
		dataLock.lock(); /* acquire data lock */
		
		rssiVals.clear();
		rssiTimes.clear();
		
		dataLock.unlock(); /* release data lock */
	}

	@Override
	public void unregister()
	{
		parentService.unregisterReceiver(this);
	}
	
	private String buildDataString(ArrayList<BasicNameValuePair> list, UUID u, int pid) {
		dataLock.lock(); /* acquire data lock: we don't want data changing while we are reading it! */

		String data = "";
		for(int i = 0; i < list.size(); i++)
		{
			BasicNameValuePair bnvp = list.get(i);
			String val = bnvp.getName() + "/" + bnvp.getValue();
			long time = 0;
			if( rssiTimes.size() > i )
			{
				time = rssiTimes.get(i);
				data += "" + time/1000 + " " + pid + " " + u + " " + val + "\r\n";
			}
			else
			{
				Log.w(ActivityTrackerService.TAG, "Warning: more RSSI data points than timestamps. Disregarding data point.");
			}
			
		}
		
		dataLock.unlock(); /* release data lock */
		
		return data;
	}
}