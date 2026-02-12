package com.jeffdisher.october.data;

import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


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
	 * Creates a new Builder object to create a new ColumnHeightMap instance.  Note that the build can only consume
	 * CuboidHeightMap instances from the top down (descending along z-axis).
	 * 
	 * @return A new Builder instance.
	 */
	public static Builder build()
	{
		return new Builder();
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
		Assert.assertTrue(x < Encoding.CUBOID_EDGE_SIZE);
		Assert.assertTrue(y >= 0);
		Assert.assertTrue(y < Encoding.CUBOID_EDGE_SIZE);
		return _yMajorMap[y][x];
	}


	/**
	 * A short-lived object which only exists to create new ColumnHeightMap instances (as they are read-only).
	 */
	public static class Builder
	{
		private final int[][] _mutableYMajorData;
		private int _lowestZ;
		private int _unknownCount;
		private Builder()
		{
			int[][] yMajor = new int[Encoding.CUBOID_EDGE_SIZE][Encoding.CUBOID_EDGE_SIZE];
			for (int y = 0; y < yMajor.length; ++y)
			{
				int[] row = yMajor[y];
				for (int x = 0; x < row.length; ++x)
				{
					row[x] = Integer.MIN_VALUE;
				}
			}
			_mutableYMajorData = yMajor;
			_lowestZ = Integer.MAX_VALUE;
			_unknownCount = Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE;
		}
		/**
		 * Integrates the given cuboid at zBase absolute Z location into the builder's height map.
		 * 
		 * @param cuboid The cuboid-local height map to integrate.
		 * @param address The address of the cuboid associated with the given height map.
		 * @return This.
		 */
		public Builder consume(CuboidHeightMap cuboid, CuboidAddress address)
		{
			int zBase = address.getBase().z();
			Assert.assertTrue(zBase < _lowestZ);
			if (_unknownCount > 0)
			{
				int changeCount = 0;
				for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
				{
					int[] targetRow = _mutableYMajorData[y];
					for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
					{
						if (Integer.MIN_VALUE == targetRow[x])
						{
							byte incoming = cuboid.getHightestSolidBlock(x, y);
							if (CuboidHeightMap.UNKNOWN_HEIGHT != incoming)
							{
								targetRow[x] = (int)incoming + zBase;
								changeCount += 1;
							}
						}
					}
				}
				_lowestZ = zBase;
				_unknownCount -= changeCount;
				Assert.assertTrue(_unknownCount >= 0);
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
