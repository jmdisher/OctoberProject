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
	 * Sets the location of a breedable creature's offspring.  This is received by the "mother" in an entity pairing.
	 * 
	 * @param spawnLocation The location where the offspring should be spawned.
	 */
	void setOffspringLocation(EntityLocation spawnLocation);
	/**
	 * @return The location where any offspring should be spawned.
	 */
	EntityLocation getOffspringLocation();
	/**
	 * Sets whether a breedable creature is in "love mode", where it will seek out other of the same type to breed.
	 * 
	 * @param isInLoveMode 
	 */
	void setLoveMode(boolean isInLoveMode);
	/**
	 * @return Whether or not this creature is in "love mode".
	 */
	boolean isInLoveMode();
}
