package com.jeffdisher.october.types;


public final record EntityVolume(float x, float y, float z, float height, float width)
{
	public final AbsoluteLocation getLocation()
	{
		return new AbsoluteLocation(Math.round(x), Math.round(y), Math.round(z));
	}
}
