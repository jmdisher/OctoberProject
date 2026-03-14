package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Pair;
import com.jeffdisher.october.utils.Assert;


/**
 * A container for the shadow/projected split of world components (cuboids, etc) used in SpeculativeProjection.
 * Note that the internal fields act as a multi-index over the same _Tuple instances (which are only created/destroyed
 * when cuboids are added/removed via server instruction).
 */
public class WorldContainer
{
	private final Map<CuboidAddress, _Tuple> _worldCuboids = new HashMap<>();
	private final Map<CuboidColumnAddress, Map<Short, _Tuple>> _worldColumns = new HashMap<>();
	private final Map<CuboidAddress, _Tuple> _projectedCuboids = new HashMap<>();

	/**
	 * @return The set of all cuboid addresses known to the container.
	 */
	public Set<CuboidAddress> getAllCuboidAddresses()
	{
		return Collections.unmodifiableSet(_worldCuboids.keySet());
	}

	/**
	 * Drops any projected cuboids or height maps so that future calls will only see the shadow instances.
	 */
	public void clearProjectedStates()
	{
		for (_Tuple tuple : _projectedCuboids.values())
		{
			tuple.projectedCuboid = null;
			tuple.projectedHeightMap = null;
		}
		_projectedCuboids.clear();
	}

	/**
	 * Adds the given list of new cuboids and associated height maps to the collection of shadow data.
	 * 
	 * @param newData The new authoritative server data.
	 */
	public void addShadowCuboids(List<Pair<IReadOnlyCuboidData, CuboidHeightMap>> newData)
	{
		for (Pair<IReadOnlyCuboidData, CuboidHeightMap> elt : newData)
		{
			IReadOnlyCuboidData cuboid = elt.one();
			CuboidHeightMap heightMap = elt.two();
			CuboidAddress address = cuboid.getCuboidAddress();
			
			CuboidColumnAddress column = address.getColumn();
			short z = address.z();
			Map<Short, _Tuple> map = _worldColumns.get(column);
			if (null == map)
			{
				map = new HashMap<>();
				_worldColumns.put(column, map);
			}
			
			// This shouldn't already be here.
			Assert.assertTrue(!map.containsKey(z));
			_Tuple tuple = new _Tuple();
			map.put(z, tuple);
			_worldCuboids.put(address, tuple);
			
			tuple.shadowCuboid = cuboid;
			tuple.shadowHeightMap = heightMap;
		}
	}

	/**
	 * Removes the given list of cuboid addresses (including the shadow/projected versions of cuboid and height map
	 * data).
	 * 
	 * @param addresses The cuboid addresses to remove from the container.
	 */
	public void removeCuboids(List<CuboidAddress> addresses)
	{
		for (CuboidAddress address : addresses)
		{
			CuboidColumnAddress column = address.getColumn();
			Map<Short, _Tuple> map = _worldColumns.get(column);
			
			map.remove(address.z());
			if (map.isEmpty())
			{
				_worldColumns.remove(column);
			}
			_worldCuboids.remove(address);
			_projectedCuboids.remove(address);
		}
	}

	/**
	 * Updates the internal shadow cuboids by applying the given list of updates to them.
	 * Note that this is an unusual method in that it doesn't just organize access to internal data, but directly
	 * updates it.  While this is potentially an inappropriate kind of functionality to add to this class, it shrinks
	 * down the API dramatically.
	 * 
	 * @param cuboidUpdates The list of block updates to apply to the cuboids in the shadow set.
	 */
	public void applyUpdatesToShadowCuboids(List<MutationBlockSetBlock> cuboidUpdates)
	{
		// Group these by cuboid.
		Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply = new HashMap<>();
		for (MutationBlockSetBlock update : cuboidUpdates)
		{
			AbsoluteLocation location = update.getAbsoluteLocation();
			CuboidAddress address = location.getCuboidAddress();
			
			List<MutationBlockSetBlock> list = updatesToApply.get(address);
			if (null == list)
			{
				list = new ArrayList<>();
				updatesToApply.put(address, list);
			}
			list.add(update);
		}
		
		// Apply these by cuboid.
		for (Map.Entry<CuboidAddress, List<MutationBlockSetBlock>> entry : updatesToApply.entrySet())
		{
			CuboidAddress address = entry.getKey();
			_Tuple cuboid = _worldCuboids.get(address);
			IReadOnlyCuboidData readOnly = cuboid.shadowCuboid;
			CuboidData mutableCuboid = CuboidData.mutableClone(readOnly);
			
			// Apply the changes and determine what is required to update the height map.
			List<BlockAddress> blocksChangedToAir = new ArrayList<>();
			List<BlockAddress> blocksChangedToNotAir = new ArrayList<>();
			for (MutationBlockSetBlock update : entry.getValue())
			{
				short blockValueSet = update.applyState(mutableCuboid);
				if (0 == blockValueSet)
				{
					blocksChangedToAir.add(update.getAbsoluteLocation().getBlockAddress());
				}
				else if (blockValueSet > 0)
				{
					blocksChangedToNotAir.add(update.getAbsoluteLocation().getBlockAddress());
				}
			}
			
			// We can now update the height map.
			CuboidHeightMap oldHeightMap = cuboid.shadowHeightMap;
			CuboidHeightMap newHeightMap = HeightMapHelpers.updateHeightMap(oldHeightMap, mutableCuboid, blocksChangedToAir, blocksChangedToNotAir);
			
			// Save these back to the tuple.
			cuboid.shadowCuboid = mutableCuboid;
			cuboid.shadowHeightMap = newHeightMap;
		}
	}

