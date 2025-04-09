package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * The algorithm for spawning creatures in the world.
 */
public class CreatureSpawner
{
	/**
	 * The distance around a player where no new hostile mobs will spawn.
	 */
	public static final float SPAWN_DENIAL_RANGE = 16.0f;

	/**
	 * Attempts to spawn a single creature by randomly selecting a spawning location in the given world.  It will spawn
	 * the new creature in the given context.
	 * 
	 * @param context The context for the current tick.
	 * @param existingEntities The existing entities in the world as of this tick.
	 * @param completedCuboids The map of all cuboids from the previous tick.
	 * @param completedHeightMaps The per-column height maps from the previous tick.
	 * @param completedCreatures The map of all creatures from the previous tick.
	 */
	public static void trySpawnCreature(TickProcessingContext context
			, EntityCollection existingEntities
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
			, Map<Integer, CreatureEntity> completedCreatures
	)
	{
		int cuboidCount = completedCuboids.size();
		int estimatedTargetCreatureCount = cuboidCount * context.config.hostilesPerCuboidTarget;
		int creatureCount = completedCreatures.size();
		
		if (creatureCount < estimatedTargetCreatureCount)
		{
			// We might want to spawn something so pick a cuboid at random.
			IReadOnlyCuboidData cuboid = _selectCuboid(context, completedCuboids, completedCreatures);
			
			if (null != cuboid)
			{
				ColumnHeightMap heightMap = completedHeightMaps.get(cuboid.getCuboidAddress().getColumn());
				if (null != heightMap)
				{
					_spawnInCuboid(context, existingEntities, cuboid, heightMap);
				}
				else
				{
					// Note that the height map isn't always up to date so it may not yet be generated for this cuboid.
				}
			}
			else
			{
				// Candidate too crowded.
			}
		}
		else
		{
			// Too many creatures.
		}
	}


	private static IReadOnlyCuboidData _selectCuboid(TickProcessingContext context
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<Integer, CreatureEntity> completedCreatures
	)
	{
		int index = context.randomInt.applyAsInt(completedCuboids.size());
		// We don't have a way to index this so we will just walk.
		Iterator<IReadOnlyCuboidData> iterator = completedCuboids.values().iterator();
		IReadOnlyCuboidData cuboid = iterator.next();
		for (int i = 0; i < index; ++i)
		{
			cuboid = iterator.next();
		}
		
		// Check how many entities are already in this cuboid.
		int existingInCuboid = 0;
		CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
		for (CreatureEntity existing : completedCreatures.values())
		{
			if (cuboidAddress.equals(existing.location().getBlockLocation().getCuboidAddress()))
			{
				existingInCuboid += 1;
			}
		}
		return (existingInCuboid < context.config.hostilesPerCuboidLimit)
				? cuboid
				: null
		;
	}

	private static void _spawnInCuboid(TickProcessingContext context
			, EntityCollection existingEntities
			, IReadOnlyCuboidData cuboid
			, ColumnHeightMap heightMap
	)
	{
		// Pick a random location in this cuboid.
		byte x = (byte)context.randomInt.applyAsInt(Encoding.CUBOID_EDGE_SIZE);
		byte y = (byte)context.randomInt.applyAsInt(Encoding.CUBOID_EDGE_SIZE);
		byte z = (byte)context.randomInt.applyAsInt(Encoding.CUBOID_EDGE_SIZE);
		
		// We will first skip past any blocks which are clearly above the height map.
		int highestBlockZ = heightMap.getHeight(x, y);
		// Also, ignore this cuboid if the column height map is unknown (often happens on initial load).
		if (Integer.MIN_VALUE != highestBlockZ)
		{
			// We will compute the target z in absolute coordinates, to account for the height map.
			AbsoluteLocation cuboidBase = cuboid.getCuboidAddress().getBase();
			int baseZ = cuboidBase.z();
			int thisAttemptZ = baseZ + z;
			thisAttemptZ = Math.min(thisAttemptZ, highestBlockZ + 1);
			
			// Then we need to convert it back into cuboid-local coordinates but also check it is in the cuboid (it might not be if the highest block is below this cuboid).
			int localZ = thisAttemptZ - baseZ;
			Assert.assertTrue(localZ < Encoding.CUBOID_EDGE_SIZE);
			if (localZ >= 0)
			{
				AbsoluteLocation checkSpawningLocation = cuboidBase.getRelative(x, y, localZ);
				
				_spawnInValidCuboid(context, existingEntities, baseZ, highestBlockZ, checkSpawningLocation);
			}
		}
	}


