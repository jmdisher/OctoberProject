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
import com.jeffdisher.october.utils.Assert;


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

	/**
	 * Looks at the blocks around the given location to determine what the correct "empty" block type should be put in
	 * this location.
	 * Note that this doesn't account for the current block type in the location so this shouldn't be used if that value
	 * should not be over-ridden.
	 * 
	 * @param context The context.
	 * @param location The location to investigate.
	 * @return The block type which the surrounding blocks imply the location should become.
	 */
	public static Block determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location)
	{
		return _determineEmptyBlockType(context, location);
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
		// Check to see if this block needs to change into a different type due to water, etc.
		Block thisBlock = newBlock.getBlock();
		if (thisBlock.canBeReplaced())
		{
			// This is an "empty" type so see if the "empty" blocks around it should influence its type.
			// Note that we don't change the "source" blocks.
			Block newType = (BlockAspect.getBlock(ItemRegistry.WATER_SOURCE) == thisBlock)
					? thisBlock
					: _determineEmptyBlockType(context, _blockLocation)
			;
			if (newType != thisBlock)
			{
				// We need to change this so write-back (copy over anything changing).
				Inventory inv = newBlock.getInventory();
				// These block types all support inventories.
				Assert.assertTrue(null != inv);
				newBlock.setBlockAndClear(newType);
				newBlock.setInventory(inv);
				thisBlock = newType;
				didApply = true;
			}
		}
		
		// Check to see if this has an inventory which should fall.
		if (thisBlock.permitsEntityMovement() && (newBlock.getInventory().currentEncumbrance > 0))
		{
			// This is an air block with an inventory so see what is below it.
			AbsoluteLocation belowLocation = _blockLocation.getRelative(0, 0, -1);
			BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
			// TODO:  Come up with a way to handle the case where this is null (not loaded).
			if ((null != below) && below.getBlock().permitsEntityMovement())
			{
				// Drop all the inventory items down.
				for (Items toDrop : newBlock.getInventory().items.values())
				{
					// We want to drop this into the below block.
					context.newMutationSink.accept(new MutationBlockStoreItems(belowLocation, toDrop, Inventory.INVENTORY_ASPECT_INVENTORY));
				}
				newBlock.setInventory(Inventory.start(InventoryAspect.getInventoryCapacity(thisBlock.asItem())).finish());
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


	private static Block _determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location)
	{
		// An "empty" block is one which is left over after breaking a block.
		// It is usually air but can be a water type.
		// Rules for the empty type:
		// -check 4 horizontal blocks, take water-1, unless there are >=2 blocks stronger, then use that
		// -check the block above and below, if the block below is empty, take the same as above, if not, take strong flow
		Block source = BlockAspect.getBlock(ItemRegistry.WATER_SOURCE);
		Block strong = BlockAspect.getBlock(ItemRegistry.WATER_STRONG);
		Block weak = BlockAspect.getBlock(ItemRegistry.WATER_WEAK);
		int[] types = new int[3];
		Block east = _getBlockOrNull(context, location.getRelative(1, 0, 0));
		Block west = _getBlockOrNull(context, location.getRelative(-1, 0, 0));
		Block north = _getBlockOrNull(context, location.getRelative(0, 1, 0));
		Block south = _getBlockOrNull(context, location.getRelative(0, -1, 0));
		_checkBlock(east, types, source, strong, weak);
		_checkBlock(west, types, source, strong, weak);
		_checkBlock(north, types, source, strong, weak);
		_checkBlock(south, types, source, strong, weak);
		
		int strength = 0;
		if (types[0] >= 2)
		{
			strength = 3;
		}
		else if ((1 == types[0]) || (types[1] >= 2))
		{
			strength = 2;
		}
		else if ((1 == types[1]) || (types[2] >= 2))
		{
			strength = 1;
		}
		
		Block up = _getBlockOrNull(context, location.getRelative(0, 0, 1));
		Block down = _getBlockOrNull(context, location.getRelative(0, 0, -1));
		int aboveStrength = _index(up, null, weak, strong, source);
		if (-1 == aboveStrength)
		{
			aboveStrength = 0;
		}
		if ((null != down) && down.canBeReplaced())
		{
			// Empty.
			strength = Math.max(strength, aboveStrength);
		}
		else
		{
			// Solid block so we make this strong flow if up is any water type.
			if ((aboveStrength > 0) && (strength < 2))
			{
				strength = 2;
			}
		}
		
		Block type;
		switch (strength)
		{
		case 3:
			type = source;
			break;
		case 2:
			type = strong;
			break;
		case 1:
			type = weak;
			break;
		case 0:
			type = BlockAspect.getBlock(ItemRegistry.AIR);
			break;
			default:
				throw Assert.unreachable();
		}
		return type;
	}

	private static Block _getBlockOrNull(TickProcessingContext context, AbsoluteLocation location)
	{
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		return (null != proxy)
				? proxy.getBlock()
				: null
		;
	}

	private static void _checkBlock(Block block, int[] types, Block... blocks)
	{
		int index = _index(block, blocks);
		if (index >= 0)
		{
			types[index] += 1;
		}
	}

	private static int _index(Block block, Block... blocks)
	{
		int index = -1;
		for (int i = 0; i < blocks.length; ++i)
		{
			if (block == blocks[i])
			{
				index = i;
				break;
			}
		}
		return index;
	}
}
