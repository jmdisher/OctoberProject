package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;


public interface IOctree
{
	/**
	 * Fetches a single data element from the octree.
	 * 
	 * @param <T> The data type.
	 * @param <O> The IOctree type.
	 * @param type The aspect for decoding the data.
	 * @param address The address of the block to read.
	 * @return The data element at this address.
	 */
	<T, O extends IOctree> T getData(Aspect<T, O> type, BlockAddress address);

	/**
	 * Stores a single element into the octree, modifying it.
	 * 
	 * @param <T> The data type.
	 * @param address The address of the block to update.
	 * @param value The new value to store.
	 */
	<T> void setData(BlockAddress address, T value);

	/**
	 * Walks the tree, issuing callbacks for every data entry found (in no particular order), so long as it isn't
	 * valueToSkip.
	 * 
	 * @param <T> The data type.
	 * @param callback The callback to use for every tree entry walked.
	 * @param valueToSkip The callback will be skipped if the value equals this.
	 */
	<T> void walkData(IWalkerCallback<T> callback, T valueToSkip);

	/**
	 * Called to request serialization of the octree.  An implementation is expected to serialize as much as it can into
	 * the given buffer, passing back any state to resume the serialization if it fills up.  It returns null when fully
	 * serialized.
	 * 
	 * @param lastCallState The state to resume from a previous call.
	 * @param buffer The buffer to write.
	 * @param codec The codec to use when serializing object data elements.
	 * @return The state to resume on the next call, or null if the serialization was completed.
	 */
	Object serializeResumable(Object lastCallState, ByteBuffer buffer, IAspectCodec<?> codec);

	/**
	 * Called to request deserialization of the octree.  An implementation is expected to deserialize as much as it can
	 * from the given buffer, passing back any state to resume the deserialization if it fills up.  It returns null when
	 * fully deserialized.
	 * 
	 * @param lastCallState The state to resume from a previous call.
	 * @param buffer The buffer to read.
	 * @param codec The codec to use when deserializing object data elements.
	 * @return The state to resume on the next call, or null if the deserialization was completed.
	 */
	Object deserializeResumable(Object lastCallState, ByteBuffer buffer, IAspectCodec<?> codec);

	/**
	 * Used to receive callbacks from the walkData() calls.  Note that this will receive callbacks for all data,
	 * although the size parameter may indicate that multiple calls have been batched together.
	 * 
	 * @param <T> The value type
	 */
	public interface IWalkerCallback<T>
	{
		/**
		 * Called when visiting a data node in the underlying tree (note that the data nodes never overlap).  Note that
		 * the size parameter may indicate that all the values, rooted at base, within that size volume are the same.
		 * 
		 * @param base The block address of the data value or where the value starts.
		 * @param size The size of the volume where the value applies (applies to all dimensions).
		 * @param value The value in this location.
		 */
		void visit(BlockAddress base, byte size, T value);
	}
}
