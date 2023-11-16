package com.jeffdisher.october.utils;


public class Encoding
{
	// 32x32x32 cuboid region since means shift by 5 (/32).
	public static int CUBOID_SHIFT = 5;
	// The low 5 bits of the absolute address is the block address within a cuboid.
	// NOTE:  While absolute and cuboid addresses are SIGNED, the block addresses are UNSIGNED.
	public static int BLOCK_ADDRESS_MASK = (1 << CUBOID_SHIFT) - 1;

	public static short setShortTag(short value)
	{
		return (short)(value | 0x8000);
	}

	public static short clearShortTag(short value)
	{
		return (short)(value & ~0x8000);
	}

	public static boolean checkTag(byte value)
	{
		return (value & 0x80) != 0;
	}

	public static short getCuboidAddress(int absolute)
	{
		return _getCuboidAddress(absolute);
	}

	public static byte getBlockAddress(int absolute)
	{
		return _getBlockAddress(absolute);
	}

	public static short[] getCombinedCuboidAddress(int[] absoluteLocation)
	{
		return new short[] { _getCuboidAddress(absoluteLocation[0]), _getCuboidAddress(absoluteLocation[1]), _getCuboidAddress(absoluteLocation[2]) };
	}

	public static byte[] getCombinedBlockAddress(int[] absoluteLocation)
	{
		return new byte[] { _getBlockAddress(absoluteLocation[0]), _getBlockAddress(absoluteLocation[1]), _getBlockAddress(absoluteLocation[2]) };
	}

	/**
	 * Encodes the given cuboid address coordinates into a single unique primitive.  This can only really be used as a
	 * sort of hash.
	 * 
	 * @param x The x cuboid address.
	 * @param y The y cuboid address.
	 * @param z The z cuboid address.
	 * @return The unique encoding of this address
	 */
	public static long encodeCuboidAddress(short x, short y, short z)
	{
		// Note that the cuboid addresses are signed so we want to encode them by converting them to unsigned values and
		// shifting them over by all 16 bits.  The resulant 48-bit address will fit into a long without sign issues.
		return (Short.toUnsignedLong(x) << 32)
				| (Short.toUnsignedLong(y) << 16)
				| Short.toUnsignedLong(z)
		;
	}


	private static short _getCuboidAddress(int absolute)
	{
		// Get the cuboid address.
		int cuboidAddress = absolute >> CUBOID_SHIFT;
		// Make sure that this is within bounds.
		Assert.assertTrue(cuboidAddress <= Short.MAX_VALUE);
		return (short)cuboidAddress;
	}

	private static byte _getBlockAddress(int absolute)
	{
		int blockAddress = absolute & BLOCK_ADDRESS_MASK;
		return (byte) blockAddress;
	}
}
