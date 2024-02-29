package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * An operation which over-writes part of the state of a single block.  This may be the entire block state, a single
 * aspect, or part of an aspect.
 * Note that these operations are considered idempotent and should blindly write data, not basing that on any existing
 * state.
 */
public interface IBlockStateUpdate
{
	/**
	 * @return The absolute coordinates of the block to which the mutation applies.
	 */
	AbsoluteLocation getAbsoluteLocation();

	/**
	 * Applies the change to the given newBlock.
	 * 
	 * @param newBlock The block currently being modified by this mutation in the current tick.
	 */
	void applyState(MutableBlockProxy newBlock);

	/**
	 * This type is just used for serialization.
	 * 
	 * @return The type for mutation serialization.
	 */
	BlockStateUpdateType getType();

	/**
	 * Called during serialization to serialize any internal instance variables of the state update to the given buffer.
	 * 
	 * @param buffer The buffer where the state update should be written.
	 */
	void serializeToBuffer(ByteBuffer buffer);
}
