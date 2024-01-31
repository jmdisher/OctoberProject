package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called by MutationEntityPushItems to store items into the inventory in a given block.
 * Any items which do not fit are destroyed.
 */
public class MutationBlockStoreItems implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.STORE_ITEMS;

	public static MutationBlockStoreItems deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Items offered = CodecHelpers.readItems(buffer);
		return new MutationBlockStoreItems(location, offered);
	}


	private final AbsoluteLocation _blockLocation;
	private final Items _offered;

	public MutationBlockStoreItems(AbsoluteLocation blockLocation, Items offered)
	{
		Assert.assertTrue(offered.count() > 0);
		
		_blockLocation = blockLocation;
		_offered = offered;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		// Get the inventory, creating it if required.
		Inventory existing = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
		MutableInventory inv = new MutableInventory((null != existing) ? existing : Inventory.start(InventoryAspect.CAPACITY_AIR).finish());
		int stored = inv.addItemsBestEfforts(_offered.type(), _offered.count());
		if (stored > 0)
		{
			newBlock.setDataSpecial(AspectRegistry.INVENTORY, inv.freeze());
		}
		return (stored > 0);
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
		CodecHelpers.writeItems(buffer, _offered);
	}
}
