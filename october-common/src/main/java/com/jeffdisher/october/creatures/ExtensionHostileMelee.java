package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ExtensionHostileMelee implements EntityType.IExtension
{
	/**
	 * A creature will wait one second between attacks.
	 */
	public static final long MILLIS_ATTACK_COOLDOWN = 1000L;

	private final byte _attackDamage;

	public ExtensionHostileMelee(byte attackDamage)
	{
		_attackDamage = attackDamage;
	}

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
			&& (creature.nextAttackMillis <= context.currentTickTimeMillis)
		)
		{
			// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
			// movement plans unless they are close enough for other actions).
			MinimalEntity targetEntity = context.previousEntityLookUp.getById(creature.movementPlan.targetEntityId());
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are in attack range.
			EntityType creatureType = creature.getType();
			EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), creatureType.volume());
			EntityLocation targetBase = targetEntity.location();
			EntityVolume targetVolume = targetEntity.type().volume();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, targetBase, targetVolume);
			float attackDistance = creatureType.actionDistance();
			if (distance <= attackDistance)
			{
				// We can attack them so choose the target.
				int index = context.randomInt.applyAsInt(BodyPart.values().length);
				BodyPart target = BodyPart.values()[index];
				EntityActionTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityActionTakeDamageFromEntity<>(target, _attackDamage, creature.getId());
				context.newChangeSink.next(creature.movementPlan.targetEntityId(), takeDamage);
				NudgeHelpers.nudgeFromMelee(context
					, creature.movementPlan.targetEntityId()
					, creature.newLocation
					, creature.getType().volume()
					, targetBase
					, targetVolume
				);
				
				// Since we sent the attack, put us on attack cooldown.
				creature.nextAttackMillis = context.currentTickTimeMillis + MILLIS_ATTACK_COOLDOWN;
				// We only count a successful attack as an "action".
				isDone = true;
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
