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
	 * @return The ID number to identity an Entity registered in the system.
	 */
	int getTargetId();

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

	/**
	 * Applies the change to the given entity, return a reverse change if applied successfully, null if it failed and
	 * should be rejected.
	 * For context:  This call is only used on the clients, when speculatively applying the change.  The reverse change
	 * is required in order to reverse-update-apply when new changes/mutations are committed "before" it.  The reverse
	 * change CANNOT fail when applied to the state produced by THIS change.
	 * 
	 * @param context Used for reading world state or scheduling follow-up operations.
	 * @param newEntity The entity currently being modified by this change in the current tick.
	 * @return A non-null reverse change if the change was applied successfully, null if it conflicted and should be
	 * rejected.
	 */
	IEntityChange applyChangeReversible(TickProcessingContext context, MutableEntity newEntity);

	/**
	 * Called when applying a change to a speculative projection in order to see if it renders the previous change
	 * applied to the same target entity as redundant.  This is to avoid sending a long stream of tiny mutations (like
	 * movements) to the server when it only cared about the final state.
	 * 
	 * @param previousChange The previous change successfully applied to this entity.
	 * @return True if this change can replace the previousChange in the system.
	 */
	boolean canReplacePrevious(IEntityChange previousChange);
}
