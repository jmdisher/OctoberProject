package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The common implementation of the mutation sink which just collects the scheduled mutations for later recovery.
 */
public class CommonMutationSink implements TickProcessingContext.IMutationSink
{
	private final Set<CuboidAddress> _loadedCuboids;
	private List<ScheduledMutation> _exportedMutations;

	public CommonMutationSink(Set<CuboidAddress> loadedCuboids)
	{
		_loadedCuboids = loadedCuboids;
		_exportedMutations = new ArrayList<>();
	}

	@Override
	public boolean next(IMutationBlock mutation)
	{
		boolean didSchedule = false;
		if (_loadedCuboids.contains(mutation.getAbsoluteLocation().getCuboidAddress()))
		{
			_exportedMutations.add(new ScheduledMutation(mutation, 0L));
			didSchedule = true;
		}
		return didSchedule;
	}

	@Override
	public boolean future(IMutationBlock mutation, long millisToDelay)
	{
		Assert.assertTrue(millisToDelay > 0L);
		boolean didSchedule = false;
		if (_loadedCuboids.contains(mutation.getAbsoluteLocation().getCuboidAddress()))
		{
			_exportedMutations.add(new ScheduledMutation(mutation, millisToDelay));
			didSchedule = true;
		}
		return didSchedule;
	}

	/**
	 * Removes the collected exported mutations, invalidating the receiver as a result.
	 * 
	 * @return The mutable mutation list, now owned by the caller.
	 */
	public List<ScheduledMutation> takeExportedMutations()
	{
		try
		{
			return _exportedMutations;
		}
		finally
		{
			_exportedMutations = null;
		}
	}
}
