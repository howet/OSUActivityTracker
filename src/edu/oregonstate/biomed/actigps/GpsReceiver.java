package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
//import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

public class GpsReceiver implements LocationListener, ActivitySensor
{	
	private ReentrantLock dataLock = new ReentrantLock();
	
	private ArrayList<Location> gpsLocs = null;
	private ArrayList<Location> prevLocs = null;

	private ActivityTrackerService parentService = null;
	private LocationManager mlocManager = null;
	
	/* 
	 * boolean to keep track of if data is currently being posted,
	 * which would require a clearData or restoreData call.
	 */
	private boolean dataPosting;
	
	/**
	 * Creates a new GpsReceiver linked to the specified service
	 * @param serv Service to link to this receiver
	 */
	public GpsReceiver(ActivityTrackerService serv)
	{
		parentService = serv;
		mlocManager = serv.location;
		
		gpsLocs = new ArrayList<Location>();
	}
		
	@SuppressWarnings("unchecked")
	@Override
	public String getDataString()
	{
		if ( dataPosting == false)
		{
			dataLock.lock(); /* acquire data lock: we don't want data changing while we are reading it! */
			
			prevLocs = (ArrayList<Location>) gpsLocs.clone();
			
			/* clear data up to this point */
			gpsLocs.clear();
			
			if(prevLocs.size() > 0)
				dataPosting = true;
			
			dataLock.unlock(); /* release data lock */
			
			return buildDataString(prevLocs);
		}
		return "";
	}

	
	@Override
	public void clearData()
	{
		if(prevLocs != null)
			prevLocs.clear();
		
		/* since we are clearing, that probably means we won't have any problems for awhile, 
		 * so we can GC the ArrayList */
		prevLocs = null;
		dataPosting = false;
	}

	@Override
	public void register()
	{
		/* register self for location updates every 2 meters */
		mlocManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 0, 2, this);
	}
	
	@Override
	public void unregister()
	{
		mlocManager.removeUpdates(this);
	}

	
	@Override
	public void onLocationChanged(Location location)
	{
		//Log.i(ActivityTrackerService.TAG, "GPS Location received: " + location.getLatitude() + "," + location.getLongitude());
		
		dataLock.lock(); /* acquire data lock */
		
		gpsLocs.add(location);
		
		dataLock.unlock(); /* release data lock */
		
		Intent i = new Intent("PHONE_GPS_UPDATE");
		Bundle b = new Bundle();
		b.putString("x", Double.toString(location.getLatitude()));
		b.putString("y", Double.toString(location.getLongitude()));
		i.putExtras(b);
		parentService.sendBroadcast(i);
	}

	
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		/* if this isnt a GPS warning, then we don't care */
		if( status == LocationProvider.OUT_OF_SERVICE 
		 && provider == LocationManager.GPS_PROVIDER )
			Log.w(ActivityTrackerService.TAG, "Warning: GPS out of service");
	}

	
	@Override
	public void onProviderEnabled(String provider)
	{
		if( provider == LocationManager.GPS_PROVIDER )
			Log.d(ActivityTrackerService.TAG, "GPS Enabled");
	}

	
	@Override
	public void onProviderDisabled(String provider)
	{
		if( provider == LocationManager.GPS_PROVIDER )
			Log.w(ActivityTrackerService.TAG, "Warning: GPS Disabled");
	}

	
	/**
	 * Create a string for the HTTP post based on location data and pid
	 * @param list of raw gps locations
	 * @return formatted string
	 */
	private String buildDataString(ArrayList<Location> list) {
		StringBuilder data = new StringBuilder();
		for(int i = 0; i < list.size(); i++)
		{
			Location loc = list.get(i);
			double lati = loc.getLatitude();
			double longi = loc.getLongitude();
			long time = loc.getTime();

			/* format data as <timestamp>:<value>\r\n" */
			data.append(String.format("%d:%f,%f\r\n", time,lati,longi));
		}
		
		return data.toString();
	}

	
	@Override
	public String getChannelName()
	{
		return "GPS_Data";
	}

	@Override
	public void restoreData()
	{
		dataLock.lock(); /* acquire data lock */
		
		/* restore the previously fetched data */
		if( prevLocs != null )
			gpsLocs.addAll(prevLocs);
		
		dataLock.unlock(); /* release data lock */
		
		prevLocs = null;
		dataPosting = false;
	}
}
