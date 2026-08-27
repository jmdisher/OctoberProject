package com.jeffdisher.october.mutations;

import java.util.List;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LiquidRegistry;
import com.jeffdisher.october.aspects.LiquidRegistry.LiquidBlock;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.GroundCoverHelpers;
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
import com.jeffdisher.october.types.Pair;
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
	public static Pair<Block, LiquidRegistry.LiquidBlock> determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location, LiquidRegistry.LiquidBlock currentBlock)
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
	 * Internally runs any follow-up processing logic, as well.
	 * 
	 * @param context The context wherein the change should be applied.
	 * @param proxy The block being written.
	 * @param location The location of the block being written.
	 * @param blockType The new block type to write.
	 * @param outputDirection The output directly of the block in location (can be null).
	 * @param blockDefined The block-defined byte to write (0 if not relevant).
	 * @param isMultiBlockExtension True if this is a multi-block extension (since they ignore block support rules).
	 * @return True if the block was written or false if the write was aborted.
	 */
	public static boolean overwriteBlockIfReplaceableWithFollowUps(TickProcessingContext context
		, IMutableBlockProxy proxy
		, AbsoluteLocation location
		, Block blockType
		, FacingDirection outputDirection
		, byte blockDefined
		, boolean isMultiBlockExtension
	)
	{
		Environment env = Environment.getShared();
		
		// Check to see if this is the expected type.
		boolean shouldSet = false;
		Block oldBlock = proxy.getBlock();
		if (env.blocks.canBeReplaced(oldBlock))
		{
			// Find the block this one is supported by (usually below, might not be loaded).
			AbsoluteLocation supportLocation = location.getRelative(0, 0, -1);
			if ((null != outputDirection) && env.orientations.doesSingleBlockRequireOrientation(blockType))
			{
				supportLocation = outputDirection.getOutputBlockLocation(location);
			}
			BlockProxy supportBlock = context.previousBlockLookUp.readBlock(supportLocation);
			
			// Make sure that this block can be supported by the one under it.
			// Note that multi-blocks only honour their support block for their root.
			boolean blockIsSupported = true;
			if (!isMultiBlockExtension)
			{
				// If the cuboid supporting this isn't loaded, we will just treat it as supported (best we can do in this situation).
				if (null != supportBlock)
				{
					blockIsSupported = env.blocks.canExistOnBlock(blockType, supportBlock.getBlock());
				}
			}
			
			// Note that failing to place this means that the block will be destroyed and nothing changes.
			shouldSet = blockIsSupported;
		}
		
		boolean didApply = false;
		if (shouldSet)
		{
			_setBlockWithFollowUps(env, context, location, proxy, blockType, outputDirection, blockDefined);
			
			didApply = true;
		}
		return didApply;
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
	 * Sets the block in proxy, at location, to newType.  Internally runs any follow-up processing logic, as well.
	 * Note that this can only be used to set a block which is not replaceable (otherwise, use empty or liquid helpers).
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of proxy.
	 * @param proxy The block to modify.
	 * @param newType The new type to assign to proxy.
	 */
	public static void setBlockWithFollowUps(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, IMutableBlockProxy proxy
		, Block newType
	)
	{
		// This case is only expected to be used for non-replaceable blocks.
		Assert.assertTrue(!env.blocks.canBeReplaced(newType));
		
		// This isn't an explicit block placement, so it has no direction.
		FacingDirection outputDirection = null;
		byte blockDefined = 0;
		_setBlockWithFollowUps(env, context, location, proxy, newType, outputDirection, blockDefined);
	}

	/**
	 * Clears the block in proxy to be an air block.  Internally runs any follow-up liquid flow-in scheduling, as well.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of proxy.
	 * @param proxy The block to modify.
	 */
	public static void setEmptyBlock(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, IMutableBlockProxy proxy
	)
	{
		// When we set an empty block, we only need to check if a liquid can flow into the space.
		Block oldType = proxy.getBlock();
		Block newType = env.special.AIR;
		proxy.setBlockAndClear(newType);
		env.hooks.didSetBlock(env, context, location, proxy, oldType);
		_didScheduleFlowInForReplaceable(env, context, location, oldType, proxy);
	}

	/**
	 * Sets the block in proxy to be the given newType liquid block.  Internally runs any follow-up fire scheduling, as
	 * well.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of proxy.
	 * @param proxy The block to modify.
	 * @param newType The new liquid value to set.
	 */
	public static void setLiquidWithFollowUps(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, IMutableBlockProxy proxy
		, LiquidRegistry.LiquidBlock newType
	)
	{
		Block oldType = proxy.getBlock();
		
		// Set the block first since it clears the block-defined byte.
		proxy.setBlockAndClear(newType.sourceType());
		proxy.setBlockDefinedByte(newType.distance());
		env.hooks.didSetBlock(env, context, location, proxy, oldType);
		
		// We will do the fire check with the source.
		Block source = newType.sourceType();
		_scheduleFireIfNewSource(env, context, location, oldType, source);
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
		byte blockDefined = 0;
		_setBlockWithFollowUps(env, context, location, proxy, emptyBlock, outputDirection, blockDefined);
	}

	/**
	 * Checks to see if a replaceable block is adjacent to liquids which should flow into it and interact.  Returns true
	 * if the follow-up mutation to accomplish this was scheduled in the given context.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of the check.
	 * @param proxy The proxy for the block to check.
	 * @return True if a MutationBlockLiquidFlowInto was scheduled for this block.
	 */
	public static boolean didScheduleFlowInForReplaceable(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, IBlockProxy proxy
	)
	{
		// We expect that this is only called when the block can be replaced.
		Block blockType = proxy.getBlock();
		Assert.assertTrue(env.blocks.canBeReplaced(blockType));
		
		// This case is used when not changing the type so we use the same for new and old (only used to choose a delay).
		return _didScheduleFlowInForReplaceable(env, context, location, blockType, proxy);
	}

	/**
	 * Checks to see if a block which can be broken by liquids is adjacent to liquids which should flow into it and
	 * interact.  Returns true if the follow-up mutation to accomplish this was scheduled in the given context.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of the check.
	 * @param blockType The current type of block (must be replaceable).
	 * @return True if a MutationBlockLiquidFlowInto was scheduled for this block.
	 */
	public static boolean didScheduleFlowInToBreak(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, Block blockType
	)
	{
		// We expect that this is only called when the block can be broken by liquids.
		Assert.assertTrue(env.blocks.isBrokenByFlowingLiquid(blockType));
		
		return _didScheduleFlowInToBreak(env, context, location, blockType);
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

	private static Pair<Block, LiquidRegistry.LiquidBlock> _determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location, LiquidRegistry.LiquidBlock currentBlock)
	{
		Environment env = Environment.getShared();
		
		LiquidRegistry.LiquidBlock east = _getLiquidOrNull(env, context, location.getRelative(1, 0, 0));
		LiquidRegistry.LiquidBlock west = _getLiquidOrNull(env, context, location.getRelative(-1, 0, 0));
		LiquidRegistry.LiquidBlock north = _getLiquidOrNull(env, context, location.getRelative(0, 1, 0));
		LiquidRegistry.LiquidBlock south = _getLiquidOrNull(env, context, location.getRelative(0, -1, 0));
		LiquidRegistry.LiquidBlock up = _getLiquidOrNull(env, context, location.getRelative(0, 0, 1));
		
		BlockProxy downProxy = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
		Block down = (null != downProxy)
			? downProxy.getBlock()
			: null
		;
		if ((null != down) && env.blocks.canBeReplaced(down))
		{
			down = null;
		}
		return env.liquids.chooseEmptyLiquidBlock(env, currentBlock, east, west, north, south, up, down);
	}

	private static LiquidRegistry.LiquidBlock _getLiquidOrNull(Environment env, TickProcessingContext context, AbsoluteLocation location)
	{
		BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
		return (null != proxy)
			? env.liquids.pairFrom(proxy).two()
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

	private static void _setBlockWithFollowUps(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, IMutableBlockProxy proxy
		, Block newType
		, FacingDirection outputDirection
		, byte blockDefined
	)
	{
		// Collect the information from the previous state or which isn't dependent on the state of this block, at all.
		Block oldType = proxy.getBlock();
		BlockProxy belowBlock = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
		
		// Set the changes to the block type.
		proxy.setBlockAndClear(newType);
		if (null != outputDirection)
		{
			proxy.setOrientation(outputDirection);
		}
		if (0 != blockDefined)
		{
			proxy.setBlockDefinedByte(blockDefined);
		}
		
		// Handle any follow-up actions or special handling for this block type.
		env.hooks.didSetBlock(env, context, location, proxy, oldType);
		
		// If this is the cornerstone of a composition, check the composition state and schedule a periodic update.
		if (env.composites.isActiveCornerstone(newType))
		{
			env.composites.processCornerstoneUpdate(env, context, location, proxy);
		}
		
		// If this changed into a fire source block, schedule the ignition mutations around it.
		_scheduleFireIfNewSource(env, context, location, oldType, newType);
		
		// If this block changed into a flammable type, see if it should receive an ignition mutation.
		// (set type first since this helper reads it).
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
		
		// Gravity blocks are placed once and then fall after an update, so see if that matters here.
		if (env.blocks.hasGravity(newType))
		{
			// If we think that this should fall, schedule the apply gravity mutation.
			if (null != belowBlock)
			{
				if (!env.blocks.isSupportedAgainstGravity(newType, belowBlock.getBlock()))
				{
					context.mutationSink.next(new MutationBlockApplyGravity(location));
				}
			}
		}
		
		// Handle the cases where this triggered a liquid to flow.
		boolean didScheduleLiquid = false;
		if (env.blocks.canBeReplaced(newType))
		{
			didScheduleLiquid = _didScheduleFlowInForReplaceable(env, context, location, oldType, proxy);
		}
		// See if this block might actually need to be broken, now, due to neighbours.
		if (!didScheduleLiquid && env.blocks.isBrokenByFlowingLiquid(newType))
		{
			didScheduleLiquid = _didScheduleFlowInToBreak(env, context, location, oldType);
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

	private static boolean _didScheduleFlowInForReplaceable(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, Block oldType
		, IBlockProxy proxy
	)
	{
		// We need to make sure that the eventual type is a mismatch but also that it has a flow rate (otherwise, placing a water source surrounded by air will think it should be air, meaning it should reflow immediately).
		LiquidRegistry.LiquidBlock currentLiquid = env.liquids.pairFrom(proxy).two();
		return _didScheduleFlowInto(env, context, location, currentLiquid);
	}

	private static boolean _didScheduleFlowInToBreak(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, Block blockType
	)
	{
		LiquidRegistry.LiquidBlock emptyBlock = null;
		return _didScheduleFlowInto(env, context, location, emptyBlock);
	}

	private static void _scheduleFireIfNewSource(Environment env, TickProcessingContext context, AbsoluteLocation location, Block oldType, Block newType)
	{
		if (env.blocks.isFireSource(newType) && !env.blocks.isFireSource(oldType))
		{
			List<AbsoluteLocation> flammable = FireHelpers.findFlammableNeighbours(env, context, location);
			for (AbsoluteLocation neighour : flammable)
			{
				MutationBlockStartFire startFire = new MutationBlockStartFire(neighour);
				context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
			}
		}
	}

	private static boolean _didScheduleFlowInto(Environment env, TickProcessingContext context, AbsoluteLocation location, LiquidRegistry.LiquidBlock currentLiquid)
	{
		Pair<Block, LiquidRegistry.LiquidBlock> eventualType = _determineEmptyBlockType(context, location, currentLiquid);
		LiquidRegistry.LiquidBlock eventualLiquid = eventualType.two();
		Block eventualBlock = eventualType.one();
		if (env.special.AIR == eventualBlock)
		{
			eventualBlock = null;
		}
		
		boolean didScheduleLiquid = false;
		if ((null != eventualBlock) || !_doLiquidsMatch(currentLiquid, eventualLiquid))
		{
			Block currentLiquidSource = (null != currentLiquid)
				? currentLiquid.sourceType()
				: null
			;
			Block eventualLiquidSource = (null != eventualLiquid)
				? eventualLiquid.sourceType()
				: null
			;
			
			// It is possible that neither of these exist (if this is a solid forming from 2 flowing neighbours), so pick a good default.
			long millisDelay = 1000L;
			if (null != currentLiquidSource)
			{
				long currentMillis = env.liquids.flowDelayMillis(currentLiquidSource);
				millisDelay = Math.min(millisDelay, currentMillis);
			}
			if (null != eventualLiquidSource)
			{
				long eventualMillis = env.liquids.flowDelayMillis(eventualLiquidSource);
				millisDelay = Math.min(millisDelay, eventualMillis);
			}
			Assert.assertTrue(millisDelay > 0L);
			
			context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
			didScheduleLiquid = true;
		}
		return didScheduleLiquid;
	}

	private static boolean _doLiquidsMatch(LiquidBlock one, LiquidBlock two)
	{
		Block oneBlock = (null != one)
			? one.sourceType()
			: null
		;
		byte oneDistance = (null != one)
			? one.distance()
			: LiquidRegistry.FLOW_NONE
		;
		Block twoBlock = (null != two)
			? two.sourceType()
			: null
		;
		byte twoDistance = (null != two)
			? two.distance()
			: LiquidRegistry.FLOW_NONE
		;
		
		return ((oneBlock == twoBlock) && (oneDistance == twoDistance));
	}
}
