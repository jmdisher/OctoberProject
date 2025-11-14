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
	 * @return The type-specific extended data.
	 */
	Object getExtendedData();
	/**
	 * Changes the type-specific extended data instance.
	 * 
	 * @param extendedData The new instance.
	 */
	void setExtendedData(Object extendedData);
	/**
	 * Changes the receiver's type, resetting its health and extended data to defaults for this type.  This is typically
	 * used for cases such as livestock growing from a baby to adult but there is no internal check on usage.
	 * 
	 * @param newType The new type to assign to the receiver.
	 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
	 */
	void changeEntityType(EntityType newType, long gameTimeMillis);
}
