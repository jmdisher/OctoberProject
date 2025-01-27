package com.jeffdisher.october.types;

import java.util.List;


/**
 * The mutable interface for a an entity representing a creature.
 * Note that this is currently designed to be very much an abstraction over a mostly pure-data object with actual
 * functionality built on top of it.
 */
public interface IMutableCreatureEntity extends IMutableMinimalEntity
{
	/**
	 * An accessor for the read-only movement plan in the instance.
	 * 
	 * @return A read-only view of the current movement plan (could be null).
	 */
	List<AbsoluteLocation> getMovementPlan();
	/**
	 * Updates the movement plan to a copy of the one given.
	 * 
	 * @param movementPlan The movement plan (could be null).
	 */
	void setMovementPlan(List<AbsoluteLocation> movementPlan);
	/**
	 * Sets the flag to trigger a deliberate action on the next tick.
	 */
	void setReadyForAction();
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
