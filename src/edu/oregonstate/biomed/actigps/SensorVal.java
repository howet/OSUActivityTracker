package edu.oregonstate.biomed.actigps;

import java.util.Date;

import android.hardware.SensorEvent;

public class SensorVal
{
	private float[] vals;
	private long time;
	private long timeoffset = 0;
	private long timeoffset_start = 0;
	
	public SensorVal(SensorEvent e)
	{
		vals = e.values.clone();
		
		if (timeoffset == 0) {
			timeoffset = (new Date()).getTime();
			timeoffset_start = e.timestamp;
		}
		
		/* build the timestamp using the nanotime stamp from the sensor and the
		   offset from the RTC, keep the result in microseconds */
		time = timeoffset*1000+(e.timestamp - timeoffset_start)/1000;
	}
	
	public String toString()
	{
		/* format data as <timestamp>:<value>\r\n" */
		return String.format("%d:%f,%f,%f\r\n", time,vals[0],vals[1],vals[2]);
	}
	
	public float[] getVals() { return vals; }
	public long getTime() { return time; }
}
