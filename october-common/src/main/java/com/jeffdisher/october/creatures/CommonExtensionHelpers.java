package com.jeffdisher.october.creatures;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This class contains any of the common logic and constants used by the other extension implementations.
 */
public class CommonExtensionHelpers
{
	public static EntityType.TargetEntity findPlayerInRange(EntityCollection entityCollection, IMutableMinimalEntity creature)
	{
		EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), creature.getType().volume());
		EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
		entityCollection.walkPlayersInViewDistance(creature, (Entity player) -> {
			// We are looking for any player so just choose the closest.
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, playerVolume);
			if (distance < distanceToTarget[0])
			{
				target[0] = new EntityType.TargetEntity(player.id(), end);
				distanceToTarget[0] = distance;
			}
		});
		return target[0];
	}

	public static boolean isHostileTargetValid(EntityCollection entityCollection, MutableCreature creature)
	{
		// We can only call this if there is a target.
		int targetId = creature.movementPlan.targetEntityId();
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
		
		// How we look at the target depends on our type and state.
		EntityType creatureType = creature.getType();
		boolean isValid = false;
		
		// Make sure that they still exist, are in range.
		Entity player = entityCollection.getPlayerById(targetId);
		if (null != player)
		{
			EntityLocation sourceEye = SpatialHelpers.getEyeLocation(creature.newLocation, creatureType.volume());
			EntityLocation playerBase = player.location();
			EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEye, playerBase, playerVolume);
			isValid = (distance <= creatureType.viewDistance());
		}
		return isValid;
	}

	public static boolean shouldHostileDespawn(TickProcessingContext context, MutableCreature mutable)
	{
		boolean shouldDespawn;
		if (Difficulty.PEACEFUL == context.config.difficulty)
		{
			// If we are peaceful, we want to despawn any creatures which are hostile.
			shouldDespawn = true;
		}
		else
		{
			// See if this should despawn due to a timeout.
			shouldDespawn = (mutable.despawnMillis <= context.currentTickTimeMillis);
		}
		return shouldDespawn;
	}
}
