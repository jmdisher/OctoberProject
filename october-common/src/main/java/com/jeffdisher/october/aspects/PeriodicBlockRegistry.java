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
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBlockRegistry
{
	public static PeriodicBlockRegistry loadRegistry(ItemRegistry items
		, BlockAspect blocks
		, PlantRegistry plants
		, SpecialConstants special
		, CompositeRegistry composites
	)
	{
		// TODO:  This should eventually be moved out into a declarative data file.
		_DefaultBehaviour defaultBehaviour = new _DefaultBehaviour();
		Map<Block, IBlockPeriodicBehaviour> map = new HashMap<>();
		
		PeriodicBehaviourPlant plantBehaviour = new PeriodicBehaviourPlant();
		PeriodicBehaviourCuboidLoader cuboidLoaderBehaviour = new PeriodicBehaviourCuboidLoader();
		PeriodicBehaviourHopper hopperBehaviour = new PeriodicBehaviourHopper();
		PeriodicBehaviourPortalKeystone portalBehaviour = new PeriodicBehaviourPortalKeystone();
		PeriodicBehaviourCompositeCornerstone compositeBehaviour = new PeriodicBehaviourCompositeCornerstone();
		
		for (Item item : items.ITEMS_BY_TYPE)
		{
			Block block = blocks.fromItem(item);
			List<IBlockPeriodicBehaviour> list = new ArrayList<>();
			
			// NOTE:  We need to check the cornerstone first (in the multi case) since it might change the block's state which other behaviours depend on.
			if (composites.isActiveCornerstone(block))
			{
				list.add(compositeBehaviour);
			}
			
			if (plants.growthDivisor(block) > 0)
			{
				// This is a plant.
				list.add(plantBehaviour);
			}
			if (special.blockCuboidLoader == block)
			{
				list.add(cuboidLoaderBehaviour);
			}
			if (special.blockHopper == block)
			{
				list.add(hopperBehaviour);
			}
			if (special.blockPortalKeystone == block)
			{
				list.add(portalBehaviour);
			}
			
			if (0 == list.size())
			{
				// We won't store this and just use the default.
			}
			else if (1 == list.size())
			{
				// We will just inline the single handler.
				map.put(block, list.get(0));
			}
			else
			{
				// This is a multi-handler so create that wrapper.
				_MultiBehaviour multi = new _MultiBehaviour(Collections.unmodifiableList(list));
				map.put(block, multi);
			}
		}
		return new PeriodicBlockRegistry(defaultBehaviour, Collections.unmodifiableMap(map));
	}


	private final IBlockPeriodicBehaviour _defaultBehaviour;
	private final Map<Block, IBlockPeriodicBehaviour> _map;

	private PeriodicBlockRegistry(IBlockPeriodicBehaviour defaultBehaviour
		, Map<Block, IBlockPeriodicBehaviour> map
	)
	{
		_defaultBehaviour = defaultBehaviour;
		_map = map;
	}

	public IBlockPeriodicBehaviour behaviour(Block block)
	{
		return _map.getOrDefault(block, _defaultBehaviour);
	}


	/**
	 * Just so the callers don't need to worry about handling nulls, we always return a do-nothing implementation when
	 * no special handling is required.
	 */
	private static class _DefaultBehaviour implements IBlockPeriodicBehaviour
	{
		public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
		{
			// Do nothing and don't reschedule this.
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
		public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
		{
			for (IBlockPeriodicBehaviour sub : _components)
			{
				sub.runPeriodic(env, context, location, newBlock);
			}
		}
	}
}
