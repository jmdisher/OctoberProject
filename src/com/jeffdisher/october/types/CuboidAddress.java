package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Encoding;


/**
 * The address of a cuboid in absolute coordinates, in units of cuboids.  These coordinates are SIGNED.
 */
public record CuboidAddress(short x, short y, short z)
{
	public long getLongHash()
	{
		return Encoding.encodeCuboidAddress(x, y, z);
	}
}
