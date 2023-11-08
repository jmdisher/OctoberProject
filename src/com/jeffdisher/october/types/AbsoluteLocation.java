package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Encoding;


public record AbsoluteLocation(int x, int y, int z)
{
	public final CuboidAddress getCuboidAddress()
	{
		return new CuboidAddress(Encoding.getCuboidAddress(x), Encoding.getCuboidAddress(y), Encoding.getCuboidAddress(z));
	}

	public final BlockAddress getBlockAddress()
	{
		return new BlockAddress(Encoding.getBlockAddress(x), Encoding.getBlockAddress(y), Encoding.getBlockAddress(z));
	}
}
