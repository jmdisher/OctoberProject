package com.jeffdisher.october.changes;

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
public interface IEntityChange
{
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
