package com.jeffdisher.october.types;


/**
 * The address of a column of cuboids in absolute coordinates, in units of cuboids.  These coordinates are SIGNED.
 * Note that this isn't generally as useful as CuboidAddress and it just used for addressing ColumnHeightMap instances.
 */
public record CuboidColumnAddress(short x, short y)
{
	// (these are int args just to require fewer casts in the callers)
	public final CuboidColumnAddress getRelative(int rx, int ry)
	{
		return new CuboidColumnAddress((short)(x + rx), (short)(y + ry));
	}
}
