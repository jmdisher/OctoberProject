package com.jeffdisher.october.mutations;

import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A crafting activity performed by this user.  Crafting converts items in the entity's inventory into different items
 * after expending some amount of time.
 */
public class EntityChangeCraft implements IMutationEntity
{
	private final Craft _operation;

	public EntityChangeCraft(Craft operation)
	{
		_operation = operation;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _operation.millisPerCraft;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didCraft = _operation.craft(newEntity.newInventory);
		if (didCraft)
		{
			// Make sure that this cleared the selection, if we used the last of them.
			if ((null != newEntity.newSelectedItem) && (0 == newEntity.newInventory.getCount(newEntity.newSelectedItem)))
			{
				newEntity.newSelectedItem = null;
			}
		}
		return didCraft;
	}
}
