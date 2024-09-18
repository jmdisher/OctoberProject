package com.jeffdisher.october.utils;


public class Encoding
{
	/**
	 * Every cuboid is a 32x32x32 region of addressable cubes.
	 */
	public static final int CUBOID_EDGE_SIZE = 32;
	// 32x32x32 cuboid region since means shift by 5 (/32).
	public static int CUBOID_SHIFT = 5;
	// The low 5 bits of the absolute address is the block address within a cuboid.
	// NOTE:  While absolute and cuboid addresses are SIGNED, the block addresses are UNSIGNED.
	public static int BLOCK_ADDRESS_MASK = (1 << CUBOID_SHIFT) - 1;

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

	public static int getBaseLocationFromCuboid(short cuboid)
	{
		return cuboid << CUBOID_SHIFT;
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
