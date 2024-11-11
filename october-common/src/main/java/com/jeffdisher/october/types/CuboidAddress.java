package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Encoding;


/**
 * The address of a cuboid in absolute coordinates, in units of cuboids.  These coordinates are SIGNED.
 */
public record CuboidAddress(short x, short y, short z)
{
	/**
	 * A helper factory method to create an instance without explicit casting to short.
	 * 
	 * @param x The x coordinate.
	 * @param y The y coordinate.
	 * @param z The z coordinate.
	 * @return A new CuboidAddress.
	 */
	public static final CuboidAddress fromInt(int x, int y, int z)
	{
		return new CuboidAddress((short)x, (short)y, (short)z);
	}

	// (these are int args just to require fewer casts in the callers)
	public final CuboidAddress getRelative(int rx, int ry, int rz)
	{
		return new CuboidAddress((short)(x + rx), (short)(y + ry), (short)(z + rz));
	}

	/**
	 * @return The location object for the (west, south, down)-most block in this cuboid.
	 */
	public AbsoluteLocation getBase()
	{
		return new AbsoluteLocation(Encoding.getBaseLocationFromCuboid(x), Encoding.getBaseLocationFromCuboid(y), Encoding.getBaseLocationFromCuboid(z));
	}

	/**
	 * @return The address of the cuboid column of which this cuboid address is a part.
	 */
	public CuboidColumnAddress getColumn()
	{
		return new CuboidColumnAddress(x, y);
	}
}