	private static void _spawnInValidCuboid(TickProcessingContext context
			, EntityCollection existingEntities
			, int baseZ
			, int highestBlockZ
			, AbsoluteLocation checkSpawningLocation
	)
	{
		// We want to slide down through this cuboid until we reach solid ground.
		Environment env = Environment.getShared();
		byte skyLightValue = PropagationHelpers.currentSkyLightValue(context.currentTick, context.config.ticksPerDay, context.config.dayStartTick);
		AbsoluteLocation goodSpawningLocation = null;
		while ((null == goodSpawningLocation) && (checkSpawningLocation.z() >= baseZ))
		{
			// We need to check not just the spawning location but the block under.
			AbsoluteLocation blockUnderLocation = checkSpawningLocation.getRelative(0, 0, -1);
			
			// Make sure that this is an air block.
			BlockProxy thisBlock = context.previousBlockLookUp.apply(checkSpawningLocation);
			boolean isAirBlock = (env.special.AIR == thisBlock.getBlock());
			if (isAirBlock)
			{
				// See if we are spawning on a solid block.
				BlockProxy base = context.previousBlockLookUp.apply(blockUnderLocation);
				boolean isBaseSolid = (null != base)
						? env.blocks.isSolid(base.getBlock())
						: false
				;
				if (isBaseSolid)
				{
					// We now need to check the lighting (using the sum of block light and sky light).
					byte blockLight = thisBlock.getLight();
					byte skyLight = (blockUnderLocation.z() == highestBlockZ)
							? skyLightValue
							: 0
					;
					byte sum = (byte)(blockLight + skyLight);
					byte totalLight = (sum > LightAspect.MAX_LIGHT)
							? LightAspect.MAX_LIGHT
							: sum
					;
					boolean isDark = ((byte)0 == totalLight);
					
					if (isDark)
					{
						// This is dark so we can spawn.
						goodSpawningLocation = checkSpawningLocation;
					}
					else
					{
						// Too dark so keep sliding down.
						checkSpawningLocation = blockUnderLocation;
					}
				}
				else
				{
					// Keep going down and see if we find a solid block somewhere else.
					checkSpawningLocation = blockUnderLocation;
				}
			}
			else
			{
				// This isn't something we can spawn in so keep shifting down.
				checkSpawningLocation = blockUnderLocation;
			}
		}
		
		// We will eventually spawn other things here but we currently only use orcs so disable this in peaceful mode once the selection was found.
		if (Difficulty.PEACEFUL == context.config.difficulty)
		{
			goodSpawningLocation = null;
		}
		
		// Check to make sure that this location isn't too close to a player.
		if (null != goodSpawningLocation)
		{
			if (existingEntities.countPlayersInRangeOfBase(goodSpawningLocation.toEntityLocation(), SPAWN_DENIAL_RANGE) > 0)
			{
				goodSpawningLocation = null;
			}
		}
		
		if (null != goodSpawningLocation)
		{
			EntityLocation location = goodSpawningLocation.toEntityLocation();
			
			// Spawn a hostile mob at random (in the future, this will need to account for size).
			int spawnIndex = context.randomInt.applyAsInt(env.creatures.HOSTILE_MOBS.length);
			EntityType spawnType = env.creatures.HOSTILE_MOBS[spawnIndex];
			EntityVolume creatureVolume = spawnType.volume();
			if (SpatialHelpers.canExistInLocation(context.previousBlockLookUp, location, creatureVolume))
			{
				// We can spawn here.
				context.creatureSpawner.spawnCreature(spawnType, location, spawnType.maxHealth());
			}
			else
			{
				// No open space.
			}
		}
		else
		{
			// No solid ground.
		}
	}
}
