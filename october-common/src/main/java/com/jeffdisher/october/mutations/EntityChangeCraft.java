package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
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
public class EntityChangeCraft implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.CRAFT;

	public static EntityChangeCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		Craft operation = CodecHelpers.readCraft(buffer);
		long millisToApply = buffer.getLong();
		return new EntityChangeCraft(operation, millisToApply);
	}


	private final Craft _operation;
	private final long _millisToApply;

	public EntityChangeCraft(Craft operation, long millisToApply)
	{
		Assert.assertTrue(null != operation);
		Assert.assertTrue(millisToApply > 0L);
		
		_operation = operation;
		_millisToApply = millisToApply;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisToApply;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// See if there is an in-progress operation (replacing it or creating a new one, if none).
		CraftOperation existing = newEntity.getCurrentCraftingOperation();
		if ((null == existing) || (existing.selectedCraft() != _operation))
		{
			// We will start a new operation, here.
			// We need to make sure that this is a crafting operations which can be performed in their inventory.
			if (_operation.classification.equals(CraftAspect.BUILT_IN))
			{
				existing = new CraftOperation(_operation, 0L);
			}
		}
		
		boolean isValid;
		if (null != existing)
		{
			// Now, increment the time.
			existing = new CraftOperation(existing.selectedCraft(), existing.completedMillis() + _millisToApply);
			
			// See if this is completed.
			if (existing.isCompleted())
			{
				// We can now apply this and clear it.
				Environment env = Environment.getShared();
				IMutableInventory mutableInventory = newEntity.accessMutableInventory();
				boolean didCraft = CraftAspect.craft(env, existing.selectedCraft(), mutableInventory);
				if (didCraft)
				{
					// Make sure that this cleared the hotbar, if we used the last of them (we need to check all of the hotbar slots).
					for (int key : newEntity.copyHotbar())
					{
						// NOTE:  This assumes that inputs are ALWAYS stackable.
						if ((Entity.NO_SELECTION != key) && (null == newEntity.accessMutableInventory().getStackForKey(key)))
						{
							// This needs to be cleared.
							newEntity.clearHotBarWithKey(key);
						}
					}
				}
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
			isValid = false;
		}
		if (isValid)
		{
			// Crafting expends energy.
			int cost = (int)(_millisToApply * EntityChangePeriodic.ENERGY_COST_CRAFT_PER_SECOND / 1000);
			EntityChangePeriodic.useEnergyAllowingDamage(context, newEntity, cost);
		}
		return isValid;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeCraft(buffer, _operation);
		buffer.putLong(_millisToApply);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
