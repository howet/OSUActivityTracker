package edu.oregonstate.biomed.actigps;

/**
 * Any sensor for the Activity tracker should implement this interface
 */
public interface ActivitySensor
{
	/**
	 * Gets the any data that has been saved up
	 * @return The saved data formatted as a string
	 */
	public abstract String getDataString();
	
	/**
	 * Clear any data that has been stored up
	 */
	public abstract void clearData();
	
	/**
	 * Unregister all associated sensors
	 */
	public abstract void unregister();
}
