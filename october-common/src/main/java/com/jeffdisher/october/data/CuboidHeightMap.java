package com.jeffdisher.october.data;

import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.worldgen.Structure;


/**
 * A read-only height-map which describes a z-level height for a given x/y coordinate within a single cuboid.
 * Since this is only for a single cuboid, the height values can only be in the range of [0..31].  If there are no solid
 * blocks in a column within the cuboid, the value of -1 will be used for the height at that point.
 */
public class CuboidHeightMap
{
	public static final byte UNKNOWN_HEIGHT = -1;

	/**
	 * Creates an instance wrapping the given raw data.  Note that this is not copied but taken by reference so the
	 * caller should discard their reference.
	 * 
	 * @param rawYMajorMap The raw height map data, addressed y-major ([y][x]).
	 * @return The new instance.
	 */
	public static CuboidHeightMap wrap(byte[][] rawYMajorMap)
	{
		return new CuboidHeightMap(rawYMajorMap);
	}


	private final byte[][] _yMajorMap;

	private CuboidHeightMap(byte[][] yMajorMap)
	{
		_yMajorMap = yMajorMap;
	}

	/**
	 * Returns the highest solid block cuboid-relative z value for the given cuboid-relative x/y coordinates.
	 * 
	 * @param x The X coordinate within the cuboid ([0..31]).
	 * @param y The Y coordinate within the cuboid ([0..31]).
	 * @return The height at this location ([0..31] or -1 if not known).
	 */
	public byte getHightestSolidBlock(int x, int y)
	{
		Assert.assertTrue(x >= 0);
		Assert.assertTrue(x < Structure.CUBOID_EDGE_SIZE);
		Assert.assertTrue(y >= 0);
		Assert.assertTrue(y < Structure.CUBOID_EDGE_SIZE);
		return _yMajorMap[y][x];
	}
}
