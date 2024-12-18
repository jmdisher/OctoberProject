package com.jeffdisher.october.logic;

import com.jeffdisher.october.types.IByteLookup;
import com.jeffdisher.october.utils.Assert;


/**
 * Represents byte values in a quasi-sparse cube in 3D space.
 * Note that the values stored here must be >=0.
 * Internally, each axis which contains data is allocated, but axes without anything are left null, so the actual memory
 * usage of the instance, when given largely sparse data, should be very cheap while still providing constant-time
 * primitive memory access.
 */
public class SparseByteCube
{
	static
	{
		// We assume that -1 is the common "not found" value in this implementation so assert this is the case.
		byte localNotFound = -1;
		byte commonNotFound = IByteLookup.NOT_FOUND;
		Assert.assertTrue(localNotFound == commonNotFound);
	}

	private final int _edgeSize;
	private byte[][][] _xyzData;

	/**
	 * Creates an instance with the given edgeSize limit for each axis.
	 * 
	 * @param edgeSize The addressing limit for each axis (all x, y, z must be less than this).
	 */
	public SparseByteCube(int edgeSize)
	{
		_edgeSize = edgeSize;
	}

	/**
	 * Stores the given value in the provided x, y, z coordinates, returning the previous value.
	 * 
	 * @param x The x coordinate, relative to zero.
	 * @param y The y coordinate, relative to zero.
	 * @param z The z coordinate, relative to zero.
	 * @param value The value to store (must be non-negative).
	 * @return The previous value (-1 if there wasn't one).
	 */
	public byte set(int x, int y, int z, byte value)
	{
		Assert.assertTrue((x >= 0) && (x < _edgeSize));
		Assert.assertTrue((y >= 0) && (y < _edgeSize));
		Assert.assertTrue((z >= 0) && (z < _edgeSize));
		Assert.assertTrue(value >= 0);
		
		// We rely on new entries being zero so we bias this.
		byte biased = (byte)(value + 1);
		if (null == _xyzData)
		{
			_xyzData = new byte[_edgeSize][][];
		}
		if (null == _xyzData[x])
		{
			_xyzData[x] = new byte[_edgeSize][];
		}
		if (null == _xyzData[x][y])
		{
			_xyzData[x][y] = new byte[_edgeSize];
		}
		byte previous = (byte)(_xyzData[x][y][z] - 1);
		_xyzData[x][y][z] = biased;
		return previous;
	}

	/**
	 * Retrieves the value at the provided x, y, z coordinates.
	 * 
	 * @param x The x coordinate, relative to zero.
	 * @param y The y coordinate, relative to zero.
	 * @param z The z coordinate, relative to zero.
	 * @return The value (-1 if there isn't one).
	 */
	public byte get(int x, int y, int z)
	{
		Assert.assertTrue((x >= 0) && (x < _edgeSize));
		Assert.assertTrue((y >= 0) && (y < _edgeSize));
		Assert.assertTrue((z >= 0) && (z < _edgeSize));
		
		byte value = IByteLookup.NOT_FOUND;
		if ((null != _xyzData) && (null != _xyzData[x]) && (null != _xyzData[x][y]))
		{
			// We rely on new entries being zero so we bias this.
			byte biased = _xyzData[x][y][z];
			value = (byte)(biased - 1);
		}
		return value;
	}

	/**
	 * Walks all the values stored within a sub-region of the receiver.  Note that the coordinates passed to the walker
	 * are relative to the given base coordinates, here.
	 * 
	 * @param walker The walker which will receive callbacks for stored data.
	 * @param baseX The X-axis where the walk should begin.
	 * @param baseY The Y-axis where the walk should begin.
	 * @param baseZ The Z-axis where the walk should begin.
	 * @param limit The limit of the scanned area, in each axis.
	 */
	public void walkAllValues(Walker walker, int baseX, int baseY, int baseZ, int limit)
	{
		if (null != _xyzData)
		{
			for (int rx = 0; rx < limit; ++rx)
			{
				int x = rx + baseX;
				if (null != _xyzData[x])
				{
					for (int ry = 0; ry < limit; ++ry)
					{
						int y = ry + baseY;
						if (null != _xyzData[x][y])
						{
							for (int rz = 0; rz < limit; ++rz)
							{
								int z = rz + baseZ;
								// We rely on new entries being zero so we bias this.
								byte biased = _xyzData[x][y][z];
								if (biased > 0)
								{
									byte value = (byte)(biased - 1);
									walker.walk(rx, ry, rz, value);
								}
							}
						}
					}
				}
			}
		}
	}


	public static interface Walker
	{
		public void walk(int x, int y, int z, byte value);
	}
}
