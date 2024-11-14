package com.jeffdisher.october.logic;

import java.util.HashMap;
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
		byte[][] rawMap = _createUniformHeightMap(CuboidHeightMap.UNKNOWN_HEIGHT);
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
	 * Similar to buildColumnMaps but avoids redundantly reconstructing those which are unchanged from the previous
	 * tick.  This allows the copy-on-write semantics of the data model to be used to avoid duplicated work.  It also
	 * means that the cost of rebuilding height maps in the single-threaded merge phase is based on the number of
	 * changed cuboids, not the number of loaded cuboids, at the increased cost of slightly more collection management.
	 * 
	 * @param previousColumnMaps The column maps from the previous tick.
	 * @param previousCuboidMaps The cuboid maps from the previous tick (including anything added).
	 * @param changedCuboidMaps The cuboid maps changed this tick.
	 * @param allLoadedCuboidAddresses The set of addresses of all loaded cuboids.
	 * @return
	 */
	public static Map<CuboidColumnAddress, ColumnHeightMap> rebuildColumnMaps(Map<CuboidColumnAddress, ColumnHeightMap> previousColumnMaps
			, Map<CuboidAddress, CuboidHeightMap> previousCuboidMaps
			, Map<CuboidAddress, CuboidHeightMap> changedCuboidMaps
			, Set<CuboidAddress> allLoadedCuboidAddresses
	)
	{
		// NOTE:  This just rebuilds these if they are stale but we could incrementally update them, in the future.
		
		// Build the changed column maps.
		// -identify which columns need to be rebuilt
		Set<CuboidColumnAddress> changedColumns = changedCuboidMaps.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.collect(Collectors.toUnmodifiableSet())
		;
		// -add in anything which has been newly loaded and isn't yet merged into a column
		Set<CuboidColumnAddress> newColumns = previousCuboidMaps.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.filter((CuboidColumnAddress column) -> !previousColumnMaps.containsKey(column))
				.collect(Collectors.toUnmodifiableSet())
		;
		
		Map<CuboidAddress, CuboidHeightMap> allMapsToConsider = new HashMap<>();
		// -add in any of the original maps for this column
		for (Map.Entry<CuboidAddress, CuboidHeightMap> existing : previousCuboidMaps.entrySet())
		{
			CuboidAddress key = existing.getKey();
			CuboidColumnAddress column = key.getColumn();
			if (changedColumns.contains(column) || newColumns.contains(column))
			{
				allMapsToConsider.put(key, existing.getValue());
			}
		}
		// -add the changed cuboids, over-writing any of the previous ones, where colliding
		allMapsToConsider.putAll(changedCuboidMaps);
		
		// Build the new column maps.
		Map<CuboidColumnAddress, ColumnHeightMap> changedMaps = _buildColumnMaps(allMapsToConsider);
		
		// Build the collection to return.
		// -start with the original map
		Map<CuboidColumnAddress, ColumnHeightMap> newMap = new HashMap<>(previousColumnMaps);
		// -remove anything which doesn't refer to a loaded cuboid
		Set<CuboidColumnAddress> loadedColumns = allLoadedCuboidAddresses.stream().map((CuboidAddress address) -> address.getColumn()).collect(Collectors.toUnmodifiableSet());
		newMap.keySet().retainAll(loadedColumns);
		// -add in anything new
		newMap.putAll(changedMaps);
		return newMap;
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
				for (int x = 0; x < size; ++x)
				{
					byte entry = heightMap[baseY + y][baseX + x];
					heightMap[baseY + y][baseX + x] = (byte)Math.max(entry, highestZ);
				}
			}
		}, (short)0);
	}

	private static Map<CuboidColumnAddress, ColumnHeightMap> _buildColumnMaps(Map<CuboidAddress, CuboidHeightMap> perCuboid)
	{
		Map<CuboidColumnAddress, ColumnHeightMap.Builder> builders = new HashMap<>();
		for (Map.Entry<CuboidAddress, CuboidHeightMap> elt : perCuboid.entrySet())
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
}
