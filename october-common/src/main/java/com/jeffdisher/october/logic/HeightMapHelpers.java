package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * Static helpers related to height map creation and manipulation.
 * These are pulled out here mostly just to make the unit testing easier.
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
		byte[][] rawMap = new byte[Encoding.CUBOID_EDGE_SIZE][];
		_populateHeightMap(rawMap, cuboid);
		return CuboidHeightMap.wrap(rawMap);
	}

	/**
	 * Merges all of the given per-cuboid CuboidHeightMap instances into per-column ColumnHeightMap instances.
	 * 
	 * @param perCuboid The CuboidHeightMap instances, addressed by CuboidAddress.
	 * @return The merged result as ColumnHeightMap instances addressed by CuboidColumnAddress.
	 */
	public static Map<CuboidColumnAddress, ColumnHeightMap> buildColumnMaps(Map<CuboidAddress, CuboidHeightMap> perCuboid)
	{
		return _buildColumnMaps(perCuboid);
	}

	/**
	 * Merges the given CuboidHeightMap instances into a single ColumnHeightMap.
	 * Note that this asserts that cuboidHeightMaps only applies to a single column and has no duplicated addresses.
	 * 
	 * @param cuboidHeightMaps The CuboidHeightMap instances to merge.
	 * @return The resultant ColumnHeightMap.
	 */
	public static ColumnHeightMap buildSingleColumn(Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps)
	{
		// We want to sort the CuboidHeightMap, descending by z, so that we can build the column map from the top down.
		List<Map.Entry<CuboidAddress, CuboidHeightMap>> list = _descendingInColumns(cuboidHeightMaps);
		
		ColumnHeightMap.Builder builder = ColumnHeightMap.build();
		if (!list.isEmpty())
		{
			CuboidAddress firstAddress = list.get(0).getKey();
			int requiredX = firstAddress.x();
			int requiredY = firstAddress.y();
			for (Map.Entry<CuboidAddress, CuboidHeightMap> ent : list)
			{
				CuboidAddress key = ent.getKey();
				Assert.assertTrue(key.x() == requiredX);
				Assert.assertTrue(key.y() == requiredY);
				
				builder.consume(ent.getValue(), key);
			}
		}
		return builder.freeze();
	}


	private static byte[][] _createUniformHeightMap(byte heightMapValue)
	{
		byte[][] rawHeight = new byte[Encoding.CUBOID_EDGE_SIZE][Encoding.CUBOID_EDGE_SIZE];
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				rawHeight[y][x] = heightMapValue;
			}
		}
		return rawHeight;
	}

	private static void _populateHeightMap(byte[][] heightMap, IReadOnlyCuboidData cuboid)
	{
		cuboid.walkData(AspectRegistry.BLOCK, (BlockAddress base, byte size, Short value) -> {
			byte baseX = base.x();
			byte baseY = base.y();
			byte highestZ = (byte)(base.z() + size - 1);
			for (int y = 0; y < size; ++y)
			{
				byte[] row = heightMap[baseY + y];
				if (null == row)
				{
					// We need to lazily populate this row since we now know something about it.
					row = new byte[Encoding.CUBOID_EDGE_SIZE];
					for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
					{
						row[x] = CuboidHeightMap.UNKNOWN_HEIGHT;
					}
					heightMap[baseY + y] = row;
				}
				for (int x = 0; x < size; ++x)
				{
					byte entry = row[baseX + x];
					row[baseX + x] = (byte)Math.max(entry, highestZ);
				}
			}
		}, (short)0);
	}

	private static Map<CuboidColumnAddress, ColumnHeightMap> _buildColumnMaps(Map<CuboidAddress, CuboidHeightMap> perCuboid)
	{
		List<Map.Entry<CuboidAddress, CuboidHeightMap>> list = _descendingInColumns(perCuboid);
		Map<CuboidColumnAddress, ColumnHeightMap.Builder> builders = new HashMap<>();
		for (Map.Entry<CuboidAddress, CuboidHeightMap> elt : list)
		{
			CuboidAddress address = elt.getKey();
			CuboidColumnAddress column = address.getColumn();
			ColumnHeightMap.Builder builder = builders.get(column);
			if (null == builder)
			{
				builder = ColumnHeightMap.build();
				builders.put(column, builder);
			}
			builder.consume(elt.getValue(), address);
		}
		return builders.entrySet().stream().collect(Collectors.toUnmodifiableMap(
				(Map.Entry<CuboidColumnAddress, ColumnHeightMap.Builder> elt) -> elt.getKey()
				, (Map.Entry<CuboidColumnAddress, ColumnHeightMap.Builder> elt) -> elt.getValue().freeze()
		));
	}

	private static List<Map.Entry<CuboidAddress, CuboidHeightMap>> _descendingInColumns(Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps)
	{
		List<Map.Entry<CuboidAddress, CuboidHeightMap>> list = cuboidHeightMaps.entrySet().stream()
			.sorted((Map.Entry<CuboidAddress, CuboidHeightMap> one, Map.Entry<CuboidAddress, CuboidHeightMap> two) -> {
				return two.getKey().z() - one.getKey().z();
			})
			.toList()
		;
		return list;
	}
}
