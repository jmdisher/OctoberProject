package com.jeffdisher.october.worldgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;


/**
 * This world generator is only used during tests.  It will only return pre-generated cuboids which were fed into it
 * before it started.
 */
public class PreloadedWorldGenerator implements IWorldGenerator
{
	private final Map<CuboidAddress, CuboidData> _preLoaded = new HashMap<>();

	/**
	 * Loads the given cuboid.  Note that this must be called before any consumer of the loader starts up as there is
	 * no synchronization on this path.
	 * Note that this is a temporary interface and will be replaced by a generator and/or pre-constructed world save in
	 * the future.
	 * 
	 * @param cuboid The cuboid to add to the pre-loaded data set.
	 */
	public void preload(CuboidData cuboid)
	{
		_preLoaded.put(cuboid.getCuboidAddress(), cuboid);
	}

	@Override
	public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address, long gameTimeMillis)
	{
		// We generally return null unless given something explicit.
		SuspendedCuboid<CuboidData> data = null;
		if (_preLoaded.containsKey(address))
		{
			// We will "consume" this since we will load from disk on the next call.
			CuboidData preloaded = _preLoaded.remove(address);
			data = new SuspendedCuboid<>(preloaded
					, HeightMapHelpers.buildHeightMap(preloaded)
					, List.of()
					, List.of()
					, Map.of()
					, List.of()
			);
		}
		return data;
	}

	@Override
	public EntityLocation getDefaultSpawnLocation()
	{
		return new EntityLocation(0.0f, 0.0f, 0.0f);
	}
}
