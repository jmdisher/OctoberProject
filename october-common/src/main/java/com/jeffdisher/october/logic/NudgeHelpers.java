package com.jeffdisher.october.logic;

import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Helpers and constants related to the collision "nudge" behaviour.
 */
public class NudgeHelpers
{
	/**
	 * By default, we run 50 ms per tick, so this will try to nudge every second.
	 */
	public static int PLAYER_NUDGE_TICK_FREQUENCY = 20;

	/**
	 * By default, we run 50 ms per tick, so this will try to nudge every 5 seconds.
	 */
	public static int CREATURE_NUDGE_TICK_FREQUENCY = 100;

	/**
	 * Checks if this player entity is colliding with any other entity, if it is time to check, and sends nudges to them
	 * in order to push them away.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param entityCollection The collection of entities from the previous tick.
	 * @param player The player to check.
	 */
	public static void nudgeAsPlayer(Environment env
		, TickProcessingContext context
		, EntityCollection entityCollection
		, Entity player
	)
	{
		int checker = (player.id() + (int)context.currentTick) % PLAYER_NUDGE_TICK_FREQUENCY;
		if (0 == checker)
		{
			EntityLocation base = player.location();
			EntityVolume volume = env.creatures.PLAYER.volume();
			_findAndDispatch(env, context, entityCollection, player, base, volume);
		}
	}

	/**
	 * Checks if this creature is colliding with any other entity, if it is time to check, and sends nudges to them in
	 * order to push them away.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param entityCollection The collection of entities from the previous tick.
	 * @param creature The creature to check.
	 */
	public static void nudgeAsCreature(Environment env
		, TickProcessingContext context
		, EntityCollection entityCollection
		, CreatureEntity creature
	)
	{
		int checker = (Math.abs(creature.id()) + (int)context.currentTick) % CREATURE_NUDGE_TICK_FREQUENCY;
		if (0 == checker)
		{
			EntityLocation base = creature.location();
			EntityVolume volume = creature.type().volume();
			_findAndDispatch(env, context, entityCollection, creature, base, volume);
		}
	}


	private static <T> void _findAndDispatch(Environment env
		, TickProcessingContext context
		, EntityCollection entityCollection
		, T check
		, EntityLocation base
		, EntityVolume volume
	)
	{
		EntityLocation inCentre = SpatialHelpers.getCentreOfRegion(base, volume);
		float inRadius = volume.width() / 2.0f;
		entityCollection.findIntersections(env, inCentre, inRadius
			, (Entity data, EntityLocation centre, float radius) -> {
				if (check != data)
				{
					EntityActionNudge<IMutablePlayerEntity> nudge = _createNudge(inCentre, centre, inRadius);
					context.newChangeSink.next(data.id(), nudge);
				}
			}
			, (CreatureEntity data, EntityLocation centre, float radius) -> {
				if (check != data)
				{
					EntityActionNudge<IMutableCreatureEntity> nudge = _createNudge(inCentre, centre, inRadius);
					context.newChangeSink.creature(data.id(), nudge);
				}
			}
		);
	}

	private static <T extends IMutableMinimalEntity> EntityActionNudge<T> _createNudge(EntityLocation start, EntityLocation end, float radius)
	{
		// These are reversed since we are subtracting them from the r
		float dx = end.x() - start.x();
		float dy = end.y() - start.y();
		float dz = end.z() - start.z();
		float vx = Math.signum(dx) * (radius - Math.abs(dx));
		float vy = Math.signum(dy) * (radius - Math.abs(dy));
		float vz = Math.signum(dz) * (radius - Math.abs(dz));
		EntityLocation force = new EntityLocation(vx, vy, vz);
		return new EntityActionNudge<>(force);
	}
}
