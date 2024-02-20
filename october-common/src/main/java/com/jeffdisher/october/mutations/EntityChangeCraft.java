package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A crafting activity performed by this user.  Crafting converts items in the entity's inventory into different items
 * after expending some amount of time.
 */
public class EntityChangeCraft implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.CRAFT;

	public static EntityChangeCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		Craft operation = CodecHelpers.readCraft(buffer);
		return new EntityChangeCraft(operation);
	}


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
		
		// Account for any movement while we were busy.
		// NOTE:  This is currently wrong as it is only applied in the last part of the operation, not each tick.
		// This will need to be revisited when we change the crafting action.
		boolean didMove = EntityChangeMove.handleMotion(newEntity, context.previousBlockLookUp, _operation.millisPerCraft);
		
		return didCraft || didMove;
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
	}
}
