package edu.oregonstate.biomed.actigps;

/*
 * Everything Commented out is for future use as a raw data collector.
 * Functionality has been temporarily changed to be a raw activity factor
 */

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

public class AccelReceiver implements SensorEventListener, ActivitySensor
{
	private ActivityTrackerService parentService = null;
	private SensorManager mSensorManager = null;
	private Sensor mAccelerometer = null;
	
	private ReentrantLock accelDataLock = new ReentrantLock();
	
	/* 
	 * boolean to keep track of if data is currently being posted,
	 * which would require a clearData or restoreData call.
	 */
	private boolean dataPosting;
	
	/* accelerometer data */
//	private ArrayList<SensorVal> accelData = new ArrayList<SensorVal>();
//	private ArrayList<SensorVal> prevData;
	private ArrayList<Double> accelData = new ArrayList<Double>();
	private ArrayList<Double> prevData;
	
	/* only send the broadcast every so often, to limit UI updates */
	private int broadcast_count;
	
	public AccelReceiver(ActivityTrackerService serv)
	{
		parentService = serv;
		mSensorManager = serv.sensors;
		broadcast_count = 0;
		dataPosting = false;
	}
	
	
	@Override
	public void register()
	{
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		// change this back to DELAY_GAME when server can handle it
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public String getDataString()
	{
		String data = "";
		
		/* return nothing if we are waiting for a data post */
		if( dataPosting == false )
		{

			accelDataLock.lock(); /* acquire data lock: we don't want data changing while we are reading it! */
			
			/* copy data to array */
//			prevData = (ArrayList<SensorVal>) accelData.clone();
			prevData = (ArrayList<Double>) accelData.clone();
			
			/* clear data up to this point */
			accelData.clear();
			
			if(prevData.size() > 0)
				dataPosting = true;
			
			accelDataLock.unlock(); /* release data lock */
			
			data = buildDataString(prevData);
		}

		return data;
	}

	@Override
	public void clearData()
	{
		if( prevData != null )
			prevData.clear();
		
		/* since we are clearing, that probably means we won't have any problems for awhile, 
		 * so we can GC the ArrayList */
		prevData = null;
		
		dataPosting = false;
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
//		SensorVal e;
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
//				e = new SensorVal(event);
				float x = event.values[0];
				float y = event.values[1];
				float z = event.values[2];
				double mag = Math.sqrt((x * x) + (y * y) + (z * z));
				
				accelDataLock.lock(); /* acquire data lock */
				//Log.i(ActivityTrackerService.TAG, "Magnitude: " + mag);

				accelData.add(mag);
//				accelData.add(e);	
				
				
				accelDataLock.unlock(); /* release data lock */
				
//				sendBroadcast(e);
				
				break;
			case Sensor.TYPE_GYROSCOPE:
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				break;
			}
	}
	
//	private Intent broadcastIntent = new Intent("PHONE_ACCEL_UPDATE");
//	private Bundle broadcastBundle = new Bundle();
//	
//	private void sendBroadcast(SensorVal event)
//	{
//		/* update UI every 4 data points */
//		if(broadcast_count < 4)
//		{
//			broadcast_count++;
//		}
//		else
//		{
//			broadcast_count = 0;
//			float[] vals = event.getVals();
//			broadcastBundle.putString("x", Float.toString(vals[0]));
//			broadcastBundle.putString("y", Float.toString(vals[1]));
//			broadcastBundle.putString("z", Float.toString(vals[2]));
//			broadcastBundle.putString("t", Long.toString(event.getTime()));
//			broadcastIntent.putExtras(broadcastBundle);
//			parentService.sendBroadcast(broadcastIntent);
//		}
//	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		/* We will ignore this for now... but post a log message about it */
		Log.i(ActivityTrackerService.TAG, "Accelerometer accuracy change");
	}

	@Override
	public void unregister()
	{
		mSensorManager.unregisterListener(this);
	}
	
	/**
	 * Create a string for the HTTP post based on a list of XYZ events
	 * @param events XYZEvents arraylist to be formatted
	 * @return formatted string
	 */
//	private String buildDataString(ArrayList<SensorVal> events) {		
//		
//		/* use of StringBuilder avoids garbage collection when appending to strings */
//		StringBuilder data = new StringBuilder();
//		
//		for(int i = 0; i < events.size(); i++)
//		{
//			data.append(events.get(i).toString());
//		}
//		
//		return data.toString();
//	}
	private String buildDataString(ArrayList<Double> events) {		
		Double avg = 0.0;
		int size = events.size();
		
		for(int i = 0; i < size; i++)
		{
			avg += events.get(i);
		}
		
		/* cancel out neutral accelerometer reading (~10) */
		avg = Math.max((avg / size) - 10, 0);
	
		return String.format("%d:%f\r\n", (new Date()).getTime(), avg);
	}


	@Override
	public String getChannelName()
	{
//		return "Accelerometer_Data";
		return "Activity_Data";
	}


	@Override
	public void restoreData()
	{
		accelDataLock.lock(); /* acquire data lock */
		
		/* restore the previously fetched data */
		if( prevData != null )
			accelData.addAll(prevData);
		
		accelDataLock.unlock(); /* release data lock */
		
		prevData = null;
		dataPosting = false;
	}

}