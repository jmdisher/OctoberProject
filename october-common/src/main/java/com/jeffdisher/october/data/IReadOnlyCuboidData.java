package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public interface IReadOnlyCuboidData
{
	CuboidAddress getCuboidAddress();
	byte getData7(Aspect<Byte, ?> type, BlockAddress address);
	short getData15(Aspect<Short, ?> type, BlockAddress address);
	<T> T getDataSpecial(Aspect<T, ?> type, BlockAddress address);

	/**
	 * Walks the tree used to represent the associated aspect, issuing callbacks for every data entry found (in no
	 * particular order), so long as it isn't valueToSkip.
	 * 
	 * @param <T> The data type.
	 * @param type The aspect to watch.
	 * @param callback The callback to use for every tree entry walked.
	 * @param valueToSkip The callback will be skipped if the value equals this.
	 */
	<T> void walkData(Aspect<T, ?> type, IOctree.IWalkerCallback<T> callback, T valueToSkip);

	Object serializeResumable(Object lastCallState, ByteBuffer buffer);
}
