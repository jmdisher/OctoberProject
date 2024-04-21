package com.jeffdisher.october.mutations;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains common helper routines for block mutations since some of the mutations end up needing to sometimes check
 * the same things and/or inline the same logic.
 */
public class CommonBlockMutationHelpers
{
	/**
	 * Checks if the inventory stored in newBlock (which is expected to be an "empty" block type) should fall into the
	 * block below it, scheduling the appropriate mutations if required.  On return, the newBlock inventory will be
	 * empty if the function returned true (note that it MUST be non-empty before calling).
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

	/**
	 * Fills the given inventoryToFill with all items found in any inventory of block, leaving it unchanged.  Note that
	 * inventoryToFill may become over-filled as encumbrance limits are ignored in this path.
	 * 
	 * @param inventoryToFill The inventory to fill.
	 * @param block The block to read.
	 */
	public static void fillInventoryFromBlockWithoutLimit(MutableInventory inventoryToFill, IBlockProxy block)
	{
		_fillInventoryFromBlockWithoutLimit(inventoryToFill, block);
	}


	private static void _combineInventory(MutableInventory mutable, Inventory oldInventory)
	{
		if (null != oldInventory)
		{
			for (Integer key : oldInventory.sortedKeys())
			{
				Items stackable = oldInventory.getStackForKey(key);
				if (null != stackable)
				{
					mutable.addAllItems(stackable.type(), stackable.count());
				}
				else
				{
					NonStackableItem nonStackable = oldInventory.getNonStackableForKey(key);
					mutable.addNonStackableBestEfforts(nonStackable);
				}
			}
		}
	}

	private static boolean _dropInventoryIfNeeded(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		
		// Note that this should ONLY be called if the existing block is "empty".
		Assert.assertTrue(env.blocks.permitsEntityMovement(newBlock.getBlock()));
		// This should also have a non-empty inventory (otherwise, this shouldn't be called).
		Assert.assertTrue(newBlock.getInventory().currentEncumbrance > 0);
		
		// We now check if the block below this one is also "empty" and will drop the entire inventory into it via mutations.
		boolean didDropInventory = false;
		AbsoluteLocation belowLocation = location.getRelative(0, 0, -1);
		BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
		if ((null != below) && env.blocks.permitsEntityMovement(below.getBlock()))
		{
			// We want to drop this inventory into the below block.
			Inventory inventory = newBlock.getInventory();
			for (Integer key : inventory.sortedKeys())
			{
				Items stackable = inventory.getStackForKey(key);
				NonStackableItem nonStackable = inventory.getNonStackableForKey(key);
				// Precisely one of these must be non-null.
				Assert.assertTrue((null != stackable) != (null != nonStackable));
				context.mutationSink.next(new MutationBlockStoreItems(belowLocation, stackable, nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY));
			}
			
			// Now, clear the inventory.
			newBlock.setInventory(Inventory.start(env.inventory.getInventoryCapacity(newBlock.getBlock())).finish());
			didDropInventory = true;
		}
		return didDropInventory;
	}

	private static Block _determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location)
	{
		Environment env = Environment.getShared();
		// An "empty" block is one which is left over after breaking a block.
		// It is usually air but can be a water type.
		// Rules for the empty type:
		// -check 4 horizontal blocks, take water-1, unless there are >=2 blocks stronger, then use that
		// -check the block above and below, if the block below is empty, take the same as above, if not, take strong flow
		Block source = env.blocks.WATER_SOURCE;
		Block strong = env.blocks.WATER_STRONG;
		Block weak = env.blocks.WATER_WEAK;
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
		if ((null != down) && env.blocks.canBeReplaced(down))
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
			type = env.blocks.AIR;
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

	private static void _fillInventoryFromBlockWithoutLimit(MutableInventory inventoryToFill, IBlockProxy block)
	{
		Inventory oldInventory = block.getInventory();
		if (null != oldInventory)
		{
			_combineInventory(inventoryToFill, oldInventory);
		}
		FuelState oldFuel = block.getFuel();
		if (null != oldFuel)
		{
			_combineInventory(inventoryToFill, oldFuel.fuelInventory());
		}
	}
}
