package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Selects the given item type if it exists in the entity's inventory.  Fails if it is already selected or is not in the
 * inventory.
 */
public class MutationEntitySelectItem implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.SELECT_ITEM;

	public static MutationEntitySelectItem deserializeFromBuffer(ByteBuffer buffer)
	{
		Item type = CodecHelpers.readItem(buffer);
		return new MutationEntitySelectItem(type);
	}


	private final Item _itemType;

	public MutationEntitySelectItem(Item itemType)
	{
		_itemType = itemType;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		if ((_itemType != newEntity.newSelectedItemKey) && ((null == _itemType) || (newEntity.newInventory.getCount(_itemType) > 0)))
		{
			newEntity.newSelectedItemKey = _itemType;
			didApply = true;
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
		CodecHelpers.writeItem(buffer, _itemType);
	}
}
