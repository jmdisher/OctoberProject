package com.jeffdisher.october.aspects;


/**
 * Helpers and constants related to the "FLAGS" byte aspect.
 */
public class FlagsAspect
{
	public static final byte FLAG_BURNING = 0x1;

	public static boolean isSet(byte value, byte flag)
	{
		return flag == (value & flag);
	}

	public static byte set(byte value, byte flag)
	{
		return (byte)(value | flag);
	}

	public static byte clear(byte value, byte flag)
	{
		return (byte)(value & ~flag);
	}
}
