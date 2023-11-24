package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.types.MutableEntity;


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
	 * @param newEntity The entity currently being modified by this change in the current tick.
	 * @param newMutationSink The consumer of any new mutations produces as a side-effect of this one.
	 * @param newChangeSink The consumer of any new entity changes produced as a side-effect of this mutation
	 * @return True if the mutation was applied successfully, false if it changed nothing and should be rejected.
	 */
	boolean applyChange(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink);

	/**
	 * Applies the change to the given entity, return a reverse change if applied successfully, null if it failed and
	 * should be rejected.
	 * For context:  This call is only used on the clients, when speculatively applying the change.  The reverse change
	 * is required in order to reverse-update-apply when new changes/mutations are committed "before" it.  The reverse
	 * change CANNOT fail when applied to the state produced by THIS change.
	 * 
	 * @param newEntity The entity currently being modified by this change in the current tick.
	 * @param newMutationSink The consumer of any new mutations produces as a side-effect of this one.
	 * @param newChangeSink The consumer of any new entity changes produced as a side-effect of this mutation
	 * @return A non-null reverse change if the change was applied successfully, null if it conflicted and should be
	 * rejected.
	 */
	IEntityChange applyChangeReversible(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink);
}