	/**
	 * @param address The cuboid address.
	 * @return A reference to the projected cuboid state or shadow, if there is no projection.
	 */
	public IReadOnlyCuboidData getProjectedOrShadowCuboid(CuboidAddress address)
	{
		IReadOnlyCuboidData cuboid = null;
		_Tuple tuple = _worldCuboids.get(address);
		if (null != tuple)
		{
			cuboid = (null != tuple.projectedCuboid)
				? tuple.projectedCuboid
				: tuple.shadowCuboid
			;
		}
		return cuboid;
	}

	/**
	 * @param address The cuboid address.
	 * @return A reference to the projected cuboid height map or shadow, if there is no projection.
	 */
	public CuboidHeightMap getProjectedOrShadowHeightMap(CuboidAddress address)
	{
		CuboidHeightMap heightMap = null;
		_Tuple tuple = _worldCuboids.get(address);
		if (null != tuple)
		{
			heightMap = (null != tuple.projectedHeightMap)
				? tuple.projectedHeightMap
				: tuple.shadowHeightMap
			;
		}
		return heightMap;
	}

	/**
	 * Updates the projected cuboid and height map at the given address.
	 * 
	 * @param address The cuboid address.
	 * @param projectedCuboid The projected cuboid to store.
	 * @param projectedHeightMap The height map to store (ignored if null).
	 */
	public void setProjectedCuboidAndMap(CuboidAddress address
		, IReadOnlyCuboidData projectedCuboid
		, CuboidHeightMap projectedHeightMap
	)
	{
		_Tuple tuple = _worldCuboids.get(address);
		tuple.projectedCuboid = projectedCuboid;
		if (null != projectedHeightMap)
		{
			tuple.projectedHeightMap = projectedHeightMap;
		}
		// Note that this may already be in the projected map.
		_projectedCuboids.put(address, tuple);
	}

	/**
	 * Retrieves the height maps (projected instances or shadows, if there are no projections) of cuboids which exist
	 * within the specified columns.
	 * 
	 * @param columnsToGenerate The set of columns describing the cuboid height maps which must be fetched.
	 * @return The map of cuboid addresses to height maps for known cuboids in these columns (projected, preferred).
	 */
	public Map<CuboidAddress, CuboidHeightMap> getHeightMapsInColumns(Set<CuboidColumnAddress> columnsToGenerate)
	{
		Map<CuboidAddress, CuboidHeightMap> cuboidMaps = new HashMap<>();
		for (CuboidColumnAddress column : columnsToGenerate)
		{
			Map<Short, _Tuple> inColumn = _worldColumns.get(column);
			for (_Tuple tuple : inColumn.values())
			{
				CuboidAddress address = tuple.shadowCuboid.getCuboidAddress();
				CuboidHeightMap heightMap = (null != tuple.projectedHeightMap)
					? tuple.projectedHeightMap
					: tuple.shadowHeightMap
				;
				Assert.assertTrue(null != heightMap);
				cuboidMaps.put(address, heightMap);
			}
		}
		return cuboidMaps;
	}


	// Note that this is a shared mutable instances, shared by all the indices.
	private static class _Tuple
	{
		public IReadOnlyCuboidData shadowCuboid;
		public CuboidHeightMap shadowHeightMap;
		public IReadOnlyCuboidData projectedCuboid;
		public CuboidHeightMap projectedHeightMap;
	}
}
