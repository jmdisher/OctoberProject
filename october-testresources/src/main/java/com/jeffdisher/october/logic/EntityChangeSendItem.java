package com.jeffdisher.october.logic;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A test of IEntityChange:  This change extracts all items of a given type from the entity's inventory and, if it is
 * more than 0, creates a new change to pass it to another entity.
 */
public class EntityChangeSendItem implements IMutationEntity<IMutablePlayerEntity>
{
	private final int _targetId;
	private final Item _itemType;

	public EntityChangeSendItem(int targetId, Item itemType)
	{
		_targetId = targetId;
		_itemType = itemType;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		int foundCount = _common(newEntity, context.newChangeSink);
		return foundCount > 0;
	}


	private int _common(IMutablePlayerEntity newEntity, TickProcessingContext.IChangeSink newChangeSink)
	{
		// Extract all items of this type from the entity, failing the mutation if there aren't any.
		IMutableInventory inventory = newEntity.accessMutableInventory();
		int foundCount = inventory.getCount(_itemType);
		
		if (foundCount > 0)
		{
			// Update the inventory.
			inventory.removeStackableItems(_itemType, foundCount);
			// If we had this selected, clear it.
			if (inventory.getIdOfStackableType(_itemType) == newEntity.getSelectedKey())
			{
				newEntity.setSelectedKey(Entity.NO_SELECTION);
			}
			// Send this to the other entity.
			newChangeSink.next(_targetId, new EntityChangeReceiveItem(_itemType, foundCount));
		}
		return foundCount;
	}

	@Override
	public MutationEntityType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
