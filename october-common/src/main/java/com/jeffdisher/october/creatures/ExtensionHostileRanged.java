package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.RayCastHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.subactions.EntitySubActionReleaseWeapon;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FixedRegion;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ExtensionHostileRanged implements EntityType.IExtension
{
	@Override
	public Object buildDefaultExtendedData(long gameTimeMillis)
	{
		return null;
	}

	@Override
	public Object readExtendedData(ByteBuffer buffer, long gameTimeMillis)
	{
		byte header = buffer.get();
		Assert.assertTrue((byte)0 == header);
		return null;
	}

	@Override
	public void writeExtendedData(ByteBuffer buffer, Object extendedData, long gameTimeMillis)
	{
		Assert.assertTrue(null == extendedData);
		byte header = 0;
		buffer.put(header);
	}

	@Override
	public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		return CommonExtensionHelpers.findPlayerInRange(entityCollection, creature);
	}
	@Override
	public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
	{
		return CommonExtensionHelpers.isHostileTargetValid(entityCollection, creature);
	}
	@Override
	public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context, EntityCollection entityCollection)
	{
		boolean isDone;
		// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
		// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
		if ((null != creature.movementPlan)
			&& (CreatureEntity.NO_TARGET_ENTITY_ID != creature.movementPlan.targetEntityId())
		)
		{
			// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
			// movement plans unless they are close enough for other actions).
			MinimalEntity targetEntity = context.previousEntityLookUp.getById(creature.movementPlan.targetEntityId());
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are in attack range - we will aim for the centre of the entity, since that will give us a large target.
			EntityType creatureType = creature.getType();
			EntityLocation sourceEye = SpatialHelpers.getEyeLocation(creature.getLocation(), creatureType.volume());
			FixedRegion region = FixedRegion.fromMinimal(targetEntity);
			float distance = SpatialHelpers.distanceFromLocationToRegion(sourceEye, region);
			float attackDistance = creatureType.actionDistance();
			if (distance <= attackDistance)
			{
				// We are in range so find the vector which will fire in an arc toward the centre of the target.
				EntityLocation targetCentre = region.getCentre();
				
				// Make sure that we can see them.
				Environment env = Environment.getShared();
				RayCastHelpers.RayBlock solidCollision = RayCastHelpers.findFirstCollision(sourceEye, targetCentre, (AbsoluteLocation location) -> {
					BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
					boolean shouldStop;
					if (null != proxy)
					{
						boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
						shouldStop = env.blocks.isSolid(proxy.getBlock(), isActive);
					}
					else
					{
						shouldStop = true;
					}
					return shouldStop;
				});
				
				if (null == solidCollision)
				{
					// Nothing in the way so see if there is a ballistic trajectory to satisfy this.
					EntityLocation startVector = SpatialHelpers.getBallisticVector(sourceEye, targetCentre, EntitySubActionReleaseWeapon.PROJECTILE_POWER_MULTIPLIER);
					if (null != startVector)
					{
						// Create the arrow.
						context.passiveSpawner.spawnPassive(PassiveType.PROJECTILE_ARROW, sourceEye, startVector, null);
						
						// We only count a successful attack as an "action".
						isDone = true;
					}
					else
					{
						// There is no way to hit there from here.
						isDone = false;
					}
				}
				else
				{
					// There is something in the way.
					isDone = false;
				}
			}
			else
			{
				// Too far away.
				isDone = false;
			}
		}
		else
		{
			// Nothing to do.
			isDone = false;
		}
		return isDone;
	}
	@Override
	public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
	{
		throw Assert.unreachable();
	}
	@Override
	public boolean shouldDespawn(MutableCreature creature, TickProcessingContext context)
	{
		return CommonExtensionHelpers.shouldHostileDespawn(context, creature);
	}
	@Override
	public boolean canApplyItemToCreature(MinimalEntity creature, Item itemType, long gameTimeMillis)
	{
		// We don't do direct item application to hostiles.
		return false;
	}
	@Override
	public boolean applyItemToCreature(MutableCreature creature, Item itemType, long gameTimeMillis)
	{
		// We don't do direct item application to hostiles.
		return false;
	}
}
