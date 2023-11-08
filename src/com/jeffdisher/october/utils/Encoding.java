package com.jeffdisher.october.utils;


public class Encoding
{
	// 32x32x32 cuboid region since means shift by 5 (/32).
	public static int CUBOID_SHIFT = 5;
	// The low 5 bits of the absolute address is the block address within a cuboid.
	public static int BLOCK_ADDRESS_MASK = (1 << CUBOID_SHIFT) - 1;

	public static short setShortTag(short value)
	{
		return (short)(value | 0x8000);
	}

	public static short clearShortTag(short value)
	{
		return (short)(value & ~0x8000);
	}

	public static boolean checkShortTag(short value)
	{
		return (value & 0x8000) != 0;
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

	public static long encodeCuboidAddress(short[] cuboidAddress)
	{
		return (Short.toUnsignedLong(cuboidAddress[0]) << 30)
				| (Short.toUnsignedLong(cuboidAddress[1]) << 15)
				| Short.toUnsignedLong(cuboidAddress[2])
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
