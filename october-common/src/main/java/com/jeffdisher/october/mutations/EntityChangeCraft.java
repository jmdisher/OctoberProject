package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A crafting activity performed by this user.  Crafting converts items in the entity's inventory into different items
 * after completing the craft operation.  This CraftOperation is part of the Entity object.
 * Note that this is meant to be used incrementally - multiple calls to this mutation will eventually complete the
 * craft.
 */
public class EntityChangeCraft implements IMutationEntity
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
		_operation = operation;
		_millisToApply = millisToApply;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisToApply;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// See if there is an in-progress operation (replacing it or creating a new one, if none).
		CraftOperation existing = newEntity.newLocalCraftOperation;
		if ((null == existing) || (existing.selectedCraft() != _operation))
		{
			// We will start a new operation, here.
			// We need to make sure that this is a crafting operations which can be performed in their inventory.
			if (_operation.classification == Craft.Classification.TRIVIAL)
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
				boolean didCraft = existing.selectedCraft().craft(newEntity.newInventory);
				if (didCraft)
				{
					// Make sure that this cleared the selection, if we used the last of them.
					if ((null != newEntity.newSelectedItem) && (0 == newEntity.newInventory.getCount(newEntity.newSelectedItem)))
					{
						newEntity.newSelectedItem = null;
					}
				}
				newEntity.newLocalCraftOperation = null;
				isValid = didCraft;
			}
			else
			{
				// Save back the remaining state and complete it later.
				newEntity.newLocalCraftOperation = existing;
				isValid = true;
			}
		}
		else
		{
			isValid = false;
		}
		
		// Account for any movement while we were busy.
		// NOTE:  This is currently wrong as it is only applied in the last part of the operation, not each tick.
		// This will need to be revisited when we change the crafting action.
		boolean didMove = EntityChangeMove.handleMotion(newEntity, context.previousBlockLookUp, _operation.millisPerCraft);
		
		return isValid || didMove;
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
}
