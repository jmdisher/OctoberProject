package com.jeffdisher.october.types;


/**
 * The mutable interface for a an entity representing a creature.
 * Note that this is currently designed to be very much an abstraction over a mostly pure-data object with actual
 * functionality built on top of it.
 */
public interface IMutableCreatureEntity extends IMutableMinimalEntity
{
	/**
	 * WARNING:  This extended data should be immutable!
	 * 
	 * @return Returns the extended data object for this creature (could be null).
	 */
	Object getExtendedData();
	/**
	 * Sets the extended data object for this creature.
	 * WARNING:  This extended data should be immutable!
	 * 
	 * @param data The new data object.
	 */
	void setExtendedData(Object data);
}
