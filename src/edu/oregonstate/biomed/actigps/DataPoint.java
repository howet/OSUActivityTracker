package edu.oregonstate.biomed.actigps;

public class DataPoint
{
	private long time;
	private double val;
	
	public DataPoint(long timestamp, double value)
	{
		time = timestamp;
		val = value;
	}
	
	public long getX()
	{
		return time;
	}
	
	public double getY()
	{
		return val;
	}
}
