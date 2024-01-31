package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A testing change to verify sequences of internal changes.
 * Given Items, will pass one to the entity in each tick until there are none left.
 */
public class EntityChangeTrickleInventory implements IMutationEntity
{
	private final Items _contents;

	public EntityChangeTrickleInventory(Items contents)
	{
		_contents = contents;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		newEntity.newInventory.addAllItems(_contents.type(), 1);
		if (_contents.count() > 1)
		{
			context.newChangeSink.accept(newEntity.original.id(), new EntityChangeTrickleInventory(new Items(_contents.type(), _contents.count() - 1)));
		}
		return true;
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
}
