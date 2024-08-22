package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.OrcStateMachine;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;


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
	 * @param completedCreatures The map of all creatures from the previous tick.
	 * @return The new entity or null if spawning was aborted.
	 */
	public static CreatureEntity trySpawnCreature(TickProcessingContext context
			, EntityCollection existingEntities
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
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
				spawned = _spawnInCuboid(context, existingEntities, cuboid);
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
	)
	{
		// Pick a random location in this cuboid.
		byte x = (byte)context.randomInt.applyAsInt(32);
		byte y = (byte)context.randomInt.applyAsInt(32);
		byte z = (byte)context.randomInt.applyAsInt(32);
		
		AbsoluteLocation cuboidBase = cuboid.getCuboidAddress().getBase();
		AbsoluteLocation checkSpawningLocation = cuboidBase.getRelative(x, y, z);
		
		// We want to slide down through this cuboid until we reach solid ground.
		int baseZ = cuboidBase.z();
		Environment env = Environment.getShared();
		AbsoluteLocation goodSpawningLocation = null;
		while ((null == goodSpawningLocation) && (checkSpawningLocation.z() >= baseZ))
		{
			// See if this location is on a solid block, in the dark, and permits spawning.
			AbsoluteLocation baseLocation = checkSpawningLocation.getRelative(0, 0, -1);
			BlockProxy base = context.previousBlockLookUp.apply(baseLocation);
			boolean isSolid = (null != base)
					? env.blocks.isSolid(base.getBlock())
					: false
			;
			boolean isDark = ((byte)0 == context.previousBlockLookUp.apply(checkSpawningLocation).getLight());
			if (isSolid && isDark)
			{
				goodSpawningLocation = checkSpawningLocation;
			}
			else
			{
				checkSpawningLocation = baseLocation;
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
