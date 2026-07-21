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
		// Just match everyone.
		return entityCollection.findClosestPlayerInViewDistance(creature, (Entity ignore) -> true);
	}

	public static boolean isHostileTargetValid(EntityCollection entityCollection, MutableCreature creature)
	{
		// We can only call this if there is a target.
		int targetId = creature.movementPlan.targetEntityId();
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
		
		// Make sure that they still exist, are in range.
		Entity player = entityCollection.getPlayerById(targetId);
		boolean isValid = false;
		if (null != player)
		{
			EntityLocation sourceEye = SpatialHelpers.getEntityEye(creature);
			EntityLocation playerBase = player.location();
			EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEye, playerBase, playerVolume);
			isValid = (distance <= creature.getType().viewDistance());
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
