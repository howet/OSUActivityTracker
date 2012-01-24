package edu.oregonstate.biomed.actigps;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

public class SensorReceiver implements SensorEventListener, ActivitySensor
{
	private ActivityTrackerService parentService = null;
	private SensorManager mSensorManager = null;
	private Sensor mAccelerometer = null;
	
	private ArrayList<Float> accelx = new ArrayList<Float>();
	private ArrayList<Float> accely = new ArrayList<Float>();
	private ArrayList<Float> accelz = new ArrayList<Float>();
	private ArrayList<Long> accelt = new ArrayList<Long>();
	
	private final UUID xUUID = UUID.fromString("2a26c468-bb61-4981-8ccb-24a40e85b41e");
	private final UUID yUUID = UUID.fromString("1fc8a314-ca47-4376-8aa4-bf77237dd809");
	private final UUID zUUID = UUID.fromString("b792a01e-94e7-4a14-bdff-a5d82a893802");
	
	private long timeoffset = 0;
	private long timeoffset_start = 0;
	
	public SensorReceiver(ActivityTrackerService serv)
	{
		parentService = serv;
		
		mSensorManager = serv.sensors;
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
	}
	
	@Override
	public String getDataString()
	{
		final int pid = parentService.getPatientId();
		String data = "";
		
		if( accelt.size() != (accelx.size() + accely.size() + accelz.size()) / 3) 
			Log.w(ActivityTrackerService.TAG, "WARNING: acceleration array sizes do not match.");
		
		if( accelt.size() > 0 )	
		{
			data += buildDataString(accelx, xUUID, pid);
			data += buildDataString(accely, yUUID, pid);
			data += buildDataString(accelz, zUUID, pid);
		}

		
		return data;
	}

	@Override
	public void clearData()
	{
		accelx.clear();
		accely.clear();
		accelz.clear();
		accelt.clear();
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				accelx.add(event.values[0]);
				accely.add(event.values[1]);
				accelz.add(event.values[2]);
				
				Log.i(ActivityTrackerService.TAG, "Accelerometer received data of (" + 
						event.values[0] + "," + event.values[1] + "," + event.values[2] + ")");
				
				if (timeoffset == 0) {
					timeoffset = (new Date()).getTime();
					timeoffset_start = event.timestamp;
				}
				
				//build the timestamp using the nanotime stamp from the sensor and the
				//offset from the RTC, keep the result in microseconds
				accelt.add(timeoffset*1000+(event.timestamp - timeoffset_start)/1000);			
				
				Intent i = new Intent("PHONE_LOCATION_UPDATE");
				Bundle b = new Bundle();
				b.putString("x", Float.toString(event.values[0]));
				b.putString("y", Float.toString(event.values[1]));
				b.putString("z", Float.toString(event.values[2]));
				b.putString("t", accelt.get(accelt.size() - 1).toString());
				i.putExtras(b);
				parentService.sendBroadcast(i);
				
				break;
			case Sensor.TYPE_GYROSCOPE:
				
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				
				break;
			}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		//We will ignore this for now... but post a log message about it
		Log.i(ActivityTrackerService.TAG, "Sensor accuracy change");
	}

	@Override
	public void unregister()
	{
		mSensorManager.unregisterListener(this);
	}
	
	private String buildDataString(ArrayList<Float> list, UUID u, int pid) {
		//build the data string
		String data = "";
		for(int i = 0; i < list.size(); i++)
		{
			float val = list.get(i);
			long time = accelt.get(i);
			data += "" + time/1000 + " " + pid + " " + u + " " + val + "\r\n";
		}
		return data;
	}

}
