package edu.oregonstate.biomed.actigps;

import android.hardware.SensorEvent;

/** 
 * Used for storing raw sensor values from accelerometer or gyroscope
 */
public class SensorVal
{
	private float[] vals;
	private long time;
	
	public SensorVal(SensorEvent e)
	{
		vals = e.values.clone();
		time = e.timestamp;
	}
	
	public String toString()
	{
		/* format data as <timestamp>:<value>\r\n" */
		return String.format("%d:%f,%f,%f\r\n", time,vals[0],vals[1],vals[2]);
	}
	
	public float[] getVals() { return vals; }
	public long getTime() { return time; }
}
