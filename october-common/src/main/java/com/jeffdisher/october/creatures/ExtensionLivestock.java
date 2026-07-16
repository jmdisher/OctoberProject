package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
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
	private final CommonBreedingLogic _breeding;
	private final Item _breedingItem;

	public ExtensionLivestock(Item breedingItem)
	{
		_breeding = new CommonBreedingLogic((Object embeddedData) -> {
			LivestockData data = (LivestockData)embeddedData;
			return data.breeding;
		});
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
			CommonBreedingLogic.Data breeding = _breeding.readData(buffer, gameTimeMillis);
			result = new LivestockData(breeding
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
		_breeding.writeData(buffer, safe.breeding, gameTimeMillis);
	}

	@Override
	public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		// This is livestock so choose our target based on whether we are looking for a partner or food.
		EntityType.TargetEntity newTarget;
		if (_breeding.isMutableInLoveMode(creature))
		{
			newTarget = _breeding.findBreedingPartner(creature, entityCollection);
		}
		else
		{
			// We will keep this simple:  Find the closest player holding our breeding item, up to our limit.
			EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
			float[] distanceToTarget = new float[] { Float.MAX_VALUE };
			EntityType thisType = creature.getType();
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
		if (_breeding.isMutableInLoveMode(creature))
		{
			// We must be looking at a partner so make sure that they are here and still in breeding mode.
			CreatureEntity partner = entityCollection.getCreatureById(targetId);
			if (null != partner)
			{
				isValid = _breeding.isCreatureInLoveMode(partner);
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
	public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context, EntityCollection entityCollection)
	{
		LivestockData changedData = _newExtendedDataAfterLivestockAction(context
			, creature
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
		CommonBreedingLogic.Data dataIfChanged = _breeding.setCreaturePregnant(creature, sireLocation, gameTimeMillis);
		if (null != dataIfChanged)
		{
			creature.newExtendedData = new LivestockData(dataIfChanged);
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
			canUse = _breeding.canEnterLoveMode(creature, gameTimeMillis);
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
			CommonBreedingLogic.Data dataIfChanged = _breeding.enterLoveMode(creature, gameTimeMillis);
			if (null != dataIfChanged)
			{
				creature.newExtendedData = new LivestockData(dataIfChanged);
				didApply = true;
			}
		}
		return didApply;
	}


	private Object _buildDefault()
	{
		return new LivestockData(_breeding.buildDefault());
	}

	// Returns null if there was no livestock action taken.
	private LivestockData _newExtendedDataAfterLivestockAction(TickProcessingContext context
		, MutableCreature creature
	)
	{
		CommonBreedingLogic.Data ifChanged = _breeding.spawnOffspring(context, creature);
		
		if (null == ifChanged)
		{
			ifChanged = _breeding.impregnateTarget(context, creature);
		}
		
		LivestockData changedData = null;
		if (null != ifChanged)
		{
			changedData = new LivestockData(ifChanged
			);
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
		CommonBreedingLogic.Data breeding
	) {}
}
