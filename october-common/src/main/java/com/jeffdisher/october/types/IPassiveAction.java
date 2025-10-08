package com.jeffdisher.october.types;


/**
 * Instances of this type are applied only to PassiveEntity instances.
 * Note that passive actions are not persisted to disk or sent over the network.  They either originate internally or
 * are sent by an existing entity or block.
 */
public interface IPassiveAction
{
	/**
	 * Applies the change to the given entity instance, returning an updated instance (if it changed), the same instance
	 * (if nothing changed), or null (if it should despawn).
	 * 
	 * @param context The current tick context.
	 * @param entity The passive entity to process.
	 * @return The new instance (or null to despawn).
	 */
	PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity);
}
