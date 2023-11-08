package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;

public interface IReadOnlyCuboidData
{
	short[] getCuboidAddress();
	byte getData7(Aspect<Byte> type, byte x, byte y, byte z);
	short getData15(Aspect<Short> type, byte x, byte y, byte z);
	<T> T getDataSpecial(Aspect<T> type, byte x, byte y, byte z);

	/**
	 * Copies out all aspects of the block at the given block address.
	 * 
	 * @param blockAddress The xyz location of the block in this cuboid.
	 * @return The block copy with all aspects.
	 */
	Block getBlock(byte[] blockAddress);
}
