package com.jeffdisher.october.data;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;


public interface IAspectCodec<T>
{
	/**
	 * Loads a single data object from the buffer, throwing if not all of the object was in the buffer.
	 * 
	 * @param buffer The buffer to read.
	 * @return The object.
	 * @throws BufferUnderflowException If the buffer didn't contain the entire object.
	 */
	T loadData(ByteBuffer buffer) throws BufferUnderflowException;

	/**
	 * Stores a single data object into the buffer, throwing if there isn't enough space to store it.
	 * 
	 * @param buffer The buffer to write.
	 * @param object The object to serialize and store.
	 * @throws BufferOverflowException If the buffer doesn't have enough space to hold the object.
	 */
	void storeData(ByteBuffer buffer, Object object) throws BufferOverflowException;
}
