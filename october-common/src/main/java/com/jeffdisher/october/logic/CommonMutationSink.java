package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The common implementation of the mutation sink which just collects the scheduled mutations for later recovery.
 */
public class CommonMutationSink implements TickProcessingContext.IMutationSink
{
	private List<ScheduledMutation> _exportedMutations = new ArrayList<>();

	@Override
	public void next(IMutationBlock mutation)
	{
		// Note that it may be worth pre-filtering the mutations to eagerly schedule them against this cuboid but that seems like needless complexity.
		_exportedMutations.add(new ScheduledMutation(mutation, 0L));
	}

	@Override
	public void future(IMutationBlock mutation, long millisToDelay)
	{
		_exportedMutations.add(new ScheduledMutation(mutation, millisToDelay));
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
