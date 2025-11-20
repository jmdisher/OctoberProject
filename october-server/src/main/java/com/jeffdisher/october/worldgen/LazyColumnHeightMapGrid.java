package com.jeffdisher.october.worldgen;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Encoding;


/**
 * Lazily constructs the ColumnHeightMap instances around a central point.
 */
public class LazyColumnHeightMapGrid
{
	public static final int MASK_HEIGHT  = 0x00000F00;
	public static final int SHIFT_HEIGHT  = 8;
	public static final int MASK_YCENTRE = 0x001F0000;
	public static final int SHIFT_YCENTRE = 16;
	public static final int MASK_XCENTRE = 0x03E00000;
	public static final int SHIFT_XCENTRE = 21;

	/**
	 * Used by tests:  Returns the "centre" of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param seed The random seed to use.
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The "centre" of this cuboid (z coordinate should be ignored).
	 */
	public static BlockAddress test_getCentre(int seed, short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(seed, cuboidX, cuboidY);
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(seeds.view(), yCentres, xCentres);
		
		return BlockAddress.fromInt(xCentres[1][1], yCentres[1][1], 0);
	}

	/**
	 * Used by tests:  Returns the peak value of the "centre" of the cuboid for the given cuboid X/Y address (not
	 * adjusting for biome).
	 * 
	 * @param seed The random seed to use.
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The raw peak height for "centre" of this cuboid.
	 */
	public static int test_getRawPeak(int seed, short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(seed, cuboidX, cuboidY);
		return _buildHeightTotal(seeds.view());
	}

	/**
	 * Used by tests:  Returns the peak value of the "centre" of the cuboid for the given cuboid X/Y address (after
	 * adjusting for biome).
	 * 
	 * @param seed The random seed to use.
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The biome-adjusted peak height for "centre" of this cuboid.
	 */
	public static int test_getAdjustedPeak(int seed, short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(seed, cuboidX, cuboidY);
		PerColumnRandomSeedField.View subField = seeds.view();
		return _peakWithinBiome(subField);
	}

	/**
	 * Used by tests:  Returns the height-map of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param seed The random seed to use.
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The height map of this cuboid.
	 */
	public static ColumnHeightMap test_getHeightMap(int seed, short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(seed, cuboidX, cuboidY);
		return _generateHeightMapForCuboidColumn(seeds.view());
	}


	private final PerColumnRandomSeedField.View _centre;
	private final ColumnHeightMap[][] _yMajorGrid;

	/**
	 * Creates the lazy grid built around the given centre seed field.
	 * 
	 * @param centre The seed field which will be treated as (0, 0).
	 */
	public LazyColumnHeightMapGrid(PerColumnRandomSeedField.View centre)
	{
		_centre = centre;
		_yMajorGrid = new ColumnHeightMap[3][3];
	}

	/**
	 * Fetches the ColumnHeightMap for the given relative column coordinates, lazily constructing if not yet available.
	 * 
	 * @param x The relative X column coordinate, in cuboid offsets.
	 * @param y The relative Y column coordinate, in cuboid offsets.
	 * @return The ColumnHeightMap for the relative column.
	 */
	public ColumnHeightMap fetchHeightMapForCuboidColumn(int x, int y)
	{
		ColumnHeightMap map = _yMajorGrid[1 + y][1 + x];
		if (null == map)
		{
			map = _generateHeightMapForCuboidColumn(_centre.relativeView(x, y));
			_yMajorGrid[1 + y][1 + x] = map;
		}
		return map;
	}


	private static ColumnHeightMap _generateHeightMapForCuboidColumn(PerColumnRandomSeedField.View subField)
	{
		// Note that we need to consider "biome" and "cuboid centre height" which requires that we generate the seed values for 5x5 cuboids around this one.
		// centres
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(subField, yCentres, xCentres);
		
		// Note that the peak height is a combination of the 3x3 average peak height and the 5x5 average biome value.
		int thisPeak = _peakWithinBiome(subField);
		int thisPeakY = yCentres[1][1];
		int thisPeakX = xCentres[1][1];
		int[][] heightMapForCuboidColumn = new int[Encoding.CUBOID_EDGE_SIZE][Encoding.CUBOID_EDGE_SIZE];
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height;
				if ((thisPeakY == y) && (thisPeakX == x))
				{
					height = thisPeak;
				}
				else
				{
					height = _findHeight(subField, yCentres, xCentres, y, x);
				}
				heightMapForCuboidColumn[y][x] = height;
			}
		}
		return ColumnHeightMap.wrap(heightMapForCuboidColumn);
	}

	private static void _buildCentreField3x3(PerColumnRandomSeedField.View subField, int[][] yCentres, int[][] xCentres)
	{
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int seed = subField.get(x, y);
				int cY = _yCentre(seed);
				yCentres[1 + y][1 + x] = cY;
				int cX = _xCentre(seed);
				xCentres[1 + y][1 + x] = cX;
			}
		}
	}

	private static int _peakWithinBiome(PerColumnRandomSeedField.View subField)
	{
		int rawHeight = _buildHeightTotal(subField);
		Biomes.Biome biome = Biomes.chooseBiomeFromSeeds5x5(subField);
		int offset = biome.heightOffset();
		return rawHeight + offset;
	}

	private static int _findHeight(PerColumnRandomSeedField.View subField, int[][] yCentres, int[][] xCentres, int thisY, int thisX)
	{
		// We only want to average the heights of the 3 nearest peaks (if we average all 9, we get subtle breaks along cuboid boundaries which will look bad).
		double[] closestDistances = new double[] { 100.0, 100.0, 100.0 };
		int[] closestPeaks = new int[3];
		
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int peak = _peakWithinBiome(subField.relativeView(x, y));
				int yC = yCentres[1 + y][1 + x] + (Encoding.CUBOID_EDGE_SIZE * y);
				int xC = xCentres[1 + y][1 + x] + (Encoding.CUBOID_EDGE_SIZE * x);
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
			double weight = totalDistance / closestDistances[j];
			total += (double)closestPeaks[j] * weight;
			totalWeight += weight;
		}
		return Math.round((float)(total / totalWeight));
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

	private static int _buildHeightTotal(PerColumnRandomSeedField.View field)
	{
		int total = 0;
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				total += _heightVote(field.get(x, y));
			}
		}
		return total / 9;
	}

	private static int _heightVote(int i)
	{
		// We need to pick a value in [0..15]:
		return (MASK_HEIGHT & i) >> SHIFT_HEIGHT;
	}
}
