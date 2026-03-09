package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	/**
	 * Updates the given oldHeights height map for cuboid, given the block type changes included in blocksChangedToAir
	 * and blocksChangedToNotAir, returning the original oldHeights instance if unchanged.
	 * 
	 * @param oldHeights The height map to update.
	 * @param cuboid The new state of the cuboid.
	 * @param blocksChangedToAir The blocks which became air after being non-air.
	 * @param blocksChangedToNotAir The blocks which became non-air (potentially starting as non-air or air).
	 * @return The new height map (or oldHeights if unchanged).
	 */
	public static CuboidHeightMap updateHeightMap(CuboidHeightMap oldHeights
		, IReadOnlyCuboidData cuboid
		, List<BlockAddress> blocksChangedToAir
		, List<BlockAddress> blocksChangedToNotAir
	)
	{
		// We need to check which columns need to be re-checked and we will batch them.
		List<BlockAddress> columnsToScan = new ArrayList<>();
		for (BlockAddress address : blocksChangedToAir)
		{
			// Since these changed to air, we might need to rescan under them to see where the high point is.
			byte value = oldHeights.getHightestSolidBlock(address.x(), address.y());
			
			// This can't be unknown if it became air since it is only unknown if the entire column already is air.
			Assert.assertTrue(CuboidHeightMap.UNKNOWN_HEIGHT != value);
			
			// In the case where this became air, that only matters if it was the highest block.
			if (address.z() == value)
			{
				// This was the highest block and we removed it so rescan.
				columnsToScan.add(address);
			}
		}
		
		// We also need to track the cases which were more basically changed.
		List<BlockAddress> columnsToUpdateDirectly = new ArrayList<>();
		for (BlockAddress address : blocksChangedToNotAir)
		{
			// If this became not air, we can update the height only if this was greater than the original height.
			// Note that this may have been non-air, to begin with, so this could be the highest value and not changing.
			byte value = oldHeights.getHightestSolidBlock(address.x(), address.y());
			if (address.z() > value)
			{
				columnsToUpdateDirectly.add(address);
			}
		}
		
		CuboidHeightMap mapToReturn;
		if (!columnsToScan.isEmpty() || !columnsToUpdateDirectly.isEmpty())
		{
			// We need to change the height map so create a new instance.
			byte[][] newMap = new byte[Encoding.CUBOID_EDGE_SIZE][];
			byte[][] unsafeRead = oldHeights.getUnsafeAccess();
			for (int i = 0; i < newMap.length; ++i)
			{
				byte[] row = unsafeRead[i];
				if (null != row)
				{
					newMap[i] = row.clone();
				}
			}
			
			// We walk the direct update case first, since they are simple and may allow us to drop other columns to scan.
			for (BlockAddress address : columnsToUpdateDirectly)
			{
				byte x = address.x();
				byte y = address.y();
				byte z = address.z();
				byte[] row = _getAsInflatedRow(newMap, y);
				byte value = row[x];
				
				if (z > value)
				{
					row[x] = z;
				}
			}
			
			// We can now walk the column scanning cases and set up a batch request for any we still don't know (use a set since these columns may overlap).
			Set<BlockAddress> batchRequest = new HashSet<>();
			for (BlockAddress address : columnsToScan)
			{
				byte x = address.x();
				byte y = address.y();
				byte z = address.z();
				byte[] row = newMap[y];
				byte value = (null != row)
					? row[x]
					: CuboidHeightMap.UNKNOWN_HEIGHT
				;
				if (z == value)
				{
					// We will scan this column so start by initializing it to unknown.
					// We weren't previously unknown so this row must be here.
					Assert.assertTrue(null != row);
					
					row[x] = CuboidHeightMap.UNKNOWN_HEIGHT;
					
					// Check if anything below this should become the highest block in the column.
					for (byte h = 0; h < z; ++h)
					{
						batchRequest.add(new BlockAddress(x, y, h));
					}
				}
			}
			
			// Do the batch request so we can find the highest block.
			if (!batchRequest.isEmpty())
			{
				BlockAddress[] array = batchRequest.toArray((int size) -> new BlockAddress[size]);
				IReadOnlyCuboidData.BlockAddressBatchComparator comparator = new IReadOnlyCuboidData.BlockAddressBatchComparator();
				Arrays.sort(array, comparator);
				
				short[] blocks = cuboid.batchReadData15(AspectRegistry.BLOCK, array);
				for (int i = 0; i < array.length; ++i)
				{
					boolean isAir = (0 == blocks[i]);
					
					if (!isAir)
					{
						// This is a solid block so write it as the highest unless something else above us already is.
						BlockAddress address = array[i];
						byte x = address.x();
						byte y = address.y();
						byte z = address.z();
						byte[] row = _getAsInflatedRow(newMap, y);
						byte value = row[x];
						if (z > value)
						{
							row[x] = z;
						}
					}
				}
			}
			
			mapToReturn = CuboidHeightMap.wrap(newMap);
		}
		else
		{
			// Nothing is changing so just return the original.
			mapToReturn = oldHeights;
		}
		return mapToReturn;
	}


	private static void _populateHeightMap(byte[][] heightMap, IReadOnlyCuboidData cuboid)
	{
		cuboid.walkData(AspectRegistry.BLOCK, (BlockAddress base, byte size, Short value) -> {
			byte baseX = base.x();
			byte baseY = base.y();
			byte highestZ = (byte)(base.z() + size - 1);
			for (int y = 0; y < size; ++y)
			{
				byte[] row = _getAsInflatedRow(heightMap, baseY + y);
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

	private static byte[] _getAsInflatedRow(byte[][] heightMap, int y)
	{
		byte[] row = heightMap[y];
		if (null == row)
		{
			// We need to lazily populate this row since we now know something about it.
			row = new byte[Encoding.CUBOID_EDGE_SIZE];
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				row[x] = CuboidHeightMap.UNKNOWN_HEIGHT;
			}
			heightMap[y] = row;
		}
		return row;
	}
}
