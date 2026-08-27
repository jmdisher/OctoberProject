package com.jeffdisher.october.aspects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.block_periodic.IBlockPeriodicBehaviour;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourCompositeCornerstone;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourCuboidLoader;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourHopper;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourPlant;
import com.jeffdisher.october.block_periodic.PeriodicBehaviourPortalKeystone;
import com.jeffdisher.october.block_set.BlockSetBehaviourCornerstone;
import com.jeffdisher.october.block_set.BlockSetBehaviourFireSource;
import com.jeffdisher.october.block_set.BlockSetBehaviourFlammable;
import com.jeffdisher.october.block_set.BlockSetBehaviourGroundCoverSource;
import com.jeffdisher.october.block_set.BlockSetBehaviourGroundCoverTarget;
import com.jeffdisher.october.block_set.BlockSetBehaviourLogic;
import com.jeffdisher.october.block_set.BlockSetBehaviourPeriodicWrapper;
import com.jeffdisher.october.block_set.IBlockSetBehaviour;
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
		Map<Block, IBlockPeriodicBehaviour> doRunPeriodic = new HashMap<>();
		for (Item item : items.ITEMS_BY_TYPE)
		{
			Block block = blocks.fromItem(item);
			if (null != block)
			{
				List<IBlockSetBehaviour> setBlockList = new ArrayList<>();
				
				// Check if this block type has periodic behaviour.
				IBlockPeriodicBehaviour periodic = _periodicBehaviourForBlock(plants, special, composites, block);
				if (null != periodic)
				{
					setBlockList.add(new BlockSetBehaviourPeriodicWrapper(periodic));
				}
				
				// Check if this has a logic handler for when first placed.
				LogicAspect.ISignalChangeCallback placementHandler = logic.initialPlacementHandler(block);
				if (null != placementHandler)
				{
					setBlockList.add(new BlockSetBehaviourLogic(placementHandler));
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
					setBlockList.add(new BlockSetBehaviourGroundCoverTarget());
				}
				
				// Move this data into maps.
				if (!setBlockList.isEmpty())
				{
					didSetBlock.put(block, Collections.unmodifiableList(setBlockList));
				}
				if (null != periodic)
				{
					doRunPeriodic.put(block, periodic);
				}
			}
		}
		return new HookRegistry(Collections.unmodifiableMap(didSetBlock)
			, Collections.unmodifiableMap(doRunPeriodic)
		);
	}

	private static IBlockPeriodicBehaviour _periodicBehaviourForBlock(PlantRegistry plants
		, SpecialConstants special
		, CompositeRegistry composites
		, Block block
	)
	{
		List<IBlockPeriodicBehaviour> list = new ArrayList<>();
		
		// NOTE:  We need to check the cornerstone first (in the multi case) since it might change the block's state which other behaviours depend on.
		if (composites.isActiveCornerstone(block))
		{
			list.add(new PeriodicBehaviourCompositeCornerstone());
		}
		
		if (plants.growthDivisor(block) > 0)
		{
			// This is a plant.
			list.add(new PeriodicBehaviourPlant());
		}
		if (special.blockCuboidLoader == block)
		{
			list.add(new PeriodicBehaviourCuboidLoader());
		}
		if (special.blockHopper == block)
		{
			list.add(new PeriodicBehaviourHopper());
		}
		if (special.blockPortalKeystone == block)
		{
			list.add(new PeriodicBehaviourPortalKeystone());
		}
		
		IBlockPeriodicBehaviour behaviour;
		if (0 == list.size())
		{
			// Not a periodic case.
			behaviour = null;
		}
		else if (1 == list.size())
		{
			// We will just inline the single handler.
			behaviour = list.get(0);
		}
		else
		{
			// This is a multi-handler so create that wrapper.
			behaviour = new _MultiBehaviour(Collections.unmodifiableList(list));
		}
		return behaviour;
	}


	private final Map<Block, List<IBlockSetBehaviour>> _didSetBlock;

	private final Map<Block, IBlockPeriodicBehaviour> _doRunPeriodic;

	private HookRegistry(Map<Block, List<IBlockSetBehaviour>> didSetBlock
		, Map<Block, IBlockPeriodicBehaviour> doRunPeriodic
	)
	{
		_didSetBlock = didSetBlock;
		_doRunPeriodic = doRunPeriodic;
	}

	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		_didSetBlock(env, context, location, proxy, replacedType);
	}

	public void doRunPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		Block newType = proxy.getBlock();
		IBlockPeriodicBehaviour behaviour = _doRunPeriodic.get(newType);
		if (null != behaviour)
		{
			behaviour.runPeriodic(env, context, location, proxy);
			
			// Note that the periodic event is allowed to change the block.
			// Since we need to re-register the callback, do that unless we are going to call the set-block handlers.
			if (proxy.getBlock() == newType)
			{
				// Unchanged, so just do the usual registration.
				behaviour.doInitialRegistration(context, proxy);
			}
			else
			{
				// This changed so run the set-block.
				_didSetBlock(env, context, location, proxy, newType);
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


	/**
	 * Note that some of the blocks handle these periodic updates in multiple ways (consider a portal keystone:  A
	 * composite cornerstone and maintains portal surface) so we will compose those cases into a multi-behaviour.
	 */
	private static class _MultiBehaviour implements IBlockPeriodicBehaviour
	{
		private final List<IBlockPeriodicBehaviour> _components;
		
		public _MultiBehaviour(List<IBlockPeriodicBehaviour> components)
		{
			_components = components;
		}
		@Override
		public void doInitialRegistration(TickProcessingContext context, IMutableBlockProxy newBlock)
		{
			for (IBlockPeriodicBehaviour sub : _components)
			{
				sub.doInitialRegistration(context, newBlock);
			}
		}
		@Override
		public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
		{
			for (IBlockPeriodicBehaviour sub : _components)
			{
				sub.runPeriodic(env, context, location, newBlock);
			}
		}
	}
}
