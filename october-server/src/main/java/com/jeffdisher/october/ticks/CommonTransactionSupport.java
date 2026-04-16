package com.jeffdisher.october.ticks;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The transaction support created by TickContextBuilder, pulled out so that it can be more easily tested.
 * The transactions that this utility supports are very uncommon so we don't do any expensive setup (although that could
 * change in the future).
 */
public class CommonTransactionSupport implements TickProcessingContext.ITransactionSupport
{
	private final TickMaterials _materials;

	public CommonTransactionSupport(TickMaterials materials)
	{
		_materials = materials;
	}

	@Override
	public boolean checkScheduledMutationCount(Collection<AbsoluteLocation> locations, int expectedMutations)
	{
		// In the future, we might want to optimize this since the current approach is quite expensive but adds no overhead when not called.
		Map<AbsoluteLocation, Integer> mutationsThisTick = new HashMap<>();
		for (TickInput.ColumnInput column : _materials.highLevel().columns())
		{
			for (TickInput.CuboidInput cuboid : column.cuboids())
			{
				for (ScheduledMutation scheduled : cuboid.mutations())
				{
					if (0L == scheduled.millisUntilReady())
					{
						AbsoluteLocation location = scheduled.mutation().getAbsoluteLocation();
						int existing = mutationsThisTick.getOrDefault(location, 0);
						mutationsThisTick.put(location, existing + 1);
					}
				}
				AbsoluteLocation cuboidBase = cuboid.cuboid().getCuboidAddress().getBase();
				for (Map.Entry<BlockAddress, Long> ent : cuboid.periodicMutationMillis().entrySet())
				{
					if (0L == ent.getValue())
					{
						BlockAddress blockAddress = ent.getKey();
						AbsoluteLocation location = cuboidBase.relativeForBlock(blockAddress);
						int existing = mutationsThisTick.getOrDefault(location, 0);
						mutationsThisTick.put(location, existing + 1);
					}
				}
			}
		}
		
		boolean didMatch = true;
		Set<CuboidAddress> loadedCuboids = _materials.completedCuboids().keySet();
		for (AbsoluteLocation location : locations)
		{
			if (loadedCuboids.contains(location.getCuboidAddress()))
			{
				if (expectedMutations != mutationsThisTick.getOrDefault(location, 0))
				{
					didMatch = false;
					break;
				}
			}
			else
			{
				didMatch = false;
				break;
			}
		}
		
		if (didMatch)
		{
			// Finally, we will check if there were any block updates which would invalidate this (this should logically
			// go first but the current implementation is expensive so it goes last).
			// TODO:  This should be replaced with a more locally-restricted algorithm instead of checking all updates.
			Set<AbsoluteLocation> syntheticUpdateSources = new HashSet<>();
			for (AbsoluteLocation location : locations)
			{
				syntheticUpdateSources.add(location.getRelative(0, 0, -1));
				syntheticUpdateSources.add(location.getRelative(0, 0, 1));
				syntheticUpdateSources.add(location.getRelative(0, -1, 0));
				syntheticUpdateSources.add(location.getRelative(0, 1, 0));
				syntheticUpdateSources.add(location.getRelative(-1, 0, 0));
				syntheticUpdateSources.add(location.getRelative(1, 0, 0));
			}
			
			// The match only persists if none of these blocks are expected to be the source of a block update (meaning
			// the update will run against one of our selected block).
			syntheticUpdateSources.retainAll(_materials.forceMissBlocksPreviousCache());
			didMatch = syntheticUpdateSources.isEmpty();
		}
		return didMatch;
	}

}
