package com.jeffdisher.october.types;


/**
 * The address of a cuboid in absolute coordinates, in units of cuboids.  These coordinates are SIGNED.
 */
public record CuboidAddress(short x, short y, short z)
{
	// (these are int args just to require fewer casts in the callers)
	public final CuboidAddress getRelative(int rx, int ry, int rz)
	{
		return new CuboidAddress((short)(x + rx), (short)(y + ry), (short)(z + rz));
	}
}
