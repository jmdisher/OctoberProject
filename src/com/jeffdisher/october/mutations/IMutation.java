package com.jeffdisher.october.mutations;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * An operation to schedule against a block in order to change it in some way.
 * 
 * Note that, even if the mutation is rejected (by returning false/null), side-effects made to newBlock or either of the
 * sinks still persist.  This means that care must be taken when a rejected transaction modifies state, since it may
 * cause client-side inconsistencies as it will NOT be transmitted to them.  To this end, the only side-effects
 * which are typically useful when rejecting are those to emit clean-up transactions which WILL succeed (as they
 * will be sent to the clients).
 */
public interface IMutation
{
	/**
	 * @return The absolute coordinates of the block to which the mutation applies.
	 */
	AbsoluteLocation getAbsoluteLocation();

	/**
	 * Applies the mutation to the given world, returning true if it was a success (and should be transmitted to
	 * clients) and false if it failed and should be rejected.
	 * For context:  This call is made when a mutation is being applied authoritatively.  That is, it will NOT be
	 * reverted as is common via the other apply path).
	 * 
	 * @param context Used for reading world state or scheduling follow-up operations.
	 * @param newBlock The block currently being modified by this mutation in the current tick.
	 * @return True if the mutation was applied successfully, false if it changed nothing and should be rejected.
	 */
	boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock);

	/**
	 * Applies the mutation to the given world, return a reverse mutation if applied successfully, null if it failed and
	 * should be rejected.
	 * For context:  This call is only used on the clients, when speculatively applying the mutation.  The reverse
	 * mutation is required in order to reverse-update-apply when new mutations are committed "before" it.  The reverse
	 * mutation CANNOT fail when applied to the state produced by THIS mutation.
	 * 
	 * @param context Used for reading world state or scheduling follow-up operations.
	 * @param newBlock The block currently being modified by this mutation in the current tick.
	 * @return A non-null reverse mutation if the mutation was applied successfully, null if it conflicted and should be
	 * rejected.
	 */
	IMutation applyMutationReversible(TickProcessingContext context, MutableBlockProxy newBlock);
}
