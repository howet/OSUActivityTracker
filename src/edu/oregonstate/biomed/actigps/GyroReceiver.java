package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

public class GyroReceiver implements ActivitySensor, SensorEventListener
{
	private ActivityTrackerService parentService = null;
	private SensorManager mSensorManager = null;
	private Sensor mGyroscope = null;
	
	private boolean isRegistered;
	
	/* 
	 * boolean to keep track of if data is currently being posted,
	 * which would require a clearData or restoreData call.
	 */
	private boolean dataPosting;
	
	private ReentrantLock dataLock = new ReentrantLock();
	
	/* accelerometer data */
	private ArrayList<SensorVal> gyroData = new ArrayList<SensorVal>();
	private ArrayList<SensorVal> prevData;
	
	/* only send the broadcast every so often, to limit UI updates */
	private int broadcast_count;
	
	public GyroReceiver(ActivityTrackerService serv)
	{
		parentService = serv;
		mSensorManager = serv.sensors;
		broadcast_count = 0;
		isRegistered = false;
		dataPosting = false;
	}
	
	
	@Override
	public void register()
	{
		mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		
		mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
		isRegistered = true;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public String getDataString()
	{
		String data = "";
		/* return nothing if we are waiting for a data post */
		if( dataPosting == false )
		{

			dataLock.lock(); /* acquire data lock: we don't want data changing while we are reading it! */
			
			/* copy data to array */
			prevData = (ArrayList<SensorVal>) gyroData.clone();
			
			/* clear data up to this point */
			gyroData.clear();
			
			if(prevData.size() > 0)
				dataPosting = true;
			
			dataLock.unlock(); /* release data lock */
			
			data = buildDataString(prevData);		
		}
		
		return data;
	}

	@Override
	public void clearData()
	{
		if(prevData != null)
			prevData.clear();
		
		/* since we are clearing, that probably means we won't have any problems for awhile, 
		 * so we can GC the ArrayList */
		prevData = null;
		dataPosting = false;
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		SensorVal e;
		switch (event.sensor.getType()) {
			case Sensor.TYPE_GYROSCOPE:
				e = new SensorVal(event);
				dataLock.lock(); /* acquire data lock */
				
				gyroData.add(e);		
				
				dataLock.unlock(); /* release data lock */
				
				sendBroadcast(e);
				
				break;
			case Sensor.TYPE_ACCELEROMETER:
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				break;
			}
	}
	
	private Intent broadcastIntent = new Intent("PHONE_GYRO_UPDATE");
	private Bundle broadcastBundle = new Bundle();
	
	private void sendBroadcast(SensorVal event)
	{
		/* update UI every 16 data points */
		if(broadcast_count < 8)
		{
			broadcast_count++;
		}
		else
		{
			broadcast_count = 0;
			float[] vals = event.getVals();
			broadcastBundle.putString("x", Float.toString(vals[0]));
			broadcastBundle.putString("y", Float.toString(vals[1]));
			broadcastBundle.putString("z", Float.toString(vals[2]));
			broadcastBundle.putString("t", Long.toString(event.getTime()));
			broadcastIntent.putExtras(broadcastBundle);
			parentService.sendBroadcast(broadcastIntent);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		/* We will ignore this for now... but post a log message about it */
		Log.i(ActivityTrackerService.TAG, "Gyro accuracy change");
	}

	@Override
	public void unregister()
	{
		if( isRegistered == true )
		{
			mSensorManager.unregisterListener(this);
			isRegistered = false;
		}
	}
	
	/**
	 * Create a string for the HTTP post based on a list of XYZ events
	 * @param events XYZEvents arraylist to be formatted
	 * @return formatted string
	 */
	private String buildDataString(ArrayList<SensorVal> events) {
		
		/* use of StringBuilder avoids garbage collection when appending to strings */
		StringBuilder data = new StringBuilder();
		
		for(int i = 0; i < events.size(); i++)
		{
			data.append(events.get(i).toString());
		}
		
		return data.toString();
	}


	@Override
	public String getChannelName()
	{
		return "Gyroscope_Data";
	}


	@Override
	public void restoreData()
	{
		dataLock.lock(); /* acquire data lock */
		
		if( prevData != null )
			gyroData.addAll(prevData);
		
		dataLock.unlock(); /* release data lock */
		
		prevData = null;
		dataPosting = false;
	}
}
