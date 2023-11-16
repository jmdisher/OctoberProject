package com.jeffdisher.october.types;


/**
 * This record just exists as a simple way to tie together the location associated with an entity, instead of passing
 * around the primitive floats, directly.
 */
public final record EntityLocation(float x, float y, float z)
{
	public final AbsoluteLocation getLocation()
	{
		return new AbsoluteLocation(Math.round(x), Math.round(y), Math.round(z));
	}
}
