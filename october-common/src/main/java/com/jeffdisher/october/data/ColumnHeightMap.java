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

	/**
	 * Creates a new Builder object to create a new ColumnHeightMap instance.
	 * 
	 * @return A new Builder instance.
	 */
	public static Builder build()
	{
		int[][] yMajor = new int[Structure.CUBOID_EDGE_SIZE][Structure.CUBOID_EDGE_SIZE];
		for (int y = 0; y < yMajor.length; ++y)
		{
			int[] row = yMajor[y];
			for (int x = 0; x < row.length; ++x)
			{
				row[x] = Integer.MIN_VALUE;
			}
		}
		return new Builder(yMajor);
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


	/**
	 * A short-lived object which only exists to create new ColumnHeightMap instances (as they are read-only).
	 */
	public static class Builder
	{
		private final int[][] _mutableYMajorData;
		private Builder(int[][] yMajor)
		{
			_mutableYMajorData = yMajor;
		}
		/**
		 * Integrates the given cuboid at zBase absolute Z location into the builder's height map.
		 * 
		 * @param cuboid The cuboid-local height map to integrate.
		 * @param zBase The base Z coordinate of the cuboid.
		 * @return This.
		 */
		public Builder consume(CuboidHeightMap cuboid, int zBase)
		{
			for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
			{
				int[] targetRow = _mutableYMajorData[y];
				for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
				{
					byte incoming = cuboid.getHightestSolidBlock(x, y);
					int value = (CuboidHeightMap.UNKNOWN_HEIGHT == incoming)
							? Integer.MIN_VALUE
							: ((int)incoming + zBase)
					;
					int existing = targetRow[x];
					targetRow[x] = Math.max(existing, value);
				}
			}
			return this;
		}
		/**
		 * Creates a new read-only ColumnHeightMap instance from the backing store in the receiver.
		 * NOTE:  The receiver should not be used after this point as it shares the backing store with the returned
		 * object.
		 * 
		 * @return A new ColumnHeightMap instance.
		 */
		public ColumnHeightMap freeze()
		{
			return new ColumnHeightMap(_mutableYMajorData);
		}
	}
}
