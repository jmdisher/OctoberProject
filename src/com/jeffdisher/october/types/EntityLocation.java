package com.jeffdisher.october.types;


/**
 * This record just exists as a simple way to tie together the location associated with an entity, instead of passing
 * around the primitive floats, directly.
 */
public final record EntityLocation(float x, float y, float z)
{
	/**
	 * @return The location of the block where this entity is located.
	 */
	public final AbsoluteLocation getBlockLocation()
	{
		return new AbsoluteLocation(_blockBase(x), _blockBase(y), _blockBase(z));
	}


	private static int _blockBase(float inBlock)
	{
		// We round from floor, instead of casting, since we need the negative values to saturate down, instead of just
		// cut off:  -0.1 should be -1 (floor) instead of 0 (cast).
		return Math.round((float)Math.floor(inBlock));
	}
}
