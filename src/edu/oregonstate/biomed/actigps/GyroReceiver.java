package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class GyroReceiver implements ActivitySensor, SensorEventListener
{
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
	
	public GyroReceiver(ActivityTrackerService serv)
	{
		mSensorManager = serv.sensors;
		isRegistered = false;
		dataPosting = false;
	}
	
	
	@Override
	public void register()
	{
		mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		
		/* change this back to DELAY_GAME when server can handle it */
		mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
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
				
				break;
			case Sensor.TYPE_ACCELEROMETER:
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				break;
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
