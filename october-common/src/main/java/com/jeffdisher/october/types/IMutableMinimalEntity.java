package com.jeffdisher.october.types;


/**
 * The mutable interface for a an entity representing a player.
 * Note that this is currently designed to be very much an abstraction over a mostly pure-data object with actual
 * functionality built on top of it.
 */
public interface IMutableMinimalEntity
{
	int getId();

	/**
	 * @return The type of the entity (this will never change for a given instance).
	 */
	EntityType getType();

	EntityLocation getLocation();

	void setLocation(EntityLocation location);

	EntityLocation getVelocityVector();

	void setVelocityVector(EntityLocation vector);

	byte getHealth();

	void setHealth(byte health);

	byte getBreath();

	void setBreath(byte breath);

	void setOrientation(byte yaw, byte pitch);

	byte getYaw();

	byte getPitch();

	NonStackableItem getArmour(BodyPart part);

	void setArmour(BodyPart part, NonStackableItem item);

	/**
	 * Resets any internal counters for operations which require multiple ticks of accumulation.  This includes things
	 * like in-inventory crafting but also charging weapons.  In the future, other operations will likely be covered in
	 * this way and/or this method may be split out or parameterized into more precise resets (for example, if walking
	 * should reset crafting, but not weapon charging).
	 */
	void resetLongRunningOperations();

	/**
	 * This will drop anything related to the entity and handle either respawning or cleaning up resources associated
	 * with them.
	 */
	void handleEntityDeath(TickProcessingContext context);

	/**
	 * This is part of the minimal interface since players have a concept of energy but other creatures don't.
	 * In the future, they might, but it would likely not be like players, anyway.
	 * 
	 * @param cost The cost associated with the change.
	 */
	void applyEnergyCost(int cost);

	/**
	 * Updates the last damage taken timeout, but only if the timeout has already expired.
	 * 
	 * @param currentTickMillis The current tick millisecond timer.
	 * @return True if the timeout was updated.
	 */
	boolean updateDamageTimeoutIfValid(long currentTickMillis);
}
