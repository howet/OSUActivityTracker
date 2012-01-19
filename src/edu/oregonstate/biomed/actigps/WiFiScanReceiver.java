package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.*;

/**
 * Receiver that handles wifi scan completions and 
 * saves data for those scan events
 */
public class WiFiScanReceiver extends BroadcastReceiver implements ActivitySensor {

	private ArrayList<Integer> rssiVals = null;
	private WifiManager mWifiManager = null;
	private ActivityTrackerService parentService;
	
	public WiFiScanReceiver(ActivityTrackerService serv) {
		parentService = serv;
		mWifiManager = serv.wifi;
		
		parentService.registerReceiver(this, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		
		rssiVals = new ArrayList<Integer>();
	}

	@Override
	public void onReceive(Context c, Intent intent) {
		List<ScanResult> results = mWifiManager.getScanResults();
		/* add each scan results RSSI value to the list */
		for(ScanResult sr : results)
		{
			rssiVals.add(sr.level);
		}
	}

	@Override
	public String getDataString()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clearData()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unregister()
	{
		parentService.unregisterReceiver(this);
	}
}