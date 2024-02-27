package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called by MutationEntityRequestItemPickUp to request that items be extracted from this inventory and sent back to the
 * calling entity.
 */
public class MutationBlockExtractItems implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.EXTRACT_ITEMS;

	public static MutationBlockExtractItems deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Items requested = CodecHelpers.readItems(buffer);
		int returnEntity = buffer.getInt();
		return new MutationBlockExtractItems(location, requested, returnEntity);
	}


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
		Inventory existing = newBlock.getInventory();
		if (null != existing)
		{
			// We will still try a best-efforts request if the inventory has changed.
			MutableInventory mutable = new MutableInventory(existing);
			Item requestedType = _requested.type();
			int maxAvailable = mutable.getCount(requestedType);
			int toFetch = Math.min(maxAvailable, _requested.count());
			if (toFetch > 0)
			{
				mutable.removeItems(requestedType, toFetch);
				context.newChangeSink.accept(_returnEntityId, new MutationEntityStoreToInventory(new Items(requestedType, toFetch)));
				newBlock.setInventory(mutable.freeze());
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
		CodecHelpers.writeItems(buffer, _requested);
		buffer.putInt(_returnEntityId);
	}
}
