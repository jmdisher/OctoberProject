package com.jeffdisher.october.types;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;


/**
 * Similar to IMutation, this is the corresponding logic for changes to entity state.
 * Note that the entity changes are applied strictly BEFORE the mutation changes in a given tick.
 * Additionally, the order of how new entity changes are enqueued at the end of a tick:
 * 1) Changes enqueued by existing changes
 * 2) Changes enqueued by existing mutations
 * 3) New entity joins
 * 4) Changes enqueued by the external calls
 */
public interface IEntityAction<T extends IMutableMinimalEntity>
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
	boolean applyChange(TickProcessingContext context, T newEntity);

	/**
	 * This type is just used for serialization so it should be null if the mutation is only for testing or internal
	 * implementation details and should never go over the network.
	 * 
	 * @return The type for entity serialization.
	 */
	EntityActionType getType();

	/**
	 * Called during serialization to serialize any internal instance variables of the mutation to the given buffer.
	 * 
	 * @param buffer The buffer where the mutation should be written.
	 */
	void serializeToBuffer(ByteBuffer buffer);

	/**
	 * This is used in cases where an entity is being unloaded while a change is scheduled against it.  While most such
	 * changes should be written to disk so they can be restored and applied later, some may not make sense to load at a
	 * later time (for example, if they reference other entities which may move/unload in the interim).
	 * 
	 * @return True if the receiver can be written to disk for later reading or if it should be discarded when the
	 * corresponding entity is unloaded.
	 */
	boolean canSaveToDisk();
}
