package com.jeffdisher.october.mutations;

import java.util.List;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.CompositeHelpers;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains common helper routines for block mutations since some of the mutations end up needing to sometimes check
 * the same things and/or inline the same logic.
 */
public class CommonBlockMutationHelpers
{
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
	 * @param outputDirection The output directly of the block in location (can be null).
	 * @param blockType The new block type to write.
	 * @param isMultiBlockExtension True if this is a multi-block extension (since they ignore block support rules).
	 * @return True if the block was written or false if the write was aborted.
	 */
	public static boolean overwriteBlock(TickProcessingContext context, IMutableBlockProxy newBlock, AbsoluteLocation location, OrientationAspect.Direction outputDirection, Block blockType, boolean isMultiBlockExtension)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// Check to see if this is the expected type.
		Block oldBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(oldBlock))
		{
			// See if the block we are changing needs a special logic mode.
			boolean shouldSetHigh = LogicLayerHelpers.shouldSetActive(env, context.previousBlockLookUp, location, outputDirection, blockType);
			
			// Make sure that this block can be supported by the one under it.
			// Note that multi-blocks only honour their support block for their root.
			boolean blockIsSupported = true;
			if (!isMultiBlockExtension)
			{
				BlockProxy belowBlock = context.previousBlockLookUp.apply(location.getRelative(0, 0, -1));
				// If the cuboid beneath this isn't loaded, we will just treat it as supported (best we can do in this situation).
				if (null != belowBlock)
				{
					blockIsSupported = env.blocks.canExistOnBlock(blockType, belowBlock.getBlock());
				}
			}
			
			// Note that failing to place this means that the block will be destroyed and nothing changes.
			if (blockIsSupported)
			{
				_setBlockCheckingFire(env, context, location, newBlock, blockType, outputDirection);
				
				if (env.plants.growthDivisor(blockType) > 0)
				{
					newBlock.requestFutureMutation(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS);
				}
				
				if (shouldSetHigh)
				{
					byte flags = FlagsAspect.set(newBlock.getFlags(), FlagsAspect.FLAG_ACTIVE);
					newBlock.setFlags(flags);
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
		// This isn't an explicit block placement, so it has no direction.
		OrientationAspect.Direction outputDirection = null;
		_setBlockCheckingFire(env, context, location, proxy, newType, outputDirection);
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
	 * 2) Drop any inventory on the ground as passives.
	 * 3) Determine the block type and drop it to the ground as a passive or send it back to the entity.
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
		
		// We will populate a MutableInventory (since it can collect like types) and then walk this union of all
		// drops to generate passives.
		// NOTE:  This approach assumes that a flowing block CANNOT also have an inventory.
		MutableInventory tempInventory = new MutableInventory(Inventory.start(Integer.MAX_VALUE).finish());
		_fillInventoryFromBlockWithoutLimit(tempInventory, proxy);
		
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
					EntityActionStoreToInventory store = new EntityActionStoreToInventory(new Items(dropped, 1), null);
					context.newChangeSink.next(optionalEntityForStorage, store);
				}
			}
			else
			{
				// Just drop this in the target location.
				_populateInventoryWhenBreakingBlock(env, context, tempInventory, block);
			}
		}
		
		// NOTE:  We use this common helper just as a consistent idiom but setting to air never starts fires.
		// This isn't an explicit block placement, so it has no direction.
		OrientationAspect.Direction outputDirection = null;
		_setBlockCheckingFire(env, context, location, proxy, emptyBlock, outputDirection);
		
		// Spawn any related passives from the dropped blocks.
		_dropTempInventoryAsPassives(context, location, tempInventory);
	}

	/**
	 * A helper to drop all the items in tempInventory as passives in the block at location.  Note that this doesn't
	 * check that the passives are allowed to exist in this space and will drop them there, either way.
	 * This help exists since it is a useful idiom to use MutableInventory to package a large collection of items to
	 * properly stack the stackable items and just use this as a temporary container.
	 * 
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location where the inventory should drop.
	 * @param tempInventory The container of the items to drop on the ground as passives.
	 */
	public static void dropTempInventoryAsPassives(TickProcessingContext context, AbsoluteLocation location, MutableInventory tempInventory)
	{
		_dropTempInventoryAsPassives(context, location, tempInventory);
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
		Environment env = Environment.getShared();
		
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
		
		if (env.specialSlot.canRemoveOrDrop(block.getBlock()))
		{
			ItemSlot oldSlot = block.getSpecialSlot();
			if (null != oldSlot)
			{
				if (null != oldSlot.stack)
				{
					inventoryToFill.addAllItems(oldSlot.stack.type(), oldSlot.stack.count());
				}
				else
				{
					inventoryToFill.addNonStackableBestEfforts(oldSlot.nonStackable);
				}
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

	private static void _setBlockCheckingFire(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block newType, OrientationAspect.Direction outputDirection)
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
		if (null != outputDirection)
		{
			proxy.setOrientation(outputDirection);
		}
		if (!env.blocks.isFlammable(oldType) && FireHelpers.canIgnite(env, context, location, proxy))
		{
			MutationBlockStartFire startFire = new MutationBlockStartFire(location);
			context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
		}
		
		// Check if there is anything changing related to ground cover.
		// First, see if this can spread ground cover.
		if (env.groundCover.isGroundCover(newType))
		{
			List<AbsoluteLocation> targets = GroundCoverHelpers.findSpreadNeighbours(env, context.previousBlockLookUp, location, newType);
			for (AbsoluteLocation neighbour : targets)
			{
				MutationBlockGrowGroundCover grow = new MutationBlockGrowGroundCover(neighbour, newType);
				context.mutationSink.future(grow, MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS);
			}
		}
		else
		{
			// Otherwise, check if this block can become ground cover.
			Block shouldBecome = GroundCoverHelpers.findPotentialGroundCoverType(env, context.previousBlockLookUp, location, newType);
			if (null != shouldBecome)
			{
				MutationBlockGrowGroundCover grow = new MutationBlockGrowGroundCover(location, shouldBecome);
				context.mutationSink.future(grow, MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS);
			}
		}
		
		// If this is the cornerstone of a composition, check the composition state and schedule a periodic update.
		if (env.blocks.isCompositionCornerstone(newType))
		{
			CompositeHelpers.processCornerstoneUpdate(env, context, location, proxy);
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

	private static void _dropTempInventoryAsPassives(TickProcessingContext context, AbsoluteLocation location, MutableInventory tempInventory)
	{
		Inventory frozen = tempInventory.freeze();
		EntityLocation passiveLocation = location.toEntityLocation();
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		for (Integer key : frozen.sortedKeys())
		{
			ItemSlot slot = frozen.getSlotForKey(key);
			context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT, passiveLocation, velocity, slot);
		}
	}
}
