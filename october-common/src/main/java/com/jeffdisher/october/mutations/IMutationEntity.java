package com.jeffdisher.october.mutations;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Similar to IMutation, this is the corresponding logic for changes to entity state.
 * Note that the entity changes are applied strictly BEFORE the mutation changes in a given tick.
 * Additionally, the order of how new entity changes are enqueued at the end of a tick:
 * 1) Changes enqueued by existing changes
 * 2) Changes enqueued by existing mutations
 * 3) New entity joins
 * 4) Changes enqueued by the external calls
 */
public interface IMutationEntity
{
	/**
	 * Determines how many milliseconds it takes to run this change.  Note that this "time" is considered more abstract
	 * than physical, as the server will use its own inter-tick delay to determine what the maximum budget is.
	 * This "budget" is how the server determines if the entity is "busy" and is how it allows long-running tasks to
	 * occupy the entity until they are considered "done" and the corresponding change is executed.
	 * The general assumption is that the server's tick rate is 1 tick every 100 ms but that may change in the future.
	 * Returning -1 here will imply that the previous change should be aborted as failure, and not run, unless it
	 * already completed, in which case this change will be aborted as failure, instead.
	 * This is called during scheduling where the entity's time budget will limit how much is scheduled.
	 * 
	 * @return The number of milliseconds for which the entity should be considered occupied (-1 to cancel previous
	 * change).
	 */
	long getTimeCostMillis();

	/**
	 * Applies the change to the given entity, returning true if it was a success (and should be transmitted to
	 * clients) and false if it failed and should be rejected.
	 * For context:  This call is made when a mutation is being applied authoritatively.  That is, it will NOT be
	 * reverted as is common via the other apply path).
	 * 
	 * @param context Used for reading world state or scheduling follow-up operations.
	 * @param newEntity The entity currently being modified by this change in the current tick.
	 * @return True if the mutation was applied successfully, false if it changed nothing and should be rejected.
	 */
	boolean applyChange(TickProcessingContext context, MutableEntity newEntity);
}
