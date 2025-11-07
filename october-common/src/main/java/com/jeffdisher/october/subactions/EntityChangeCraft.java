package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.CommonEntityMutationHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A crafting activity performed by this user.  Crafting converts items in the entity's inventory into different items
 * after completing the craft operation.  This CraftOperation is part of the Entity object.
 * Note that this is meant to be used incrementally - multiple calls to this mutation will eventually complete the
 * craft.
 */
public class EntityChangeCraft implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.CRAFT;

	public static EntityChangeCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		Craft operation = CodecHelpers.readCraft(buffer);
		buffer.getLong();
		return new EntityChangeCraft(operation);
	}


	private final Craft _operation;

	public EntityChangeCraft(Craft operation)
	{
		// NOTE:  In storage version 6 or network version 8, this craft operation was not allowed to be null.
		// In storage 7 and network 9, this was relaxed so that it can be null.
		_operation = operation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// See if there is an in-progress operation (replacing it or creating a new one, if none).
		CraftOperation existing = newEntity.getCurrentCraftingOperation();
		if (null != existing)
		{
			// There is something running so see if we should continue it or cancel it.
			if ((null != _operation) && (existing.selectedCraft() != _operation))
			{
				// We want to change it so drop it and try the new one.
				if (_operation.classification.equals(CraftAspect.BUILT_IN))
				{
					existing = new CraftOperation(_operation, 0L);
				}
				else
				{
					existing = null;
				}
			}
		}
		else
		{
			// There is nothing running so see if we provided a new one.
			if ((null != _operation) && _operation.classification.equals(CraftAspect.BUILT_IN))
			{
				existing = new CraftOperation(_operation, 0L);
			}
		}
		
		// Make sure that this crafting operation is valid for this inventory.
		if (null != existing)
		{
			if (!CraftAspect.canApplyMutable(existing.selectedCraft(), newEntity.accessMutableInventory()))
			{
				existing = null;
			}
		}
		
		boolean isValid;
		if (null != existing)
		{
			// Now, increment the time.
			existing = new CraftOperation(existing.selectedCraft(), existing.completedMillis() + context.millisPerTick);
			
			// See if this is completed.
			if (existing.isCompleted())
			{
				// We can now apply this and clear it.
				Environment env = Environment.getShared();
				IMutableInventory mutableInventory = newEntity.accessMutableInventory();
				
				// We just checked this above so we expect this to craft.
				boolean didCraft = CraftAspect.craft(env, existing.selectedCraft(), mutableInventory);
				Assert.assertTrue(didCraft);
				
				// Make sure that this cleared the hotbar, if we used the last of them (we need to check all of the hotbar slots).
				CommonEntityMutationHelpers.rationalizeHotbar(newEntity);
				
				// Clear the current operation since we are done.
				newEntity.setCurrentCraftingOperation(null);
				isValid = didCraft;
			}
			else
			{
				// Save back the remaining state and complete it later.
				newEntity.setCurrentCraftingOperation(existing);
				isValid = true;
			}
		}
		else
		{
			// Nothing to run so we want to clear this, even though this may be redundant.
			newEntity.setCurrentCraftingOperation(null);
			isValid = false;
		}
		
		if (isValid)
		{
			// Crafting expends energy.
			int cost = EntityActionPeriodic.ENERGY_COST_PER_TICK_INVENTORY_CRAFT;
			newEntity.applyEnergyCost(cost);
			
			// While this is an action which is considered primary, it should actually delay secondary actions, too.
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
		}
		return isValid;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeCraft(buffer, _operation);
		buffer.putLong(0L); // millis no longer stored.
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
		return "Craft " + _operation;
	}
}
