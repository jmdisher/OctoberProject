package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionApplyItemToCreature;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FixedRegion;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Handles the "right-click on entity" case for specific items.
 * An example of this is feeding wheat to a cow.
 * Note that this is NOT the same as "hitting" an entity.
 */
public class EntitySubActionUseSelectedItemOnEntity implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.USE_SELECTED_ITEM_ON_ENTITY;
	public static final long COOLDOWN_MILLIS = 250L;

	public static EntitySubActionUseSelectedItemOnEntity deserializeFromContext(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int entityId = buffer.getInt();
		return new EntitySubActionUseSelectedItemOnEntity(entityId);
	}

	/**
	 * A helper to determine if the given item can be used on a specific entity instance with this entity mutation.
	 * 
	 * @param item The item.
	 * @param entity The target entity.
	 * @param gameTimeMillis The current game time, in milliseconds.
	 * @return True if this mutation can be used to apply the item to the entity.
	 */
	public static boolean canUseOnEntity(Item item, PartialEntity entity, long gameTimeMillis)
	{
		MinimalEntity minimal = MinimalEntity.fromPartialEntity(entity);
		EntityType creatureType = entity.type();
		return creatureType.extension().canApplyItemToCreature(minimal, item, gameTimeMillis);
	}


	private final int _entityId;

	public EntitySubActionUseSelectedItemOnEntity(int entityId)
	{
		_entityId = entityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// First, we want to make sure that we are not still busy doing something else.
		boolean isReady = ((newEntity.getLastSpecialActionMillis() + COOLDOWN_MILLIS) <= context.currentTickTimeMillis);
		
		// We also want to make sure that this is in range.
		MinimalEntity target = context.previousEntityLookUp.getById(_entityId);
		boolean isInRange;
		if (null != target)
		{
			EntityLocation sourceEyeLocation = SpatialHelpers.getEntityEye(newEntity);
			FixedRegion region = FixedRegion.fromMinimal(target);
			float distance = SpatialHelpers.distanceFromLocationToRegion(sourceEyeLocation, region);
			isInRange = (distance <= MiscConstants.REACH_ENTITY);
		}
		else
		{
			isInRange = false;
		}
		
		// Get the current selected item.
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		int selectedKey = newEntity.getSelectedKey();
		ItemSlot slot = mutableInventory.getSlotForKey(selectedKey);
		
		// (we currently only handle the wheat type so just check for stackable)
		Items selectedStack = (null != slot)
			? slot.stack
			: null
		;
		Item itemType = (null != selectedStack) ? selectedStack.type() : null;
		
		boolean didApply = false;
		if (isReady && isInRange && target.type().extension().canApplyItemToCreature(target, itemType, context.currentTickTimeMillis))
		{
			// Remove the wheat item and apply it to the entity.
			// Note that we don't bother with racy conditions where we might need to pass it back since that is a rare case and of minimal impact.
			mutableInventory.removeStackableItems(itemType, 1);
			if (0 == mutableInventory.getCount(itemType))
			{
				newEntity.setSelectedKey(Entity.NO_SELECTION);
			}
			
			// Pass this to the entity.
			context.newChangeSink.creature(_entityId, new EntityActionApplyItemToCreature(itemType));
			didApply = true;
			
			// Rate-limit us by updating the special action time.
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
			newEntity.setCurrentChargeMillis(0);
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_entityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// The target may have changed.
		return false;
	}

	@Override
	public String toString()
	{
		return "Use selected item on entity " + _entityId;
	}
}
