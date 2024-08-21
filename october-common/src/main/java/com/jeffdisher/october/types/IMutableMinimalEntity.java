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

	int getBreath();

	void setBreath(int breath);

	NonStackableItem getArmour(BodyPart part);

	void setArmour(BodyPart part, NonStackableItem item);

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
	 * @param context The context where a change is running.
	 * @param cost The cost associated with the change.
	 */
	void applyEnergyCost(TickProcessingContext context, int cost);
}
