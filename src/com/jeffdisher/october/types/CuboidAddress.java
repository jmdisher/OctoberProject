package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Encoding;


public record CuboidAddress(short x, short y, short z)
{
	public long getLongHash()
	{
		return Encoding.encodeCuboidAddress(new short[] {x, y, z});
	}
}
