package com.jeffdisher.october.aspects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.block_hybrid.BlockHybridBehaviourBecomeGroundcover;
import com.jeffdisher.october.block_hybrid.BlockHybridBehaviourFlowBrokenByLiquids;
import com.jeffdisher.october.block_hybrid.BlockHybridBehaviourFlowInReplaceable;
import com.jeffdisher.october.block_hybrid.BlockHybridBehaviourGravity;
import com.jeffdisher.october.block_hybrid.BlockHybridBehaviourLogic;
import com.jeffdisher.october.block_periodic.IBlockPeriodicBehaviour;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourCompositeCornerstone;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourCuboidLoader;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourHopper;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourPlant;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourPortalKeystone;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourTilledSoil;
import com.jeffdisher.october.block_set.BlockSetBehaviourCornerstone;
import com.jeffdisher.october.block_set.BlockSetBehaviourFireSource;
import com.jeffdisher.october.block_set.BlockSetBehaviourFlammable;
import com.jeffdisher.october.block_set.BlockSetBehaviourGroundCoverSource;
import com.jeffdisher.october.block_set.BlockSetBehaviourPeriodicWrapper;
import com.jeffdisher.october.block_set.IBlockSetBehaviour;
import com.jeffdisher.october.block_update.BlockUpdateBehaviourCheckSupported;
import com.jeffdisher.october.block_update.BlockUpdateBehaviourExtinguish;
import com.jeffdisher.october.block_update.BlockUpdateBehaviourOrphanLeaf;
import com.jeffdisher.october.block_update.BlockUpdateBehaviourRevertGroundcover;
import com.jeffdisher.october.block_update.IBlockUpdateBehaviour;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The HookRegistry is a high-level object which uses the other elements of the Environment to connect high-level events
 * in the system to the various actions which need to follow as a result.
 * An example of this is the "didSetBlock" call wherein specific types of blocks will need to set other auxiliary state
 * and/or register for follow-up actions when a block is set, based on the block type or replaced block type.
 */
public class HookRegistry
{
	public static HookRegistry setupHooks(ItemRegistry items
		, BlockAspect blocks
		, PlantRegistry plants
		, LogicAspect logic
		, CompositeRegistry composites
		, GroundCoverRegistry groundCover
		, SpecialConstants special
	)
	{
		
		Map<Block, List<IBlockSetBehaviour>> didSetBlock = new HashMap<>();
		Map<Block, List<IBlockUpdateBehaviour>> doRunUpdate = new HashMap<>();
		Map<Block, List<IBlockPeriodicBehaviour>> doRunPeriodic = new HashMap<>();
		for (Item item : items.ITEMS_BY_TYPE)
		{
			Block block = blocks.fromItem(item);
			if (null != block)
			{
				List<IBlockSetBehaviour> setBlockList = new ArrayList<>();
				List<IBlockUpdateBehaviour> updateList = new ArrayList<>();
				List<IBlockPeriodicBehaviour> periodicList = new ArrayList<>();
				
				// Check if this has a logic handler for when first placed.
				LogicAspect.ISignalChangeCallback placementHandler = logic.initialPlacementHandler(block);
				if (null != placementHandler)
				{
					setBlockList.add(new BlockHybridBehaviourLogic(placementHandler));
				}
				
				// The composite cornerstone is a simple one but we still want it in this general mechanism.
				if (composites.isActiveCornerstone(block))
				{
					setBlockList.add(new BlockSetBehaviourCornerstone());
				}
				
				// Fire sources have extra checks when invoked but are based on their block type.
				if (blocks.isFireSource(block))
				{
					setBlockList.add(new BlockSetBehaviourFireSource());
				}
				
				// Check flammable blocks.
				if (blocks.isFlammable(block))
				{
					setBlockList.add(new BlockSetBehaviourFlammable());
				}
				
				// Handle blocks which can spread ground cover.
				if (groundCover.isGroundCover(block))
				{
					setBlockList.add(new BlockSetBehaviourGroundCoverSource());
				}
				
				// Handle the blocks which can receive ground cover.
				if (null != groundCover.canGrowGroundCover(block))
				{
					setBlockList.add(new BlockHybridBehaviourBecomeGroundcover());
				}
				
				// Handle the gravity blocks.
				if (blocks.hasGravity(block))
				{
					setBlockList.add(new BlockHybridBehaviourGravity());
				}
				if (blocks.canBeReplaced(block))
				{
					setBlockList.add(new BlockHybridBehaviourFlowInReplaceable());
				}
				if (blocks.isBrokenByFlowingLiquid(block))
				{
					setBlockList.add(new BlockHybridBehaviourFlowBrokenByLiquids());
				}
				
				// Handle update checks if the block is supported.
				if (blocks.doesRequireSupport(block))
				{
					updateList.add(new BlockUpdateBehaviourCheckSupported());
				}
				if (blocks.canBeReplaced(block))
				{
					updateList.add(new BlockHybridBehaviourFlowInReplaceable());
				}
				if (blocks.isBrokenByFlowingLiquid(block))
				{
					updateList.add(new BlockHybridBehaviourFlowBrokenByLiquids());
				}
				if (blocks.hasGravity(block))
				{
					updateList.add(new BlockHybridBehaviourGravity());
				}
				if (blocks.isFlammable(block))
				{
					updateList.add(new BlockUpdateBehaviourExtinguish());
				}
				if (groundCover.isGroundCover(block))
				{
					updateList.add(new BlockUpdateBehaviourRevertGroundcover());
				}
				if (null != groundCover.canGrowGroundCover(block))
				{
					updateList.add(new BlockHybridBehaviourBecomeGroundcover());
				}
				LogicAspect.ISignalChangeCallback handler = logic.blockUpdateHandler(block);
				if (null != handler)
				{
					updateList.add(new BlockHybridBehaviourLogic(handler));
				}
				if (special.blockLeaf == block)
				{
					updateList.add(new BlockUpdateBehaviourOrphanLeaf());
				}
				
				// Populate the periodic callback list.
				// NOTE:  We need to check the cornerstone first (in the multi case) since it might change the block's state which other behaviours depend on.
				if (composites.isActiveCornerstone(block))
				{
					periodicList.add(new PeriodicBehaviourCompositeCornerstone());
				}
				if (plants.growthDivisor(block) > 0)
				{
					periodicList.add(new PeriodicBehaviourPlant());
				}
				if (special.blockCuboidLoader == block)
				{
					periodicList.add(new PeriodicBehaviourCuboidLoader());
				}
				if (special.blockHopper == block)
				{
					periodicList.add(new PeriodicBehaviourHopper());
				}
				if (special.blockPortalKeystone == block)
				{
					periodicList.add(new PeriodicBehaviourPortalKeystone());
				}
				if (special.blockTilledSoil == block)
				{
					periodicList.add(new PeriodicBehaviourTilledSoil());
				}
				if (!periodicList.isEmpty())
				{
					// All periodic mutation blocks need to run an initial callback registration when placed.
					setBlockList.add(new BlockSetBehaviourPeriodicWrapper(periodicList));
				}
				
				// Move this data into maps.
				if (!setBlockList.isEmpty())
				{
					didSetBlock.put(block, Collections.unmodifiableList(setBlockList));
				}
				if (!updateList.isEmpty())
				{
					doRunUpdate.put(block, Collections.unmodifiableList(updateList));
				}
				if (!periodicList.isEmpty())
				{
					doRunPeriodic.put(block, periodicList);
				}
			}
		}
		return new HookRegistry(Collections.unmodifiableMap(didSetBlock)
			, Collections.unmodifiableMap(doRunUpdate)
			, Collections.unmodifiableMap(doRunPeriodic)
		);
	}


