package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Handles the "right-click with selection" case for specific items.
 * An example of this is a piece of bread being used in order to eat it.
 */
public class EntityChangeUseSelectedItemOnSelf implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.USE_SELECTED_ITEM_ON_SELF;
	public static final long COOLDOWN_MILLIS = 250L;

	public static EntityChangeUseSelectedItemOnSelf deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangeUseSelectedItemOnSelf();
	}

	/**
	 * A helper to determine if the given item can be used on its own entity with this entity mutation.
	 * 
	 * @param item The item.
	 * @return True if this mutation can be used to apply the item to its own entity.
	 */
	public static boolean canBeUsedOnSelf(Item item)
	{
		Environment env = Environment.getShared();
		// We only use this for food, at this point.
		return (env.foods.foodValue(item) > 0);
	}


	public EntityChangeUseSelectedItemOnSelf()
	{
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// First, we want to make sure that we are not still busy doing something else.
		boolean isReady = ((newEntity.getLastSpecialActionMillis() + COOLDOWN_MILLIS) <= context.currentTickTimeMillis);
		
		boolean didApply = false;
		if (isReady)
		{
			didApply = _apply(newEntity);
			
			if (didApply)
			{
				// Rate-limit us by updating the special action time.
				newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
			}
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

	@Override
	public String toString()
	{
		return "Use selected item on self";
	}


	private boolean _apply(IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		int selectedKey = newEntity.getSelectedKey();
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
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
}
