package com.jeffdisher.october.mutations;

import java.util.List;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
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
	public static boolean dropInventoryDownIfNeeded(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		return _dropInventoryDownIfNeeded(context, location, newBlock);
	}

	/**
	 * Looks at the blocks around the given location to determine what the correct "empty" block type should be put in
	 * this location.
	 * Note that this doesn't account for the current block type in the location so this shouldn't be used if that value
	 * should not be over-ridden.
	 * 
	 * @param context The context.
	 * @param location The location to investigate.
	 * @param currentBlock The current block contents (not read from context since it could be changing in caller).
	 * @return The block type which the surrounding blocks imply the location should become.
	 */
	public static Block determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location, Block currentBlock)
	{
		return _determineEmptyBlockType(context, location, currentBlock);
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

	/**
	 * A helper to overwrite the given newBlock with a block of blockType if it is a block type which can be replaced.
	 * 
	 * @param context The context wherein the change should be applied.
	 * @param newBlock The block being written.
	 * @param location The location of the block being written.
	 * @param blockType The new block type to write.
	 * @return True if the block was written or false if the write was aborted.
	 */
	public static boolean overwriteBlock(TickProcessingContext context, IMutableBlockProxy newBlock, AbsoluteLocation location, Block blockType)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// Check to see if this is the expected type.
		Block oldBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(oldBlock))
		{
			// See if the block we are changing needs a special logic mode.
			Block newType = LogicLayerHelpers.blockTypeToPlace(context, location, blockType);
			
			// Make sure that this block can be supported by the one under it.
			BlockProxy belowBlock = context.previousBlockLookUp.apply(location.getRelative(0, 0, -1));
			// If the cuboid beneath this isn't loaded, we will just treat it as supported (best we can do in this situation).
			boolean blockIsSupported = (null != belowBlock)
					? env.blocks.canExistOnBlock(newType, belowBlock.getBlock())
					: true
			;
			
			// Note that failing to place this means that the block will be destroyed and nothing changes.
			if (blockIsSupported)
			{
				// Do the standard inventory handling.
				Inventory inventoryToMove = _replaceBlockAndRestoreInventory(env, context, location, newBlock, newType);
				if (null != inventoryToMove)
				{
					_pushInventoryToNeighbour(env, context, location, inventoryToMove, false);
				}
				
				if (env.plants.growthDivisor(newType) > 0)
				{
					newBlock.requestFutureMutation(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS);
				}
				didApply = true;
			}
		}
		
		// Handle the case where this might be a hopper.
		if (didApply && HopperHelpers.isHopper(location, newBlock))
		{
			newBlock.requestFutureMutation(MutationBlockPeriodic.MILLIS_BETWEEN_HOPPER_CALLS);
		}
		if (didApply)
		{
			_scheduleLiquidFlowIfRequired(env, context, location, oldBlock, blockType);
		}
		return didApply;
	}

	/**
	 * Changes the block type in newBlock to be block, attempting to restore any previous inventory into the new block.
	 * The inventory is restored if the block type is non-solid (air, plants, etc) or has a standard inventory (chest,
	 * etc).
	 * Will return the inventory if it was non-empty and couldn't be restored so that the caller can decide what to do
	 * with it.
	 * 
	 * @param env The environment.
	 * @param context The context for scheduling any follow-up mutations related to fire.
	 * @param location The location of newBlock.
	 * @param newBlock The block to modify.
	 * @param block The new block type to set.
	 * @return Null if there was no inventory or if it could be restored (only non-null when there is an inventory this
	 * helper can't restore).
	 */
	public static Inventory replaceBlockAndRestoreInventory(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock, Block block)
	{
		return _replaceBlockAndRestoreInventory(env, context, location, newBlock, block);
	}

	/**
	 * Tries to find a neighbour which isn't a solid block in which to store the given inventoryToMove.  The search
	 * order is:  Up, North, South, East, West.  If none of those are loaded, non-solid blocks, the inventory is
	 * silently discarded.
	 * 
	 * @param env The environment.
	 * @param context The context for scheduling the follow-up storage mutations or looking up blocks.
	 * @param location The location where the inventorytToMove originated.
	 * @param inventoryToMove The inventory which should be pushed into a neighbouring block.
	 * @param skipAbove True if we should skip the "up" check (since some cases explicitly know this will fail.
	 */
	public static void pushInventoryToNeighbour(Environment env, TickProcessingContext context, AbsoluteLocation location, Inventory inventoryToMove, boolean skipAbove)
	{
		_pushInventoryToNeighbour(env, context, location, inventoryToMove, skipAbove);
	}

	/**
	 * Checks the blocks around a location where one is being replaced and schedules a liquid flow mutation for the
	 * future if there should be a flow into that location.
	 * 
	 * @param env The environment.
	 * @param context The context for scheduling the follow-up flow mutation or looking up blocks.
	 * @param location The location where oldType was replaced by newType.
	 * @param oldType The previous block type in this location.
	 * @param newType The updated block type in this location.
	 */
	public static void scheduleLiquidFlowIfRequired(Environment env, TickProcessingContext context, AbsoluteLocation location, Block oldType, Block newType)
	{
		_scheduleLiquidFlowIfRequired(env, context, location, oldType, newType);
	}

	/**
	 * Adds any dropped items from breaking a block of type "block" into the inventory provided by out_inventory.  Note
	 * that this does NOT include any inventory dropped by a specific container block.
	 * 
	 * @param env The environment.
	 * @param context The context for requesting random numbers.
	 * @param out_inventory The inventory to populate with any dropped items.
	 * @param block The block type being broken.
	 */
	public static void populateInventoryWhenBreakingBlock(Environment env, TickProcessingContext context, MutableInventory out_inventory, Block block)
	{
		_populateInventoryWhenBreakingBlock(env, context, out_inventory, block);
	}

	/**
	 * Returns the list of items dropped by breaking a block of type "block".  Note that this does NOT include any
	 * inventory dropped by a specific container block.
	 * 
	 * @param env The environment.
	 * @param context The context for requesting random numbers.
	 * @param block The block type being broken.
	 * @return The list of items dropped.
	 */
	public static Item[] getItemsDroppedWhenBreakingBlock(Environment env, TickProcessingContext context, Block block)
	{
		return _getItemsDroppedWhenBreakingBlock(env, context, block);
	}

	/**
	 * Sets the block in proxy, at location, to newType.  Internally, checks if fire-related mutations should be
	 * scheduled for this or a neighbouring block and schedules those mutations.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of proxy.
	 * @param proxy The block to modify.
	 * @param newType The new type to assign to proxy.
	 */
	public static void setBlockCheckingFire(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block newType)
	{
		_setBlockCheckingFire(env, context, location, proxy, newType);
	}

	/**
	 * Ignites the proxy, at location.  Internally, schedules the burn-down for this block and fire spread for any
	 * flammable neighbouring blocks.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of proxy.
	 * @param proxy The block to modify.
	 */
	public static void igniteBlockAndSpread(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		_igniteBlockAndSpread(env, context, location, proxy);
	}

	/**
	 * Handles the complex idiom of breaking a block:
	 * 1) Determine the appropriate block to put in its place (potentially scheduling liquid movement).
	 * 2) Drop any inventory on the ground.
	 * 3) Determine the block type and add it to the inventory or send it back to the entity.
	 * 4) Determine if any fires need to start or spread (never, in this case).
	 * 5) Schedule the inventory to fall into a lower block, if applicable.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of proxy.
	 * @param proxy The block to modify.
	 * @param optionalEntityForStorage If >0, the dropped block will be sent here, instead of to the ground.
	 */
	public static void breakBlockAndHandleFollowUp(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, int optionalEntityForStorage)
	{
		// We want to see if there are any liquids around this block which we will need to handle.
		Block block = proxy.getBlock();
		Block emptyBlock = env.special.AIR;
		Block eventualBlock = _determineEmptyBlockType(context, location, emptyBlock);
		if (emptyBlock != eventualBlock)
		{
			long millisDelay = env.liquids.minFlowDelayMillis(env, eventualBlock, block);
			context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
		}
		
		// Create the inventory for this type.
		MutableInventory newInventory = new MutableInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, emptyBlock));
		_fillInventoryFromBlockWithoutLimit(newInventory, proxy);
		
		// We are going to break this block so see if we should send it back to an entity.
		// (note that we drop the existing inventory on the ground, either way).
		// We don't want to drop the block if it is a multi-block extension.
		boolean isMultiBlockExtension = (env.blocks.isMultiBlock(block) && (null != proxy.getMultiBlockRoot()));
		if (!isMultiBlockExtension)
		{
			if (optionalEntityForStorage > 0)
			{
				// Schedule a mutation to send it back to them (will drop at their feet on failure).
				// This is usually just 1 element so send 1 mutation per item.
				Item[] droppedItems = _getItemsDroppedWhenBreakingBlock(env, context, block);
				for (Item dropped : droppedItems)
				{
					MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(new Items(dropped, 1), null);
					context.newChangeSink.next(optionalEntityForStorage, store);
				}
			}
			else
			{
				// Just drop this in the target location.
				_populateInventoryWhenBreakingBlock(env, context, newInventory, block);
			}
		}
		
		// Break the block and replace it with the empty type, storing the inventory into it (may be over-filled).
		// NOTE:  We use this common helper just as a consistent idiom but setting to air never starts fires.
		_setBlockCheckingFire(env, context, location, proxy, emptyBlock);
		Inventory inventory = newInventory.freeze();
		proxy.setInventory(inventory);
		
		// See if the inventory should drop from this block.
		if (inventory.currentEncumbrance > 0)
		{
			_dropInventoryDownIfNeeded(context, location, proxy);
		}
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

	private static boolean _dropInventoryDownIfNeeded(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		
		// Note that this should ONLY be called if the existing block is "empty".
		Assert.assertTrue(env.blocks.hasEmptyBlockInventory(newBlock.getBlock()));
		// This should also have a non-empty inventory (otherwise, this shouldn't be called).
		Assert.assertTrue(newBlock.getInventory().currentEncumbrance > 0);
		
		// We now check if the block below this one is also "empty" and will drop the entire inventory into it via mutations.
		boolean didDropInventory = false;
		AbsoluteLocation belowLocation = location.getRelative(0, 0, -1);
		BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
		if ((null != below) && env.blocks.hasEmptyBlockInventory(below.getBlock()))
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
			
			// Now, clear the inventory by saving back whatever the default was.
			newBlock.setInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, newBlock.getBlock()));
			didDropInventory = true;
		}
		return didDropInventory;
	}

	private static Block _determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location, Block currentBlock)
	{
		Environment env = Environment.getShared();
		Block east = _getBlockOrNull(context, location.getRelative(1, 0, 0));
		Block west = _getBlockOrNull(context, location.getRelative(-1, 0, 0));
		Block north = _getBlockOrNull(context, location.getRelative(0, 1, 0));
		Block south = _getBlockOrNull(context, location.getRelative(0, -1, 0));
		Block up = _getBlockOrNull(context, location.getRelative(0, 0, 1));
		Block down = _getBlockOrNull(context, location.getRelative(0, 0, -1));
		
		return env.liquids.chooseEmptyLiquidBlock(env, currentBlock, east, west, north, south, up, down);
	}

	private static Block _getBlockOrNull(TickProcessingContext context, AbsoluteLocation location)
	{
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		return (null != proxy)
				? proxy.getBlock()
				: null
		;
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

	private static Inventory _replaceBlockAndRestoreInventory(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock, Block block)
	{
		// Get the existing inventory (note that this will return an empty inventory if the block type can support an
		// inventory but there is nothing there).
		Inventory original = newBlock.getInventory();
		if ((null != original) && (0 == original.currentEncumbrance))
		{
			// We will ignore empty inventories.
			original = null;
		}
		_setBlockCheckingFire(env, context, location, newBlock, block);
		
		// If we have an inventory and the block type is either empty with an inventory or a station with an inventory, store there.
		if ((null != original) && (env.blocks.hasEmptyBlockInventory(block) || (0 != env.stations.getNormalInventorySize(block))))
		{
			// Note that we only want to store the inventory if this block doesn't destroy it.
			if (0 == env.blocks.getBlockDamage(block))
			{
				// We need to restore this since it is an empty block or a normal inventory.
				newBlock.setInventory(original);
			}
			// We have resolved this.
			original = null;
		}
		return original;
	}

	private static void _pushInventoryToNeighbour(Environment env, TickProcessingContext context, AbsoluteLocation location, Inventory inventoryToMove, boolean skipAbove)
	{
		// The inventory must not be empty.
		Assert.assertTrue(inventoryToMove.currentEncumbrance > 0);
		
		// We will try to drop this inventory in the first non-solid block we find in this search order:  Above, North, South, East, West.
		AbsoluteLocation[] locations = new AbsoluteLocation[] {
				location.getRelative(0, 0, 1),
				location.getRelative(0, 1, 0),
				location.getRelative(0, -1, 0),
				location.getRelative(1, 0, 0),
				location.getRelative(-1, 0, 0),
		};
		// If none of these can hold it, the inventory will be lost.  Note that we don't check for stations.
		// Since we send the inventory via a mutation, the block may have changed by the time it gets there,
		boolean didPlace = false;
		for (int i = skipAbove ? 1 : 0; !didPlace && (i < locations.length); ++i)
		{
			AbsoluteLocation target = locations[i];
			BlockProxy test = context.previousBlockLookUp.apply(target);
			if ((null != test) && env.blocks.hasEmptyBlockInventory(test.getBlock()))
			{
				for (Integer key : inventoryToMove.sortedKeys())
				{
					Items stackable = inventoryToMove.getStackForKey(key);
					NonStackableItem nonStackable = inventoryToMove.getNonStackableForKey(key);
					// Precisely one of these must be non-null.
					Assert.assertTrue((null != stackable) != (null != nonStackable));
					context.mutationSink.next(new MutationBlockStoreItems(target, stackable, nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY));
				}
				didPlace = true;
			}
		}
	}

	private static void _scheduleLiquidFlowIfRequired(Environment env, TickProcessingContext context, AbsoluteLocation location, Block oldType, Block newType)
	{
		boolean didScheduleLiquid = false;
		if (env.blocks.canBeReplaced(newType))
		{
			// We need to make sure that the eventual type is a mismatch but also that it has a flow rate (otherwise, placing a water source surrounded by air will think it should be air, meaning it should reflow immediately).
			Block eventualType = CommonBlockMutationHelpers.determineEmptyBlockType(context, location, newType);
			long millisDelay = env.liquids.minFlowDelayMillis(env, eventualType, oldType);
			if ((newType != eventualType) && (millisDelay > 0L))
			{
				context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
				didScheduleLiquid = true;
			}
		}
		// See if this block might actually need to be broken, now, due to neighbours.
		if (!didScheduleLiquid && env.blocks.isBrokenByFlowingLiquid(newType))
		{
			Block emptyBlock = env.special.AIR;
			Block eventualType = CommonBlockMutationHelpers.determineEmptyBlockType(context, location, emptyBlock);
			if (emptyBlock != eventualType)
			{
				long millisDelay = env.liquids.minFlowDelayMillis(env, eventualType, oldType);
				context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
				didScheduleLiquid = true;
			}
		}
	}

	private static Item[] _getItemsDroppedWhenBreakingBlock(Environment env, TickProcessingContext context, Block block)
	{
		int random0to99 = context.randomInt.applyAsInt(BlockAspect.RANDOM_DROP_LIMIT);
		return env.blocks.droppedBlocksOnBreak(block, random0to99);
	}

	private static void _setBlockCheckingFire(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block newType)
	{
		Block oldType = proxy.getBlock();
		
		// If this changed into a fire source block, schedule the ignition mutations around it.
		if (env.blocks.isFireSource(newType) && !env.blocks.isFireSource(oldType))
		{
			List<AbsoluteLocation> flammable = FireHelpers.findFlammableNeighbours(env, context, location);
			for (AbsoluteLocation neighour : flammable)
			{
				MutationBlockStartFire startFire = new MutationBlockStartFire(neighour);
				context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
			}
		}
		
		// If this block changed into a flammable type, see if it should receive an ignition mutation.
		// (set type first since this helper reads it).
		proxy.setBlockAndClear(newType);
		if (!env.blocks.isFlammable(oldType) && FireHelpers.canIgnite(env, context, location, proxy))
		{
			MutationBlockStartFire startFire = new MutationBlockStartFire(location);
			context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
		}
	}

	private static void _igniteBlockAndSpread(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		byte flags = proxy.getFlags();
		
		// Make sure that this isn't already on fire.
		Assert.assertTrue(!FlagsAspect.isSet(flags, FlagsAspect.FLAG_BURNING));
		// Set us on fire (in the future, this will probably be made random).
		flags = FlagsAspect.set(flags, FlagsAspect.FLAG_BURNING);
		proxy.setFlags(flags);
		
		// Schedule the mutation to finish burning.
		context.mutationSink.future(new MutationBlockBurnDown(location), MutationBlockBurnDown.BURN_DELAY_MILLIS);
		
		// See if there are any ignition blocks around this.
		List<AbsoluteLocation> flammable = FireHelpers.findFlammableNeighbours(env, context, location);
		for (AbsoluteLocation neighour : flammable)
		{
			MutationBlockStartFire startFire = new MutationBlockStartFire(neighour);
			context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
		}
	}

	private static void _populateInventoryWhenBreakingBlock(Environment env, TickProcessingContext context, MutableInventory out_inventory, Block block)
	{
		for (Item dropped : _getItemsDroppedWhenBreakingBlock(env, context, block))
		{
			out_inventory.addItemsAllowingOverflow(dropped, 1);
		}
	}
}
