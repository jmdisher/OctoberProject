package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public interface IReadOnlyCuboidData
{
	CuboidAddress getCuboidAddress();
	byte getData7(Aspect<Byte> type, BlockAddress address);
	short getData15(Aspect<Short> type, BlockAddress address);
	<T> T getDataSpecial(Aspect<T> type, BlockAddress address);

	/**
	 * Copies out all aspects of the block at the given block address.
	 * 
	 * @param address The xyz location of the block in this cuboid.
	 * @return The block copy with all aspects.
	 */
	Block getBlock(BlockAddress address);
}
