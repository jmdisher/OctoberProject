package com.jeffdisher.october.data;

import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.worldgen.Structure;


/**
 * A read-only height-map which describes a z-level height for a given x/y coordinate within a single cuboid column.
 * As this is per cuboid column, the x/y coordinates are relative to the cuboid but the height value returned in
 * absolute.
 */
public class ColumnHeightMap
{
	/**
	 * Creates an instance wrapping the given raw data.  Note that this is not copied but taken by reference so the
	 * caller should discard their reference.
	 * 
	 * @param rawYMajorMap The raw height map data, addressed y-major ([y][x]).
	 * @return The new instance.
	 */
	public static ColumnHeightMap wrap(int[][] rawYMajorMap)
	{
		return new ColumnHeightMap(rawYMajorMap);
	}


	private final int[][] _yMajorMap;

	private ColumnHeightMap(int[][] yMajorMap)
	{
		_yMajorMap = yMajorMap;
	}

	/**
	 * Returns the height map value at the given cuboid-relative x/y coordinates.
	 * 
	 * @param x The X coordinate within the cuboid ([0..31]).
	 * @param y The Y coordinate within the cuboid ([0..31]).
	 * @return The height at this location (in absolute coordinates).
	 */
	public int getHeight(int x, int y)
	{
		Assert.assertTrue(x >= 0);
		Assert.assertTrue(x < Structure.CUBOID_EDGE_SIZE);
		Assert.assertTrue(y >= 0);
		Assert.assertTrue(y < Structure.CUBOID_EDGE_SIZE);
		return _yMajorMap[y][x];
	}
}
