package com.jeffdisher.october.mutations;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A mutation from an entity to request that items from an inventory be moved to its inventory.
 * This is a multi-step process since both the entity and the block need to change and the request needs to start from
 * an entity:
 * -MutationEntityRequestItemPickUp - just checks for available inventory space and starts the process
 * -MutationBlockExtractItems - run against the block to extract the items
 * -MutationEntityStoreToInventory - run against the entity to store the extracted items
 */
public class MutationEntityRequestItemPickUp implements IMutationEntity
{
	private final AbsoluteLocation _blockLocation;
	private final Items _requested;

	public MutationEntityRequestItemPickUp(AbsoluteLocation blockLocation, Items requested)
	{
		_blockLocation = blockLocation;
		_requested = requested;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will currently assume that accepting items is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// We will still try a best-efforts request if the inventory has changed.
		int maxToFetch = newEntity.newInventory.maxVacancyForItem(_requested.type());
		int toFetch = Math.min(maxToFetch, _requested.count());
		
		if (toFetch > 0)
		{
			// Request that the items be extracted and sent back to us.
			context.newMutationSink.accept(new MutationBlockExtractItems(_blockLocation, new Items(_requested.type(), toFetch), newEntity.original.id()));
		}
		return (toFetch > 0);
	}
}
