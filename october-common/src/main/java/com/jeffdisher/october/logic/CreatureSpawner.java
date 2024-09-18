package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.creatures.OrcStateMachine;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;
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
	 * Attempts to spawn a single creature by randomly selecting a spawning location in the given world.
	 * 
	 * @param context The context for the current tick.
	 * @param existingEntities The existing entities in the world as of this tick.
	 * @param completedCuboids The map of all cuboids from the previous tick.
	 * @param completedHeightMaps The per-column height maps from the previous tick.
	 * @param completedCreatures The map of all creatures from the previous tick.
	 * @return The new entity or null if spawning was aborted.
	 */
	public static CreatureEntity trySpawnCreature(TickProcessingContext context
			, EntityCollection existingEntities
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
			, Map<Integer, CreatureEntity> completedCreatures
	)
	{
		int cuboidCount = completedCuboids.size();
		int estimatedTargetCreatureCount = cuboidCount * context.config.hostilesPerCuboidTarget;
		int creatureCount = completedCreatures.size();
		
		CreatureEntity spawned;
		if (creatureCount < estimatedTargetCreatureCount)
		{
			// We might want to spawn something so pick a cuboid at random.
			IReadOnlyCuboidData cuboid = _selectCuboid(context, completedCuboids, completedCreatures);
			
			if (null != cuboid)
			{
				ColumnHeightMap heightMap = completedHeightMaps.get(cuboid.getCuboidAddress().getColumn());
				if (null != heightMap)
				{
					spawned = _spawnInCuboid(context, existingEntities, cuboid, heightMap);
				}
				else
				{
					// Note that the height map isn't always up to date so it may not yet be generated for this cuboid.
					spawned = null;
				}
			}
			else
			{
				// Candidate too crowded.
				spawned = null;
			}
		}
		else
		{
			// Too many creatures.
			spawned = null;
		}
		return spawned;
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

	private static CreatureEntity _spawnInCuboid(TickProcessingContext context
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
		AbsoluteLocation cuboidBase = cuboid.getCuboidAddress().getBase();
		int baseZ = cuboidBase.z();
		int thisAttemptZ = baseZ + z;
		thisAttemptZ = Math.min(thisAttemptZ, highestBlockZ + 1);
		AbsoluteLocation checkSpawningLocation = cuboidBase.getRelative(x, y, (byte)(thisAttemptZ - baseZ));
		
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
			if (existingEntities.walkPlayersInRange(goodSpawningLocation.toEntityLocation(), SPAWN_DENIAL_RANGE, (Entity ignored) -> {}) > 0)
			{
				goodSpawningLocation = null;
			}
		}
		
		CreatureEntity spawned;
		if (null != goodSpawningLocation)
		{
			EntityLocation location = goodSpawningLocation.toEntityLocation();
			
			// For now, we only dynamically spawn orcs.
			EntityVolume creatureVolume = EntityConstants.VOLUME_ORC;
			if (SpatialHelpers.canExistInLocation(context.previousBlockLookUp, location, creatureVolume))
			{
				// We can spawn here.
				spawned = CreatureEntity.create(context.idAssigner.next(), EntityType.ORC, location, OrcStateMachine.ORC_DEFAULT_HEALTH);
			}
			else
			{
				// No open space.
				spawned = null;
			}
		}
		else
		{
			// No solid ground.
			spawned = null;
		}
		return spawned;
	}
}
