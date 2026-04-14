package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
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
public interface IMutationBlock
{
	/**
	 * @return The absolute coordinates of the block to which the mutation applies.
	 */
	AbsoluteLocation getAbsoluteLocation();

	/**
	 * Applies the mutation described by the receiver to the given newBlock mutable proxy within the given context.
	 * 
	 * @param context Used for reading world state or scheduling follow-up operations.
	 * @param newBlock The block currently being modified by this mutation in the current tick.
	 */
	void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock);

	/**
	 * This type is just used for serialization so it should be null if the mutation is only for testing or internal
	 * implementation details and should never go over the network.
	 * 
	 * @return The type for mutation serialization.
	 */
	MutationBlockType getType();

	/**
	 * Called during serialization to serialize any internal instance variables of the mutation to the given buffer.
	 * 
	 * @param buffer The buffer where the mutation should be written.
	 */
	void serializeToBuffer(ByteBuffer buffer);

	/**
	 * This is used in cases where a cuboid is being unloaded while a mutation is scheduled against it.  While most such
	 * mutations should be written to disk so they can be restored and applied later, some may not make sense to load at
	 * a later time (for example, if they reference entities which may move/unload in the interim).
	 * 
	 * @return True if the receiver can be written to disk for later reading or if it should be discarded when the
	 * corresponding cuboid is unloaded.
	 */
	boolean canSaveToDisk();
}
