package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
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
	public static final MutationEntityType TYPE = MutationEntityType.ITEMS_REQUEST_PULL;

	public static MutationEntityRequestItemPickUp deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		Items requested = CodecHelpers.readItems(buffer);
		byte inventoryAspect = buffer.get();
		return new MutationEntityRequestItemPickUp(blockLocation, requested, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final Items _requested;
	private final byte _inventoryAspect;

	public MutationEntityRequestItemPickUp(AbsoluteLocation blockLocation, Items requested, byte inventoryAspect)
	{
		_blockLocation = blockLocation;
		_requested = requested;
		_inventoryAspect = inventoryAspect;
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
		
		// See what this is, in the block's inventory, to make sure that we aren't trying to over-fetch.
		BlockProxy target = context.previousBlockLookUp.apply(_blockLocation);
		Inventory inv = target.getInventory();
		int maxAvailable = inv.getCount(_requested.type());
		toFetch = Math.min(toFetch, maxAvailable);
		
		if (toFetch > 0)
		{
			// Request that the items be extracted and sent back to us.
			context.mutationSink.next(new MutationBlockExtractItems(_blockLocation, new Items(_requested.type(), toFetch), _inventoryAspect, newEntity.original.id()));
		}
		return (toFetch > 0);
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
		CodecHelpers.writeItems(buffer, _requested);
		buffer.put(_inventoryAspect);
	}
}
