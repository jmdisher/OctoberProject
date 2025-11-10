package com.jeffdisher.october.types;


/**
 * This record just exists as a simple way to tie together the location associated with an entity, instead of passing
 * around the primitive floats, directly.
 * Note that an entity also has EntityVolume so the EntityLocation is the bottom, south-west corner of the total space
 * currently occupied by the entity.
 * NOTE:  EntityLocation instances are ALWAYS rounded to have 0.01f precision on each coordinate.
 */
public final record EntityLocation(float x, float y, float z)
{
	public EntityLocation(float x, float y, float z)
	{
		this.x = _roundToHundredths(x);
		this.y = _roundToHundredths(y);
		this.z = _roundToHundredths(z);
	}

	/**
	 * @return The location of the block where this entity is located.
	 */
	public final AbsoluteLocation getBlockLocation()
	{
		return new AbsoluteLocation(_blockBase(x), _blockBase(y), _blockBase(z));
	}

	/**
	 * @return The offset from the base of the block where the entity is (all values in the range: [0.0-1.0)).
	 */
	public final EntityLocation getOffsetIntoBlock()
	{
		float x = this.x - _blockBase(this.x);
		float y = this.y - _blockBase(this.y);
		float z = this.z - _blockBase(this.z);
		return new EntityLocation(x, y, z);
	}

	/**
	 * @return The magnitude of the receiver, interpreted as a vector.
	 */
	public final float getMagnitude()
	{
		return (float)Math.sqrt((this.x * this.x) + (this.y * this.y) + (this.z * this.z));
	}

	/**
	 * @param scale The scale to multiply by the coordinates.
	 * @return A new instance, scaling the receiver by the scale, interpreting it as a vector.
	 */
	public final EntityLocation makeScaledInstance(float scale)
	{
		return new EntityLocation(this.x * scale, this.y * scale, this.z * scale);
	}

	/**
	 * This helper rounds the given float to the nearest 0.01.
	 * 
	 * @param f The float to round.
	 * @return The float rounded to the nearest hundredth.
	 */
	public static float roundToHundredths(float f)
	{
		return _roundToHundredths(f);
	}


	private static int _blockBase(float inBlock)
	{
		// We round from floor, instead of casting, since we need the negative values to saturate down, instead of just
		// cut off:  -0.1 should be -1 (floor) instead of 0 (cast).
		return Math.round((float)Math.floor(inBlock));
	}

	private static float _roundToHundredths(float f)
	{
		// We might need to check more on this for very large numbers (might need BigDecimal rounding) but this approach
		// is simple and provides the predictability we want to reasonable numbers (this is mostly just to make tests
		// deterministic and obvious).
		return Math.round(100.0f * f) / 100.0f;
	}
}
