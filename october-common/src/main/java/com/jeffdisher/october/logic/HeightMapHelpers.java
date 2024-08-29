package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.worldgen.Structure;


/**
 * Static helpers related to height map creation and manipulation.
 */
public class HeightMapHelpers
{
	/**
	 * Creates a raw cuboid height map with a single value for every entry.
	 * 
	 * @param heightMapValue The value to store in every entry.
	 * @return The raw y-major height map.
	 */
	public static byte[][] createUniformHeightMap(byte heightMapValue)
	{
		return _createUniformHeightMap(heightMapValue);
	}

	/**
	 * Updates a given heightMap by applying the heights within cuboid such that every entry in the heightMap will be
	 * the max of its original value and this new value.
	 * 
	 * @param heightMap The height map to read and write.
	 * @param cuboid The cuboid data to read.
	 */
	public static void populateHeightMap(byte[][] heightMap, IReadOnlyCuboidData cuboid)
	{
		_populateHeightMap(heightMap, cuboid);
	}

	/**
	 * Builds a CuboidHeightMap object from the block data in the given cuboid.
	 * 
	 * @param cuboid The cuboid data to read.
	 * @return The height map object for this cuboid.
	 */
	public static CuboidHeightMap buildHeightMap(IReadOnlyCuboidData cuboid)
	{
		byte[][] rawMap = _createUniformHeightMap(CuboidHeightMap.UNKNOWN_HEIGHT);
		_populateHeightMap(rawMap, cuboid);
		return CuboidHeightMap.wrap(rawMap);
	}


	private static byte[][] _createUniformHeightMap(byte heightMapValue)
	{
		byte[][] rawHeight = new byte[Structure.CUBOID_EDGE_SIZE][Structure.CUBOID_EDGE_SIZE];
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
			{
				rawHeight[y][x] = heightMapValue;
			}
		}
		return rawHeight;
	}

	private static void _populateHeightMap(byte[][] heightMap, IReadOnlyCuboidData cuboid)
	{
		cuboid.walkData(AspectRegistry.BLOCK, (BlockAddress base, BlockAddress size, Short value) -> {
			byte baseX = base.x();
			byte baseY = base.y();
			byte highestZ = (byte)(base.z() + size.z() - 1);
			for (int y = 0; y < size.y(); ++y)
			{
				for (int x = 0; x < size.x(); ++x)
				{
					byte entry = heightMap[baseY + y][baseX + x];
					heightMap[baseY + y][baseX + x] = (byte)Math.max(entry, highestZ);
				}
			}
		}, (short)0);
	}
}
