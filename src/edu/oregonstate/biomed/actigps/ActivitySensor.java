package edu.oregonstate.biomed.actigps;

/**
 * Any sensor for the Activity tracker should implement this interface
 */
public interface ActivitySensor
{
	/**
	 * Gets the any data that has been saved up.  
	 * clearData() or restoreData() must be called before this method will return anything on subsequent calls
	 * @return The saved data formatted as a string
	 */
	public abstract String getDataString();
	
	/**
	 * Clear any data that was previously fetched through getDataString()
	 */
	public abstract void clearData();
	
	/**
	 * Registers the receiver for data receipt
	 */
	public abstract void register();
	
	/**
	 * Unregister all associated sensors
	 */
	public abstract void unregister();
	
	/**
	 * Restore any data that was previously fetched through getDataString().
	 */
	public abstract void restoreData();
	
	/**
	 * retrieve the channel name for this sensor
	 * @return The name of the channel
	 */
	public String getChannelName();
}
