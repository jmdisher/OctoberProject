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
}
