package com.jeffdisher.october.worldgen;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.jeffdisher.october.utils.Assert;


/**
 * Stores the random number seeds for columns of cuboids for generation.  The field stores the random seeds for the 9x9
 * columns around a start point.
 */
public class PerColumnRandomSeedField
{
	/**
	 * A factory to create a new column seed field around cuboidX/cuboidY with the given world seed.
	 * 
	 * @param seed The seed for the world.
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The field of per-column cuboid generation seeds around this column.
	 */
	public static PerColumnRandomSeedField buildSeedField9x9(int seed, short cuboidX, short cuboidY)
	{
		int[][] seeds = new int[9][9];
		for (int y = -4; y <= 4; ++y)
		{
			for (int x = -4; x <= 4; ++x)
			{
				seeds[4 + y][4 + x] = _deterministicRandom(seed, cuboidX + x, cuboidY + y);
			}
		}
		return new PerColumnRandomSeedField(seeds);
	}

	private static int _deterministicRandom(int seed, int x, int y)
	{
		// This "deterministic random" is random since it is based on the given random seed but "deterministic" in that
		// it will be the same for a given seed-y-x triplet.
		// We write these into the buffer in various combinations just because it seems to give the hash better distribution.
		ByteBuffer buffer = ByteBuffer.allocate(4 * Integer.BYTES);
		buffer.putInt(y);
		buffer.putInt(x);
		buffer.putInt(x ^ y);
		buffer.putInt(x + y);
		int hash = Arrays.hashCode(buffer.array());
		return seed ^ hash;
	}


	private final int[][] _seeds;

	private PerColumnRandomSeedField(int[][] seeds)
	{
		Assert.assertTrue(9 == seeds.length);
		_seeds = seeds;
	}

	public int get(int relX, int relY)
	{
		return _seeds[4 + relY][4 + relX];
	}

	public View view()
	{
		return new View(0, 0);
	}


	/**
	 * A sub-view of the random field to allow for simpler addressing.  When created, this field will centre around a
	 * different location in the parent field or view.
	 */
	public class View
	{
		private final int _relX;
		private final int _relY;
		
		private View(int relX, int relY)
		{
			_relX = relX;
			_relY = relY;
		}
		public View relativeView(int relX, int relY)
		{
			return new View(_relX + relX, _relY + relY);
		}
		public int get(int relX, int relY)
		{
			return PerColumnRandomSeedField.this.get(_relX + relX, _relY + relY);
		}
	}
}
