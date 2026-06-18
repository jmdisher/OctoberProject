package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionImpregnateCreature;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ExtensionLivestock implements EntityType.IExtension
{
	/**
	 * The timeout from exiting love mode until it can be entered again.
	 */
	public static final long MILLIS_BREEDING_COOLDOWN = 5L * 60L * 1000L;

	private final Item _breedingItem;

	public ExtensionLivestock(Item breedingItem)
	{
		_breedingItem = breedingItem;
	}

	@Override
	public Object buildDefaultExtendedData(long gameTimeMillis)
	{
		return _buildDefault();
	}

	@Override
	public Object readExtendedData(ByteBuffer buffer, long gameTimeMillis)
	{
		// If the header is 0, this is default, otherwise we will read the fields of LivestockData.
		Object result;
		byte header = buffer.get();
		if ((byte)0 == header)
		{
			// Just use the default structure.
			result = _buildDefault();
		}
		else
		{
			Assert.assertTrue((byte)1 == header);
			
			// All this data was added in storage version 10.
			boolean inLoveMode = CodecHelpers.readBoolean(buffer);
			EntityLocation offspringLocation = CodecHelpers.readNullableEntityLocation(buffer);
			int millisRemaining = buffer.getInt();
			long breedingReadyMillis = gameTimeMillis + (long)millisRemaining;
			result = new LivestockData(inLoveMode
				, offspringLocation
				, breedingReadyMillis
			);
		}
		return result;
	}

	@Override
	public void writeExtendedData(ByteBuffer buffer, Object extendedData, long gameTimeMillis)
	{
		// We never store the null value (it is mostly just a hold-over from storage version 9) so write a 1 byte.
		byte header = 1;
		buffer.put(header);
		
		// Now, write everything else.
		LivestockData safe = (LivestockData) extendedData;
		CodecHelpers.writeBoolean(buffer, safe.inLoveMode);
		CodecHelpers.writeNullableEntityLocation(buffer, safe.offspringLocation);
		long spill = safe.breedingReadyMillis - gameTimeMillis;
		int millisRemaining = (spill > 0L)
			? (int)spill
			: 0
		;
		buffer.putInt(millisRemaining);
	}

	@Override
	public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		EntityType thisType = creature.getType();
		
		// This is livestock so choose our target based on whether we are looking for a partner or food.
		EntityType.TargetEntity newTarget;
		if (((LivestockData)creature.newExtendedData).inLoveMode())
		{
			// Find another of this type in breeding mode.
			EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
			float[] distanceToTarget = new float[] { Float.MAX_VALUE };
			EntityVolume thisVolume = thisType.volume();
			int thisCreatureId = creature.getId();
			EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), thisVolume);
			entityCollection.walkCreaturesInViewDistance(creature, (CreatureEntity check) -> {
				// Ignore ourselves and make sure that they are the same type and in love mode.
				if ((thisCreatureId != check.id()) && (thisType == check.type()))
				{
					LivestockData safe = (LivestockData)check.extendedData();
					if (safe.inLoveMode())
					{
						// See how far away they are so we choose the closest.
						EntityLocation end = check.location();
						float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, thisVolume);
						if (distance < distanceToTarget[0])
						{
							target[0] = new EntityType.TargetEntity(check.id(), end);
							distanceToTarget[0] = distance;
						}
					}
				}
			});
			newTarget = target[0];
		}
		else
		{
			// We will keep this simple:  Find the closest player holding our breeding item, up to our limit.
			EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
			float[] distanceToTarget = new float[] { Float.MAX_VALUE };
			EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), thisType.volume());
			EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
			entityCollection.walkPlayersInViewDistance(creature, (Entity player) -> {
				// See if this player has the breeding item in their hand.
				if (_breedingItem == _itemInPlayerHand(player))
				{
					// See how far away they are so we choose the closest.
					EntityLocation end = player.location();
					float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, playerVolume);
					if (distance < distanceToTarget[0])
					{
						target[0] = new EntityType.TargetEntity(player.id(), end);
						distanceToTarget[0] = distance;
					}
				}
			});
			newTarget = target[0];
		}
		return newTarget;
	}

	@Override
	public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
	{
		// We can only call this if there is a target.
		int targetId = creature.movementPlan.targetEntityId();
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
		
		// How we look at the target depends on our type and state.
		EntityType creatureType = creature.getType();
		boolean isValid = false;
		// This may be a player or a partner creature, depending on state.
		LivestockData extendedData = (LivestockData)creature.newExtendedData;
		if (extendedData.inLoveMode())
		{
			// We must be looking at a partner so make sure that they are here and still in breeding mode.
			CreatureEntity partner = entityCollection.getCreatureById(targetId);
			if (null != partner)
			{
				LivestockData safe = (LivestockData)partner.extendedData();
				isValid = safe.inLoveMode();
			}
			else
			{
				isValid = false;
			}
		}
		else
		{
			// We must be looking at a player so make sure they still have food.
			Entity player = entityCollection.getPlayerById(targetId);
			if (null != player)
			{
				EntityLocation sourceEye = SpatialHelpers.getEyeLocation(creature.getLocation(), creatureType.volume());
				EntityLocation playerBase = player.location();
				EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
				float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEye, playerBase, playerVolume);
				if (distance <= creatureType.viewDistance())
				{
					isValid = (_breedingItem == _itemInPlayerHand(player));
				}
			}
		}
		return isValid;
	}

	@Override
	public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
	{
		LivestockData changedData = _newExtendedDataAfterLivestockAction(context
			, creature.getId()
			, creature.newType
			, creature.newLocation
			, (LivestockData)creature.newExtendedData
			, (null != creature.movementPlan) ? creature.movementPlan.targetEntityId() : CreatureEntity.NO_TARGET_ENTITY_ID
		);
		boolean isDone;
		if (null != changedData)
		{
			creature.newExtendedData = changedData;
			creature.movementPlan = null;
			isDone = true;
		}
		else
		{
			isDone = false;
		}
		return isDone;
	}

	@Override
	public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
	{
		boolean didBecomePregnant = false;
		// We can only do this if already in love mode.
		LivestockData extendedData = (LivestockData) creature.newExtendedData;
		if (extendedData.inLoveMode())
		{
			// Average the locations.
			EntityLocation parentLocation = creature.getLocation();
			EntityLocation spawnLocation = new EntityLocation((sireLocation.x() + parentLocation.x()) / 2.0f
					, (sireLocation.y() + parentLocation.y()) / 2.0f
					, (sireLocation.z() + parentLocation.z()) / 2.0f
			);
			// Clear the love mode, set the spawn location, and clear existing plans.
			long breedingReadyMillis = gameTimeMillis + MILLIS_BREEDING_COOLDOWN;
			LivestockData updated = new LivestockData(
				false
				, spawnLocation
				, breedingReadyMillis
			);
			creature.newExtendedData = updated;
			creature.movementPlan = null;
			creature.newShouldTakeAction = true;
			didBecomePregnant = true;
		}
		return didBecomePregnant;
	}

	@Override
	public boolean shouldDespawn(MutableCreature creature, TickProcessingContext context)
	{
		// Livestock doesn't automatically despawn.
		return false;
	}

	@Override
	public boolean canApplyItemToCreature(MinimalEntity creature, Item itemType, long gameTimeMillis)
	{
		boolean canUse = false;
		// Currently, the only use for this mutation is to feed animals to put them into a love mode.
		if (_breedingItem == itemType)
		{
			// This is the correct item but we need to see if the entity can be put into love mode.
			LivestockData extended = (LivestockData) creature.extendedData();
			canUse = !extended.inLoveMode() && (gameTimeMillis >= extended.breedingReadyMillis());
		}
		return canUse;
	}

	@Override
	public boolean applyItemToCreature(MutableCreature creature, Item itemType, long gameTimeMillis)
	{
		boolean didApply = false;
		// The only item application case which currently exists is breeding items so make sure that is the case.
		if (_breedingItem == itemType)
		{
			// If this has a breeding item, it must be livestock.
			LivestockData safe = (LivestockData)creature.newExtendedData;
			// Don't redundantly enter love mode.
			// We can't enter love mode if already pregnant (although that would only remain the case for a single tick).
			if (!safe.inLoveMode() && (null == safe.offspringLocation()) && (safe.breedingReadyMillis() <= gameTimeMillis))
			{
				// If we applied this, put us into love mode and clear other plans.
				LivestockData updated = new LivestockData(
					true
					, null
					, 0L
				);
				creature.newExtendedData = updated;
				creature.movementPlan = null;
				creature.newShouldTakeAction = true;
				didApply = true;
			}
		}
		return didApply;
	}


	private Object _buildDefault()
	{
		return new LivestockData(false
			, null
			, 0L
		);
	}

	// Returns null if there was no livestock action taken.
	private static LivestockData _newExtendedDataAfterLivestockAction(TickProcessingContext context
		, int creatureId
		, EntityType creatureType
		, EntityLocation location
		, LivestockData extendedData
		, int targetEntityId
	)
	{
		LivestockData changedData = null;
		
		// See if we are pregnant or searching for our mate.
		if (null != extendedData.offspringLocation())
		{
			// Spawn the creature and clear our offspring location.
			Environment env = Environment.getShared();
			EntityType offspringType = env.creatures.getOffspringType(creatureType);
			context.creatureSpawner.spawnCreature(offspringType, extendedData.offspringLocation());
			LivestockData updated = new LivestockData(
				false
				, null
				, extendedData.breedingReadyMillis()
			);
			changedData = updated;
		}
		else if (extendedData.inLoveMode() && (CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId))
		{
			// We are in love mode, and have found a target, so see if we are close enough to impregnate our target.
			// We have a target so see if we are in love mode and if they are in range to breed.
			MinimalEntity targetEntity = context.previousEntityLookUp.getById(targetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are within mating distance and we are the father.
			EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(location, creatureType.volume());
			EntityLocation targetBase = targetEntity.location();
			EntityVolume targetVolume = targetEntity.type().volume();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, targetBase, targetVolume);
			float matingDistance = creatureType.actionDistance();
			if ((distance <= matingDistance) && (targetEntity.id() < creatureId))
			{
				// Send the message to impregnate them.
				EntityActionImpregnateCreature sperm = new EntityActionImpregnateCreature(location);
				context.newChangeSink.creature(targetEntityId, sperm);
				// We can also now clear our plans since we are done with them.
				// However, we exited love mode so record when we should re-enter it.
				long breedingReadyMillis = context.currentTickTimeMillis + MILLIS_BREEDING_COOLDOWN;
				LivestockData updated = new LivestockData(
					false
					, null
					, breedingReadyMillis
				);
				changedData = updated;
			}
		}
		return changedData;
	}

	private static Item _itemInPlayerHand(Entity player)
	{
		int itemKey = player.hotbarItems()[player.hotbarIndex()];
		Items itemsInHand = player.inventory().getStackForKey(itemKey);
		return (null != itemsInHand)
			? itemsInHand.type()
			: null
		;
	}


	public static record LivestockData(
		// True if this is a breedable creature which should now search for a partner.
		boolean inLoveMode
		// Non-null if this is a breedable creature who is ready to spawn offspring.
		, EntityLocation offspringLocation
		// The gameTimeMillis when breeding becomes available again (cooldown).
		, long breedingReadyMillis
	) {}
}
