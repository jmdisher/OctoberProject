package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Handles the "right-click on entity" case for specific items.
 * An example of this is feeding wheat to a cow.
 * Note that this is NOT the same as "hitting" an entity.
 */
public class EntityChangeUseSelectedItemOnEntity implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.USE_SELECTED_ITEM_ON_ENTITY;
	public static final long COOLDOWN_MILLIS = 250L;

	public static EntityChangeUseSelectedItemOnEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		int entityId = buffer.getInt();
		return new EntityChangeUseSelectedItemOnEntity(entityId);
	}

	/**
	 * A helper to determine if the given item can be used on a specific entity type with this entity mutation.
	 * 
	 * @param item The item.
	 * @param entityType The target entity type.
	 * @return True if this mutation can be used to apply the item to the entity.
	 */
	public static boolean canUseOnEntity(Item item, EntityType entityType)
	{
		// We just call the helper in the other class since it already has the appropriate constants for this logic.
		return CreatureLogic.canUseOnEntity(item, entityType);
	}


	private final int _entityId;

	public EntityChangeUseSelectedItemOnEntity(int entityId)
	{
		_entityId = entityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// First, we want to make sure that we are not still busy doing something else.
		boolean isReady = ((newEntity.getLastSpecialActionMillis() + COOLDOWN_MILLIS) <= context.currentTickTimeMillis);
		
		// We also want to make sure that this is in range.
		MinimalEntity target = context.previousEntityLookUp.apply(_entityId);
		float distance = (null != target)
				? SpatialHelpers.distanceFromMutableEyeToEntitySurface(newEntity, target)
				: Float.MAX_VALUE
		;
		boolean isInRange = (distance <= MiscConstants.REACH_ENTITY);
		
		// Get the current selected item.
		int selectedKey = newEntity.getSelectedKey();
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		// (we currently only handle the wheat type so just check for stackable)
		Items selectedStack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getStackForKey(selectedKey) : null;
		Item itemType = (null != selectedStack) ? selectedStack.type() : null;
		
		// See if the target entity exists and is of the correct type.
		EntityType entityType = (null != target) ? target.type() : null;
		
		boolean didApply = false;
		if (isReady && isInRange && CreatureLogic.canUseOnEntity(itemType, entityType))
		{
			// Remove the wheat item and apply it to the entity.
			// Note that we don't bother with racy conditions where we might need to pass it back since that is a rare case and of minimal impact.
			mutableInventory.removeStackableItems(itemType, 1);
			if (0 == mutableInventory.getCount(itemType))
			{
				newEntity.setSelectedKey(Entity.NO_SELECTION);
			}
			
			// Pass this to the entity.
			context.newChangeSink.creature(_entityId, new EntityChangeApplyItemToCreature(itemType));
			didApply = true;
			
			// Rate-limit us by updating the special action time.
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
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
