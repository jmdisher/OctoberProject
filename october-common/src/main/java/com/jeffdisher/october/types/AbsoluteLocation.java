package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Encoding;


public final record AbsoluteLocation(int x, int y, int z)
{
	public final CuboidAddress getCuboidAddress()
	{
		return new CuboidAddress(Encoding.getCuboidAddress(x), Encoding.getCuboidAddress(y), Encoding.getCuboidAddress(z));
	}

	public final BlockAddress getBlockAddress()
	{
		return new BlockAddress(Encoding.getBlockAddress(x), Encoding.getBlockAddress(y), Encoding.getBlockAddress(z));
	}

	public final AbsoluteLocation getRelative(int rx, int ry, int rz)
	{
		return new AbsoluteLocation(x + rx, y + ry, z + rz);
	}

	public final EntityLocation toEntityLocation()
	{
		return new EntityLocation(this.x, this.y, this.z);
	}
}
