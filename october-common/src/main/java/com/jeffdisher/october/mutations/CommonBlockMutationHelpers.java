package com.jeffdisher.october.mutations;

import java.util.List;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourHopper;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourPlant;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.logic.MiscHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IBlockProxy;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;


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
	 * Drops all the given inventories (normal, fuel, but also special slot) of the given block as passives at the given
	 * location.
	 * 
	 * @param context The context.
	 * @param location The location for passives.
	 * @param block The block to read.
	 */
	public static void dropBlockInventoriesAsPassives(TickProcessingContext context, AbsoluteLocation location, IBlockProxy block)
	{
		_dropBlockInventoriesAsPassives(context, location, block);
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
	public static boolean overwriteBlock(TickProcessingContext context, IMutableBlockProxy newBlock, AbsoluteLocation location, FacingDirection outputDirection, Block blockType, boolean isMultiBlockExtension)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// Check to see if this is the expected type.
		Block oldBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(oldBlock))
		{
			// See if the block we are changing needs a special logic mode.
			boolean shouldSetHigh = LogicLayerHelpers.shouldSetActive(env, context.previousBlockLookUp, location, outputDirection, blockType);
			BlockProxy belowBlock = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
			
			// Make sure that this block can be supported by the one under it.
			// Note that multi-blocks only honour their support block for their root.
			boolean blockIsSupported = true;
			if (!isMultiBlockExtension)
			{
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
					newBlock.requestFutureMutation(PeriodicBehaviourPlant.MILLIS_BETWEEN_GROWTH_CALLS);
				}
				
				if (shouldSetHigh)
				{
					byte oldFlags = newBlock.getFlags();
					if (!FlagsAspect.isSet(oldFlags, FlagsAspect.FLAG_ACTIVE))
					{
						byte newFlags = FlagsAspect.set(oldFlags, FlagsAspect.FLAG_ACTIVE);
						newBlock.setFlags(newFlags);
						
						LogicAspect.IActiveFlagChangeCallback changeState = env.logic.flagChangeHandler(newBlock.getBlock());
						if (null != changeState)
						{
							changeState.activeFlagDidChange(context, newBlock, location, shouldSetHigh);
						}
					}
				}
				
				// Gravity blocks are placed once and then fall after an update, so see if that matters here.
				if (env.blocks.hasGravity(blockType))
				{
					// If we think that this should fall, schedule the apply gravity mutation.
					if (null != belowBlock)
					{
						if (!env.blocks.isSupportedAgainstGravity(blockType, belowBlock.getBlock()))
						{
							context.mutationSink.next(new MutationBlockApplyGravity(location));
						}
					}
				}
				
				didApply = true;
			}
		}
		
		// Handle the case where this might be a hopper.
		if (didApply && HopperHelpers.isHopper(location, newBlock))
		{
			newBlock.requestFutureMutation(PeriodicBehaviourHopper.MILLIS_BETWEEN_HOPPER_CALLS);
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
	 * Drops all the normal item blocks of the given block type as passives at the given location.  Note that this does
	 * NOT include any inventory dropped by a specific container block.
	 * 
	 * @param env The environment.
	 * @param context The context for requesting random numbers.
	 * @param location The location for passives.
	 * @param block The block type being broken.
	 */
	public static void dropAsPassivesWhenBreakingBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, Block block)
	{
		_dropAsPassivesWhenBreakingBlock(env, context, location, block);
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
		FacingDirection outputDirection = null;
		_setBlockCheckingFire(env, context, location, proxy, newType, outputDirection);
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
			long millisDelay = env.liquids.minFlowDelayMillis(eventualBlock, block);
			context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
		}
		
		_dropBlockInventoriesAsPassives(context, location, proxy);
		
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
				ItemSlot[] droppedItems = _getItemsDroppedWhenBreakingBlock(env, context, block);
				for (ItemSlot dropped : droppedItems)
				{
					EntityActionStoreToInventory store = new EntityActionStoreToInventory(dropped.stack, dropped.nonStackable);
					context.newChangeSink.next(optionalEntityForStorage, store);
				}
			}
			else
			{
				// Just drop this in the target location.
				_dropAsPassivesWhenBreakingBlock(env, context, location, block);
			}
		}
		
		// NOTE:  We use this common helper just as a consistent idiom but setting to air never starts fires.
		// This isn't an explicit block placement, so it has no direction.
		FacingDirection outputDirection = null;
		_setBlockCheckingFire(env, context, location, proxy, emptyBlock, outputDirection);
	}


	private static void _dropInventoryAsPassives(TickProcessingContext context, AbsoluteLocation location, Inventory oldInventory)
	{
		if (null != oldInventory)
		{
			for (Integer key : oldInventory.sortedKeys())
			{
				ItemSlot slot = oldInventory.getSlotForKey(key);
				_dropAsPassive(context, location, slot);
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
		BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
		return (null != proxy)
				? proxy.getBlock()
				: null
		;
	}

	private static void _dropBlockInventoriesAsPassives(TickProcessingContext context, AbsoluteLocation location, IBlockProxy block)
	{
		Inventory oldInventory = block.getInventory();
		if (null != oldInventory)
		{
			_dropInventoryAsPassives(context, location, oldInventory);
		}
		
		FuelState oldFuel = block.getFuel();
		if (null != oldFuel)
		{
			_dropInventoryAsPassives(context, location, oldFuel.fuelInventory());
		}
		
		ItemSlot oldSlot = block.getSpecialSlot();
		if (null != oldSlot)
		{
			_dropAsPassive(context, location, oldSlot);
		}
	}

	private static void _scheduleLiquidFlowIfRequired(Environment env, TickProcessingContext context, AbsoluteLocation location, Block oldType, Block newType)
	{
		boolean didScheduleLiquid = false;
		if (env.blocks.canBeReplaced(newType))
		{
			// We need to make sure that the eventual type is a mismatch but also that it has a flow rate (otherwise, placing a water source surrounded by air will think it should be air, meaning it should reflow immediately).
			Block eventualType = CommonBlockMutationHelpers.determineEmptyBlockType(context, location, newType);
			long millisDelay = env.liquids.minFlowDelayMillis(eventualType, oldType);
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
				long millisDelay = env.liquids.minFlowDelayMillis(eventualType, oldType);
				context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
				didScheduleLiquid = true;
			}
		}
	}

	private static ItemSlot[] _getItemsDroppedWhenBreakingBlock(Environment env, TickProcessingContext context, Block block)
	{
		DropChance[] chances = env.blocks.possibleDropsOnBreak(block);
		
		// Note that the client needs to only assume 100% drop change will work.
		int random0to99 = (null != context.randomInt)
			? context.randomInt.applyAsInt(MiscHelpers.RANDOM_DROP_LIMIT)
			: 99
		;
		ItemSlot[] slots = MiscHelpers.convertToDrops(env, random0to99, chances);
		return slots;
	}

	private static void _setBlockCheckingFire(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block newType, FacingDirection outputDirection)
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
		if (env.composites.isActiveCornerstone(newType))
		{
			env.composites.processCornerstoneUpdate(env, context, location, proxy);
		}
	}

	private static void _dropAsPassivesWhenBreakingBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, Block block)
	{
		for (ItemSlot dropped : _getItemsDroppedWhenBreakingBlock(env, context, block))
		{
			_dropAsPassive(context, location, dropped);
		}
	}

	private static void _dropAsPassive(TickProcessingContext context, AbsoluteLocation location, ItemSlot slot)
	{
		EntityLocation passiveLocation = location.toEntityLocation();
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT, passiveLocation, velocity, slot);
	}
}
