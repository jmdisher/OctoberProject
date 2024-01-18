package com.jeffdisher.october.mutations;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called by MutationEntityRequestItemPickUp to request that items be extracted from this inventory and sent back to the
 * calling entity.
 */
public class MutationBlockExtractItems implements IMutationBlock
{
	private final AbsoluteLocation _blockLocation;
	private final Items _requested;
	private final int _returnEntityId;

	public MutationBlockExtractItems(AbsoluteLocation blockLocation, Items requested, int returnEntityId)
	{
		_blockLocation = blockLocation;
		_requested = requested;
		_returnEntityId = returnEntityId;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		boolean didApply = false;
		Inventory existing = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
		if (null != existing)
		{
			// We will still try a best-efforts request if the inventory has changed.
			MutableInventory mutable = new MutableInventory(existing);
			int maxAvailable = mutable.getCount(_requested.type());
			int toFetch = Math.min(maxAvailable, _requested.count());
			if (toFetch > 0)
			{
				mutable.removeItems(_requested.type(), toFetch);
				context.newChangeSink.accept(_returnEntityId, new MutationEntityStoreToInventory(new Items(_requested.type(), toFetch)));
				newBlock.setDataSpecial(AspectRegistry.INVENTORY, mutable.freeze());
				didApply = true;
			}
		}
		return didApply;
	}
}
