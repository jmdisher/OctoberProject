package com.jeffdisher.october.persistence;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;


/**
 * The generic interface world generators must implement.
 */
public interface IWorldGenerator
{
	/**
	 * Generates a new cuboid.
	 * 
	 * @param creatureIdAssigner The ID assigner for any new creatures spawned within the cuboid.
	 * @param address The address of the cuboid to generate.
	 * @return The cuboid data and meta-data.
	 */
	SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address);

	/**
	 * This just returns a "reasonable" spawn location in the world but where the target starting location is is handled
	 * purely internally.
	 * Currently, this just returns a location in the 0,0 column which is standing on the ground where the world
	 * would be generated (since it cannot account for changes since the world was generated).
	 * 
	 * @return The location where new entities can be reasonably spawned.
	 */
	EntityLocation getDefaultSpawnLocation();
}
