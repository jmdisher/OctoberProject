package com.jeffdisher.october.data;

import java.nio.ByteBuffer;
import java.util.Comparator;

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
	 * Reads several 15-bit shorts at the same time.
	 * Note that the array of addresses given must contain at least 1 element, must be in the "batch sorted order", and
	 * contain no duplicates.  Use BlockAddressBatchComparator to sort BlockAddress instances.
	 * 
	 * @param type The aspect to read.
	 * @param addresses The list of addresses to read.
	 * @return An array containing every requested short address value, in the same order requested.
	 */
	short[] batchReadData15(Aspect<Short, ?> type, BlockAddress[] addresses);

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


	/**
	 * A comparator which can be used to sort an array of BlockAddress instances intended for use in batchReadData15.
	 */
	public static class BlockAddressBatchComparator implements Comparator<BlockAddress>
	{
		@Override
		public int compare(BlockAddress o1, BlockAddress o2)
		{
			int sort1 = getBatchSortOrder(o1);
			int sort2 = getBatchSortOrder(o2);
			return sort1 - sort2;
		}
	}

	/**
	 * Used to generate the sort order index of a BlockAddress when being sorted for use in a batch.  This essentially
	 * weaves the bits from every magnitude together, such that the magnitudes of dimensions are ordered X, Y, then Z.
	 * 
	 * @param blockAddress The address to consider.
	 * @return The sort order index of this instance.
	 */
	public static int getBatchSortOrder(BlockAddress blockAddress)
	{
		byte x = blockAddress.x();
		byte y = blockAddress.y();
		byte z = blockAddress.z();
		
		int value = 0;
		for (int mask = 0x100; mask > 0; mask >>= 1)
		{
			int xBit = (0 != (x & mask)) ? 1 : 0;
			int yBit = (0 != (y & mask)) ? 1 : 0;
			int zBit = (0 != (z & mask)) ? 1 : 0;
			
			// Shift over the previous iteration and add in our bits for X, Y, Z (in that order).
			value <<= 3;
			value |= (xBit << 2);
			value |= (yBit << 1);
			value |= zBit;
		}
		return value;
	}
}
