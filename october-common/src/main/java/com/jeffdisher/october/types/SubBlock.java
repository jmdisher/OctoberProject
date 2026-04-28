package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * A sub-block refers to the location in a 4x4x4 sub-block of an existing block.  It is used when computing sub-block
 * collision.
 * As suits the sub-block coordinates, all axes are in the [0..3] range.
 */
public class SubBlock
{
	/**
	 * Creates a sub-block instance from the fractional part of this base entity location.
	 * 
	 * @param location The base location to describe.
	 * @return The SubBlock instance describing the fractional part of the location.
	 */
	public static SubBlock base(EntityLocation location)
	{
		float lx = location.x();
		float ly = location.y();
		float lz = location.z();
		byte x = (byte)(4.0f * (lx - Math.floor(lx)));
		byte y = (byte)(4.0f * (ly - Math.floor(ly)));
		byte z = (byte)(4.0f * (lz - Math.floor(lz)));
		return new SubBlock(x, y, z);
	}


	public final byte x;
	public final byte y;
	public final byte z;

	private SubBlock(byte x, byte y, byte z)
	{
		// We will only allow special factory methods to call this, directly.
		Assert.assertTrue(x >= 0);
		Assert.assertTrue(x <= 3);
		Assert.assertTrue(y >= 0);
		Assert.assertTrue(y <= 3);
		Assert.assertTrue(z >= 0);
		Assert.assertTrue(z <= 3);
		
		this.x = x;
		this.y = y;
		this.z = z;
	}
}
