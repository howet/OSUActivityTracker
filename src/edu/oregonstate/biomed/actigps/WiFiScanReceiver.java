package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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

	private final UUID rssiUUID = UUID.fromString("8f7aa070-464d-11e1-b86c-0800200c9a66");
	private ArrayList<BasicNameValuePair> rssiVals = null;
	private ArrayList<Long> rssiTimes = null;
	
	private WifiManager mWifiManager = null;
	private ActivityTrackerService parentService;
	
	public WiFiScanReceiver(ActivityTrackerService serv) {
		parentService = serv;
		mWifiManager = serv.wifi;
		
		parentService.registerReceiver(this, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		
		rssiVals = new ArrayList<BasicNameValuePair>();
		rssiTimes = new ArrayList<Long>();
	}

	@Override
	public void onReceive(Context c, Intent intent) {
		List<ScanResult> results = mWifiManager.getScanResults();
		/* add each scan results RSSI value to the list */
		for(ScanResult sr : results)
		{
			BasicNameValuePair bnvp = new BasicNameValuePair(sr.BSSID, Integer.toString(sr.level));
			rssiVals.add(bnvp);
			rssiTimes.add((new Date()).getTime());
			Log.i(ActivityTrackerService.TAG, "Received RSSI with BSSID: " + bnvp.getName() + ", Value: " + bnvp.getValue());
		}
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
		rssiVals.clear();
		rssiTimes.clear();
	}

	@Override
	public void unregister()
	{
		parentService.unregisterReceiver(this);
	}
	
	private String buildDataString(ArrayList<BasicNameValuePair> list, UUID u, int pid) {
		//build the data string
		String data = "";
		for(int i = 0; i < list.size(); i++)
		{
			BasicNameValuePair bnvp = list.get(i);
			String val = bnvp.getName() + "/" + bnvp.getValue();
			long time = rssiTimes.get(i);
			data += "" + time/1000 + " " + pid + " " + u + " " + val + "\r\n";
		}
		return data;
	}
}