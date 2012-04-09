package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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

	private static final int SCAN_PERIOD = 6; /* scan every 6 seconds */	
	private ReentrantLock dataLock = new ReentrantLock();
	
	private ArrayList<BasicNameValuePair> rssiVals = null;
	private ArrayList<Long> rssiTimes = null;
	
	private ArrayList<BasicNameValuePair> prevVals = null;
	private ArrayList<Long> prevTimes = null;
	
	private Timer scanTimer = null;
	
	private boolean isScanning;
	private boolean isRegistered;
	
	/* 
	 * boolean to keep track of if data is currently being posted,
	 * which would require a clearData or restoreData call.
	 */
	private boolean dataPosting;
	
	private WifiManager mWifiManager = null;
	private ActivityTrackerService parentService;
	
	/**
	 * Creates a new wifiScanReceiver linked to the specified service
	 * @param serv Service to link to this receiver
	 */
	public WiFiScanReceiver(ActivityTrackerService serv) {
		parentService = serv;
		mWifiManager = serv.wifi;
		
		/* wifi scan timer */
		scanTimer = new Timer();
		isScanning = false;
		isRegistered = false;
		dataPosting = false;
		
		rssiVals = new ArrayList<BasicNameValuePair>();
		rssiTimes = new ArrayList<Long>();
	}
	
	
	/**
	 * Registers the WiFi receiver
	 */
	public void register()
	{
		parentService.registerReceiver(this, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		
		/* start wifi scan timer */
		scanTimer = new Timer();
		isRegistered = true;
		
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
			//Log.i(ActivityTrackerService.TAG, "Received RSSI with BSSID: " + bnvp.getName() + ", Value: " + bnvp.getValue());
		}
		
		dataLock.unlock(); /* release data lock */
	}

	@SuppressWarnings("unchecked")
	@Override
	public String getDataString()
	{
		/* return nothing if we are waiting for a data post */
		if( dataPosting == false )
		{
			dataLock.lock(); /* acquire data lock: we don't want data changing while we are reading it! */
			
			prevVals = (ArrayList<BasicNameValuePair>) rssiVals.clone();
			prevTimes = (ArrayList<Long>) rssiTimes.clone();
			
			/* clear data up to this point */
			rssiVals.clear();
			rssiTimes.clear();
			
			if(prevVals.size() > 0)
				dataPosting = true;
			
			dataLock.unlock(); /* release data lock */

			return buildDataString(prevVals, prevTimes);
		}
		return "";
	}

	@Override
	public void clearData()
	{
		if( prevVals != null )
			prevVals.clear();
		if( prevTimes != null )
			prevTimes.clear();
		
		/* since we are clearing, that probably means we won't have any problems for awhile, 
		 * so we can GC the ArrayLists */
		prevVals = null;
		prevTimes = null;
		dataPosting = false;
	}

	@Override
	public void unregister()
	{
		scanTimer.cancel();
		
		if(isRegistered == true)
			parentService.unregisterReceiver(this);
		
		isRegistered = false;
	}
	
	/**
	 * Create a string for the HTTP post based on data and timestamps
	 * @param list raw rssi data
	 * @param times array of timestamps matching data
	 * @return formatted string
	 */
	private String buildDataString(ArrayList<BasicNameValuePair> list, ArrayList<Long> times) {
		StringBuilder data = new StringBuilder();
		for(int i = 0; i < list.size(); i++)
		{
			BasicNameValuePair bnvp = list.get(i);
			String val = bnvp.getName() + "/" + bnvp.getValue();
			long time = 0;
			if( times.size() > i )
			{
				time = times.get(i);
				data.append(String.format("%d:%s\r\n", time, val));
			}
			else
			{
				Log.w(ActivityTrackerService.TAG, "Warning: more RSSI data points than timestamps. Disregarding data point.");
			}
			
		}
		
		return data.toString();
	}


	@Override
	public String getChannelName()
	{
		return "WiFi_Data";
	}


	@Override
	public void restoreData()
	{
		dataLock.lock(); /* acquire data lock */
		
		/* restore the previously fetched data */
		if( prevVals != null )
			rssiVals.addAll(prevVals);
		if( prevTimes != null )
			rssiTimes.addAll(prevTimes);
		
		dataLock.unlock(); /* release data lock */
		
		prevVals = null;
		prevTimes = null;
		
		dataPosting = false;
	}
}