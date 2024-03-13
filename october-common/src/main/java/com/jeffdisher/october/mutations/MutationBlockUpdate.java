package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are synthesized by the WorldProcessor, directly, for all the blocks adjacent to a block which changed
 * in the previous tick.
 */
public class MutationBlockUpdate implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.UPDATE;

	public static MutationBlockUpdate deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockUpdate(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockUpdate(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		// Check to see if this has an inventory which should fall.
		Block airBlock = BlockAspect.getBlock(ItemRegistry.AIR);
		if ((airBlock == newBlock.getBlock()) && (newBlock.getInventory().currentEncumbrance > 0))
		{
			// This is an air block with an inventory so see what is below it.
			AbsoluteLocation belowLocation = _blockLocation.getRelative(0, 0, -1);
			BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
			// TODO:  Come up with a way to handle the case where this is null (not loaded).
			if ((null != below) && (airBlock == below.getBlock()))
			{
				// Drop all the inventory items down.
				for (Items toDrop : newBlock.getInventory().items.values())
				{
					// We want to drop this into the below block.
					context.newMutationSink.accept(new MutationBlockStoreItems(belowLocation, toDrop, Inventory.INVENTORY_ASPECT_INVENTORY));
				}
				newBlock.setInventory(Inventory.start(InventoryAspect.getInventoryCapacity(ItemRegistry.AIR)).finish());
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
	}
}
