package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiFunction;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * A basic world generator which tries to build a random world just by averaging out random numbers selected based on
 * cuboid x/y addresses and a starting seed.
 * The general design includes the following key points:
 * -the world has a seed and cuboid coordinates are used to generate the "local" seed for each cuboid
 * -the local cuboid is used to determine a "biome vote", cuboid "centre", and "height vote" for each cuboid
 * -the biome votes of the 5x5 cuboid columns around the target are averaged to determine its biome (smoothes
 *  distribution).
 * -the height votes of the 3x3 cuboid columns around the target are averaged to determine its "centre height" (allows
 *  for variation without abrupt changes).
 * -the height value assigned for each block column is determined by averaging the 3 closest "centre heights" in the 3x3
 *  cuboid columns around it, weighted by how close they are to the block.
 * 
 * The general idea is that all the interest is in generating a reasonable height map for each block column as this
 * generator doesn't do anything too special with how it fills in the space underneath these or what it populates above
 * them.
 * 
 * In the future (once this can be play-tested with a more immersive interface), this will likely be replaced with a
 * more traditional noise-based generator.  For now, it allows for a smooth terrain which is at least not repetitive or
 * manually defined.
 */
public class BasicWorldGenerator implements BiFunction<CreatureIdAssigner, CuboidAddress, SuspendedCuboid<CuboidData>> 
{
	public static final int MASK_HEIGHT  = 0x00000F00;
	public static final int SHIFT_HEIGHT  = 8;
	public static final int MASK_BIOME   = 0x0000F000;
	public static final int SHIFT_BIOME   = 12;
	public static final int MASK_YCENTRE = 0x001F0000;
	public static final int SHIFT_YCENTRE = 16;
	public static final int MASK_XCENTRE = 0x03E00000;
	public static final int SHIFT_XCENTRE = 21;

	private final int _seed;

	/**
	 * Creates the world generator.
	 * 
	 * @param env The environment.
	 * @param seed A base seed for the world generator.
	 */
	public BasicWorldGenerator(Environment env, int seed)
	{
		_seed = seed;
	}

	@Override
	public SuspendedCuboid<CuboidData> apply(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
	{
		// TODO:  Implement once this is connected to the loader.
		throw Assert.unreachable();
	}

	/**
	 * Used by tests:  Returns the cuboid-local seed for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The cuboid-local seed.
	 */
	public int test_getCuboidSeed(short cuboidX, short cuboidY)
	{
		return _deterministicRandom(_seed, cuboidX, cuboidY);
	}

	/**
	 * Used by tests:  Returns the biome for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The biome of this cuboid.
	 */
	public int test_getBiome(short cuboidX, short cuboidY)
	{
		int[][] seeds = _buildSeedField5x5(cuboidX, cuboidY);
		return _buildBiomeFromSeeds5x5(seeds);
	}

	/**
	 * Used by tests:  Returns the "centre" of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The "centre" of this cuboid (z coordinate should be ignored).
	 */
	public BlockAddress test_getCentre(short cuboidX, short cuboidY)
	{
		int[][] seeds = _buildSeedField5x5(cuboidX, cuboidY);
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(seeds, yCentres, xCentres);
		
		return new BlockAddress((byte)xCentres[1][1], (byte)yCentres[1][1], (byte)0);
	}

	/**
	 * Used by tests:  Returns the peak value of the "centre" of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The peak height for "centre" of this cuboid.
	 */
	public int test_getPeak(short cuboidX, short cuboidY)
	{
		int[][] seeds = _buildSeedField5x5(cuboidX, cuboidY);
		int[][] heightTotals = _buildHeightField3x3(seeds);
		return heightTotals[1][1];
	}

	/**
	 * Used by tests:  Returns the height-map of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The height map of this cuboid (y-major addressing).
	 */
	public int[][] test_getHeightMap(short cuboidX, short cuboidY)
	{
		// Note that we need to consider "biome" and "cuboid centre height" which requires that we generate the seed values for 5x5 cuboids around this one.
		int[][] seeds = _buildSeedField5x5(cuboidX, cuboidY);
		
		// We also need to determine the height and "peak location" of this cuboid and the 8 neighbours to interpolate the 8 planes in this cuboid.
		int[][] heightTotals = _buildHeightField3x3(seeds);
		
		// centres
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(seeds, yCentres, xCentres);
		
		int thisPeak = heightTotals[1][1];
		int thisPeakY = yCentres[1][1];
		int thisPeakX = xCentres[1][1];
		int[][] heightMapForCuboidColumn = new int[32][32];
		for (int y = 0; y < 32; ++y)
		{
			for (int x = 0; x < 32; ++x)
			{
				int height;
				if ((thisPeakY == y) && (thisPeakX == x))
				{
					height = thisPeak;
				}
				else
				{
					height = _findHeight(heightTotals, yCentres, xCentres, y, x);
				}
				heightMapForCuboidColumn[y][x] = height;
			}
		}
		return heightMapForCuboidColumn;
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

	private static int _biomeVote(int i)
	{
		// We need to pick a value in [0..15]:
		return (MASK_BIOME & i) >> SHIFT_BIOME;
	}

	private int _buildHeightTotal(int[][] seeds, int startY, int startX)
	{
		int total = 0;
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				total += _heightVote(seeds[startY + y][startX + x]);
			}
		}
		return total / 9;
	}

