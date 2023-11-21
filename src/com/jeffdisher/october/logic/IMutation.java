package com.jeffdisher.october.logic;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;


public interface IMutation
{
	/**
	 * @return The absolute coordinates of the block to which the mutation applies.
	 */
	AbsoluteLocation getAbsoluteLocation();

	/**
	 * Applies the mutation to the given world, returning true if it was a success and false if it failed and should be
	 * rejected.
	 * Note that this call is made when a mutation is being applied authoritatively.  That is, it will NOT be reverted.
	 * 
	 * @param oldWorldLoader The view of the entire world, as of the beginning of this tick.
	 * @param newBlock The block currently being modified by this mutation in the current tick.
	 * @param newMutationSink The consumer of any new mutations produces as a side-effect of this one (do NOT write to
	 * this if rejecting the mutation).
	 * @return True if the mutation was applied successfully, false if it changed nothing and should be rejected.
	 */
	boolean applyMutation(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink);

	/**
	 * Applies the mutation to the given world, return a reverse mutation if applied successfully, null if it failed and
	 * should be rejected.
	 * Note that this call is only used on the clients, when speculatively applying the mutation.  The reverse mutation
	 * is required in order to reverse-update-apply when new mutations are committed "before" it.  The reverse mutation
	 * CANNOT fail when applied to the state produced by THIS mutation.
	 * 
	 * @param oldWorldLoader The view of the entire world, as of the beginning of this tick.
	 * @param newBlock The block currently being modified by this mutation in the current tick.
	 * @param newMutationSink The consumer of any new mutations produces as a side-effect of this one (do NOT write to
	 * this if rejecting the mutation).
	 * @return A non-null reverse mutation if the mutation was applied successfully, null if it conflicted and should be
	 * rejected.
	 */
	IMutation applyMutationReversible(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink);
}
