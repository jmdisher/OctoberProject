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
	 * The number of sub-blocks along the edge of a block.
	 */
	public static final int SUB_BLOCK_EDGE = 4;
	/**
	 * The float representation of SUB_BLOCK_EDGE.
	 */
	public static final float SUB_BLOCK_EDGE_FLOAT = SUB_BLOCK_EDGE;
	/**
	 * The number of sub-blocks covering one face of a block.
	 */
	public static final int SUB_BLOCK_FACE = SUB_BLOCK_EDGE * SUB_BLOCK_EDGE;
	/**
	 * Half the number of sub-blocks along the edge of a block.
	 */
	public static final int SUB_BLOCK_HALF_EDGE = SUB_BLOCK_EDGE / 2;

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
		byte x = (byte)(SUB_BLOCK_EDGE_FLOAT * (lx - Math.floor(lx)));
		byte y = (byte)(SUB_BLOCK_EDGE_FLOAT * (ly - Math.floor(ly)));
		byte z = (byte)(SUB_BLOCK_EDGE_FLOAT * (lz - Math.floor(lz)));
		return new SubBlock(x, y, z);
	}

	/**
	 * Creates a sub-block instance from raw inputs, constructed externally.
	 * 
	 * @param x The x-coordinate to use.
	 * @param y The y-coordinate to use.
	 * @param z The z-coordinate to use.
	 * @return The SubBlock instance.
	 */
	public static SubBlock fromInt(int x, int y, int z)
	{
		return new SubBlock((byte)x, (byte)y, (byte)z);
	}


	public final byte x;
	public final byte y;
	public final byte z;

	private SubBlock(byte x, byte y, byte z)
	{
		// We will only allow special factory methods to call this, directly.
		Assert.assertTrue(x >= 0);
		Assert.assertTrue(x < SUB_BLOCK_EDGE);
		Assert.assertTrue(y >= 0);
		Assert.assertTrue(y < SUB_BLOCK_EDGE);
		Assert.assertTrue(z >= 0);
		Assert.assertTrue(z < SUB_BLOCK_EDGE);
		
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * Sub-block collision data is represented as a long bitvector per block.  This allows for any permutation of the
	 * 64 sub-block locations to be represented.
	 * This helper returns the mask to use to look up this location in that bitvector.
	 * 
	 * @return The mask used to look up whether this sub-block is solid in a sub-block bitvector.
	 */
	public long getMask()
	{
		int offset = (SUB_BLOCK_FACE * this.z) + (SUB_BLOCK_EDGE * this.y) + this.x;
		return 0x1L << offset;
	}

	@Override
	public boolean equals(Object obj)
	{
		boolean isEqual = false;
		if (obj instanceof SubBlock)
		{
			SubBlock other = (SubBlock) obj;
			isEqual = (this.x == other.x)
				&& (this.y == other.y)
				&& (this.z == other.z)
			;
		}
		return isEqual;
	}

	@Override
	public int hashCode()
	{
		return (this.z << 16)
			| (this.y << 8)
			| this.x
		;
	}

	@Override
	public String toString()
	{
		return String.format("SubBlock(%d, %d, %d)", this.x, this.y, this.z);
	}
}
