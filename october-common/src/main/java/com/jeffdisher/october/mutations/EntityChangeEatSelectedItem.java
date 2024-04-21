package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Attempts to eat the currently selected block, incrementing food level if it is edible.
 */
public class EntityChangeEatSelectedItem implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.EAT_ITEM;

	public static EntityChangeEatSelectedItem deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangeEatSelectedItem();
	}


	public EntityChangeEatSelectedItem()
	{
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		Items selectedStack = (newEntity.newSelectedItemKey > 0) ? newEntity.newInventory.getStackForKey(newEntity.newSelectedItemKey) : null;
		Item selected = (null != selectedStack) ? selectedStack.type() : null;
		int foodValue = (null != selected)
				? env.foods.foodValue(selected)
				: 0
		;
		if (foodValue > 0)
		{
			// Eat the food, decrementing how many we have.
			int newFood = newEntity.newFood + foodValue;
			if (newFood > 100)
			{
				newFood = 100;
			}
			newEntity.newFood = (byte)newFood;
			
			// Remove the item from the inventory.
			newEntity.newInventory.removeStackableItems(selected, 1);
			if (0 == newEntity.newInventory.getCount(selected))
			{
				newEntity.newSelectedItemKey = Entity.NO_SELECTION;
			}
			didApply = true;
			
			// Do other state reset.
			newEntity.newLocalCraftOperation = null;
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
	}
}
