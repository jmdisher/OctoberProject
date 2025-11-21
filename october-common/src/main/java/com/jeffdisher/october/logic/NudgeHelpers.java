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
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers and constants related to the collision "nudge" behaviour.
 */
public class NudgeHelpers
{
	/**
	 * By default, we run 50 ms per tick, so this will try to nudge every second.
	 */
	public static final int PLAYER_NUDGE_TICK_FREQUENCY = 20;

	/**
	 * By default, we run 50 ms per tick, so this will try to nudge every 5 seconds.
	 */
	public static final int CREATURE_NUDGE_TICK_FREQUENCY = 100;

	/**
	 * Knockback from a melee attack will not be applied it if the distance between the attacker and target centres is
	 * this value or lower (made to avoid bizarre scaling with small vectors).
	 */
	public static final float MELEE_KNOCKBACK_MINIMUM_DISTANCE = 0.1f;

	/**
	 * Melee knockback is applied based on the vector between the attacker and the target, scaled to a unit vector, then
	 * multiplied by this magnitude (so the vector of the force will always be this number).
	 */
	public static final float MELEE_KNOCKBACK_MAGNITUDE = 5.0f;
	/**
	 * The knockback applied by an arrow is based on its velocity, scaled by this factor.
	 */
	public static final float ARROW_KNOCKBACK_MAGNITUDE = 0.5f;
	/**
	 * The knockback applied by collision with another entity is based on how much they overlap, scaled by this factor.
	 */
	public static final float COLLISION_KNOCKBACK_MAGNITUDE = 5.0f;

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

	/**
	 * Determines the melee knockback force to apply based on the source and target, as well as their volumes.  If there
	 * should be a nudge knockback, it is sent to the target for the next tick.
	 * 
	 * @param context The current tick context.
	 * @param targetId The ID of the player/creature being targeted by the nudge.
	 * @param source The source of the melee attack.
	 * @param sourceVolume The volume of the source.
	 * @param target The target of the melee attack.
	 * @param targetVolume The volume of the target.
	 */
	public static void nudgeFromMelee(TickProcessingContext context
		, int targetId
		, EntityLocation source
		, EntityVolume sourceVolume
		, EntityLocation target
		, EntityVolume targetVolume
	)
	{
		EntityLocation sourceCentre = SpatialHelpers.getCentreOfRegion(source, sourceVolume);
		EntityLocation targetCentre = SpatialHelpers.getCentreOfRegion(target, targetVolume);
		EntityLocation delta = new EntityLocation(targetCentre.x() - sourceCentre.x()
			, targetCentre.y() - sourceCentre.y()
			, targetCentre.z() - sourceCentre.z()
		);
		float magnitude = delta.getMagnitude();
		if (magnitude > MELEE_KNOCKBACK_MINIMUM_DISTANCE)
		{
			EntityLocation knockbackVector = delta.makeScaledInstance(MELEE_KNOCKBACK_MAGNITUDE / magnitude);
			if (targetId > 0)
			{
				EntityActionNudge<IMutablePlayerEntity> knockback = new EntityActionNudge<>(knockbackVector);
				context.newChangeSink.next(targetId, knockback);
			}
			else
			{
				Assert.assertTrue(targetId < 0);
				EntityActionNudge<IMutableCreatureEntity> knockback = new EntityActionNudge<>(knockbackVector);
				context.newChangeSink.creature(targetId, knockback);
			}
		}
	}

	/**
	 * Determines the arrow knockback force to apply based on the arrow velocity.  The nudge knockback is sent to the
	 * target for the next tick.
	 * 
	 * @param context The current tick context.
	 * @param targetId The ID of the player/creature being targeted by the nudge.
	 * @param arrowVelocity The velocity of the arrow at the time of impact.
	 */
	public static void nudgeFromArrow(TickProcessingContext context
		, int targetId
		, EntityLocation arrowVelocity
	)
	{
		EntityLocation knockbackPower = arrowVelocity.makeScaledInstance(ARROW_KNOCKBACK_MAGNITUDE);
		if (targetId > 0)
		{
			EntityActionNudge<IMutablePlayerEntity> knockback = new EntityActionNudge<>(knockbackPower);
			context.newChangeSink.next(targetId, knockback);
		}
		else
		{
			Assert.assertTrue(targetId < 0);
			EntityActionNudge<IMutableCreatureEntity> knockback = new EntityActionNudge<>(knockbackPower);
			context.newChangeSink.creature(targetId, knockback);
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

	private static <T extends IMutableMinimalEntity> EntityActionNudge<T> _createNudge(EntityLocation start, EntityLocation end, float sourceRadius)
	{
		// These are reversed since we are subtracting them from the sourceRadius.
		float dx = end.x() - start.x();
		float dy = end.y() - start.y();
		float dz = end.z() - start.z();
		// The sourceRadius is the effective radius of the source located at "start" so we want to push harder, the closer we are, but to a minimum of 0.0f (since we may be far away in some axes).
		float magX = Math.max(sourceRadius - Math.abs(dx), 0.0f);
		float magY = Math.max(sourceRadius - Math.abs(dy), 0.0f);
		float magZ = Math.max(sourceRadius - Math.abs(dz), 0.0f);
		float vx = Math.signum(dx) * magX * COLLISION_KNOCKBACK_MAGNITUDE;
		float vy = Math.signum(dy) * magY * COLLISION_KNOCKBACK_MAGNITUDE;
		float vz = Math.signum(dz) * magZ * COLLISION_KNOCKBACK_MAGNITUDE;
		EntityLocation force = new EntityLocation(vx, vy, vz);
		return new EntityActionNudge<>(force);
	}
}
