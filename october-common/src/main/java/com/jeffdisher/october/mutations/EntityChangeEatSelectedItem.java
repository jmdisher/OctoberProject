package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Attempts to eat the currently selected block, incrementing food level if it is edible.
 */
public class EntityChangeEatSelectedItem implements IMutationEntity<IMutablePlayerEntity>
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
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		int selectedKey = newEntity.getSelectedKey();
		MutableInventory mutableInventory = newEntity.accessMutableInventory();
		Items selectedStack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getStackForKey(selectedKey) : null;
		Item selected = (null != selectedStack) ? selectedStack.type() : null;
		int foodValue = (null != selected)
				? env.foods.foodValue(selected)
				: 0
		;
		if (foodValue > 0)
		{
			// Eat the food, decrementing how many we have.
			int newFood = newEntity.getFood() + foodValue;
			if (newFood > 100)
			{
				newFood = 100;
			}
			newEntity.setFood((byte)newFood);
			
			// Remove the item from the inventory.
			mutableInventory.removeStackableItems(selected, 1);
			if (0 == mutableInventory.getCount(selected))
			{
				newEntity.setSelectedKey(Entity.NO_SELECTION);
			}
			didApply = true;
			
			// Do other state reset.
			newEntity.setCurrentCraftingOperation(null);
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

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
