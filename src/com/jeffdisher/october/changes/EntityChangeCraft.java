package com.jeffdisher.october.changes;

import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A crafting activity performed by this user.  Crafting converts items in the entity's inventory into different items
 * after expending some amount of time.
 */
public class EntityChangeCraft implements IEntityChange
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
		boolean didApply = false;
		if (_operation.checkValid.apply(newEntity.newInventory))
		{
			newEntity.newInventory = _operation.applyCraft.apply(newEntity.newInventory);
			didApply = true;
		}
		return didApply;
	}
}