	private final Map<Block, List<IBlockSetBehaviour>> _didSetBlock;

	private final Map<Block, List<IBlockUpdateBehaviour>> _doRunUpdate;
	private final Map<Block, List<IBlockPeriodicBehaviour>> _doRunPeriodic;

	private HookRegistry(Map<Block, List<IBlockSetBehaviour>> didSetBlock
		, Map<Block, List<IBlockUpdateBehaviour>> doRunUpdate
		, Map<Block, List<IBlockPeriodicBehaviour>> doRunPeriodic
	)
	{
		_didSetBlock = didSetBlock;
		_doRunUpdate = doRunUpdate;
		_doRunPeriodic = doRunPeriodic;
	}

	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		_didSetBlock(env, context, location, proxy, replacedType);
	}

	public void doRunPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		Block newType = proxy.getBlock();
		List<IBlockPeriodicBehaviour> behaviours = _doRunPeriodic.get(newType);
		if (null != behaviours)
		{
			for (IBlockPeriodicBehaviour behaviour : behaviours)
			{
				behaviour.runPeriodic(env, context, location, proxy);
			}
			
			// Note that the periodic event is allowed to change the block.
			// Since we need to re-register the callback, do that unless we are going to call the set-block handlers.
			if (proxy.getBlock() == newType)
			{
				for (IBlockPeriodicBehaviour behaviour : behaviours)
				{
					// Unchanged, so just do the usual registration.
					behaviour.doInitialRegistration(context, proxy);
				}
			}
			else
			{
				// This changed so run the set-block.
				_didSetBlock(env, context, location, proxy, newType);
			}
		}
	}

	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		Block type = proxy.getBlock();
		List<IBlockUpdateBehaviour> list = _doRunUpdate.get(type);
		if (null != list)
		{
			for (IBlockUpdateBehaviour update : list)
			{
				update.doRunUpdate(env, context, location, proxy);
				
				// The block can be changed (generally broken) by the update.  Usually, the update just schedules a
				// follow-up mutation but some changes are applied inline.  There is no point in continuing to process
				// update events once the block has been replaced (since other aspects are cleared when this happens).
				// TODO:  Can we move the didSetBlock() call to this level, to only call once after all updates?
				if (proxy.getBlock() != type)
				{
					break;
				}
			}
		}
	}


	private void _didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		Block newType = proxy.getBlock();
		List<IBlockSetBehaviour> list = _didSetBlock.get(newType);
		if (null != list)
		{
			for (IBlockSetBehaviour set : list)
			{
				set.didSetBlock(env, context, location, proxy, replacedType);
			}
			
			// It is not acceptable change the block in the post-set-block hooks.
			Assert.assertTrue(proxy.getBlock() == newType);
		}
	}
}