	private static int _heightVote(int i)
	{
		// We need to pick a value in [0..15]:
		return (MASK_HEIGHT & i) >> SHIFT_HEIGHT;
	}

	private static int _yCentre(int i)
	{
		// We need to pick a value in [0..31]:
		return (MASK_YCENTRE & i) >> SHIFT_YCENTRE;
	}

	private static int _xCentre(int i)
	{
		// We need to pick a value in [0..31]:
		return (MASK_XCENTRE & i) >> SHIFT_XCENTRE;
	}

	private int _findHeight(int[][] heightTotals, int[][] yCentres, int[][] xCentres, int thisY, int thisX)
	{
		// We only want to average the heights of the 3 nearest peaks (if we average all 9, we get subtle breaks along cuboid boundaries which will look bad).
		double[] closestDistances = new double[] { 100.0, 100.0, 100.0 };
		int[] closestPeaks = new int[3];
		
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int peak = heightTotals[1 + y][1 + x];
				int yC = yCentres[1 + y][1 + x] + (32 * y);
				int xC = xCentres[1 + y][1 + x] + (32 * x);
				int dY = thisY - yC;
				int dX = thisX - xC;
				int distanceSquare = (dY * dY) + (dX * dX);
				double distance = Math.sqrt((double)distanceSquare);
				
				// The array is 3 elements so just bubble this in.
				int i = 0;
				while ((i < 3) && (distance < closestDistances[2]))
				{
					double swapDistance = closestDistances[i];
					if (distance < swapDistance)
					{
						int swapPeak = closestPeaks[i];
						closestPeaks[i] = peak;
						closestDistances[i] = distance;
						peak = swapPeak;
						distance = swapDistance;
					}
					i += 1;
				}
			}
		}
		double totalDistance = closestDistances[0] + closestDistances[1] + closestDistances[2];
		double total = 0.0;
		double totalWeight = 0.0f;
		for (int j = 0; j < 3; ++j)
		{
			double weight = totalDistance / closestDistances[j];//1.0 - (distances[j] / totalDistance);
			total += (double)closestPeaks[j] * weight;
			totalWeight += weight;
		}
		return Math.round((float)(total / totalWeight));//Math.round((float)Math.sqrt(total) / 9.0f);
	}

	private int[][] _buildSeedField5x5(int baseX, int baseY)
	{
		int[][] seeds = new int[5][5];
		for (int y = -2; y <= 2; ++y)
		{
			for (int x = -2; x <= 2; ++x)
			{
				seeds[2 + y][2 + x] = _deterministicRandom(_seed, baseX + x, baseY + y);
			}
		}
		return seeds;
	}

	private int _buildBiomeFromSeeds5x5(int[][] seeds)
	{
		int biomeTotal = 0;
		for (int y = -2; y <= 2; ++y)
		{
			for (int x = -2; x <= 2; ++x)
			{
				biomeTotal += _biomeVote(seeds[2 + y][2 + x]);
			}
		}
		int biome = biomeTotal / 25;
		return biome;
	}

	private int[][] _buildHeightField3x3(int[][] seeds)
	{
		int[][] heightTotals = new int[3][3];
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				heightTotals[1 + y][1 + x] = _buildHeightTotal(seeds, 2 + y, 2 + x);
			}
		}
		return heightTotals;
	}

	private void _buildCentreField3x3(int[][] seeds, int[][] yCentres, int[][] xCentres)
	{
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int seed = seeds[2 + y][2 + x];
				int cY = _yCentre(seed);
				yCentres[1 + y][1 + x] = cY;
				int cX = _xCentre(seed);
				xCentres[1 + y][1 + x] = cX;
			}
		}
	}
}
