package com.jeffdisher.october.mutations;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains common helper routines for block mutations since some of the mutations end up needing to sometimes check
 * the same things and/or inline the same logic.
 */
public class CommonBlockMutationHelpers
{
	/**
	 * Breaks the newBlock at location, replacing it with whatever "empty" block is appropriate, based on what is around
	 * it, and coalescing any inventories and the block, itself, into the new empty block's inventory.
	 * 
	 * @param context The context.
	 * @param location The location of newBlock.
	 * @param newBlock The block being broken.
	 */
	public static void breakBlockAndCoalesceInventory(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		_breakBlockAndCoalesceInventory(context, location, newBlock);
	}

	/**
	 * Checks if the inventory stored in newBlock (which is expected to be an "empty" block type) should fall into the
	 * block below it, scheduling the appropriate mutations if required.
	 * 
	 * @param context The context.
	 * @param location The location of newBlock.
	 * @param newBlock The block being checked and modified.
	 * @return True if the block below can accept items.
	 */
	public static boolean dropInventoryIfNeeded(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		return _dropInventoryIfNeeded(context, location, newBlock);
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


	private static void _breakBlockAndCoalesceInventory(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// The block is broken so replace it with the appropriate "empty" block and place the block in the inventory.
		Inventory oldInventory = newBlock.getInventory();
		FuelState oldFuel = newBlock.getFuel();
		Block previousBlockType = newBlock.getBlock();
		Block emptyBlock = _determineEmptyBlockType(context, location);
		newBlock.setBlockAndClear(emptyBlock);
		
		// If the inventory fits, move it over to the new block.
		// TODO:  Handle the case where the inventory can't fit when we have cases where it might be too big.
		Assert.assertTrue((null == oldInventory) || (oldInventory.currentEncumbrance <= InventoryAspect.CAPACITY_AIR));
		
		// This will create the new inventory since setting the item clears.
		Inventory newInventory = newBlock.getInventory();
		MutableInventory mutable = new MutableInventory(newInventory);
		_combineInventory(mutable, oldInventory);
		if (null != oldFuel)
		{
			_combineInventory(mutable, oldFuel.fuelInventory());
		}
		
		// We want to drop this block in the inventory, if it fits.
		for (Item dropped : BlockAspect.droppedBlocksOnBread(previousBlockType))
		{
			mutable.addItemsBestEfforts(dropped, 1);
		}
		newBlock.setInventory(mutable.freeze());
	}

	private static void _combineInventory(MutableInventory mutable, Inventory oldInventory)
	{
		if (null != oldInventory)
		{
			for (Items items : oldInventory.items.values())
			{
				mutable.addAllItems(items.type(), items.count());
			}
		}
	}

	private static boolean _dropInventoryIfNeeded(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		boolean didDropInventory = false;
		// Note that we need to handle the special-case where the block below this one is also empty and we should actually drop all the items.
		AbsoluteLocation belowLocation = location.getRelative(0, 0, -1);
		BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
		if ((null != below) && BlockAspect.permitsEntityMovement(below.getBlock()))
		{
			// We want to drop this inventory into the below block.
			for (Items items : newBlock.getInventory().items.values())
			{
				context.newMutationSink.accept(new MutationBlockStoreItems(belowLocation, items, Inventory.INVENTORY_ASPECT_INVENTORY));
			}
			newBlock.setInventory(Inventory.start(InventoryAspect.getInventoryCapacity(newBlock.getBlock())).finish());
			didDropInventory = true;
		}
		return didDropInventory;
	}

	private static Block _determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location)
	{
		// An "empty" block is one which is left over after breaking a block.
		// It is usually air but can be a water type.
		// Rules for the empty type:
		// -check 4 horizontal blocks, take water-1, unless there are >=2 blocks stronger, then use that
		// -check the block above and below, if the block below is empty, take the same as above, if not, take strong flow
		Block source = BlockAspect.WATER_SOURCE;
		Block strong = BlockAspect.WATER_STRONG;
		Block weak = BlockAspect.WATER_WEAK;
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
		if ((null != down) && BlockAspect.canBeReplaced(down))
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
			type = BlockAspect.AIR;
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
