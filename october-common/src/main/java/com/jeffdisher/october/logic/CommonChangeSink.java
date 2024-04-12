package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The common implementation of the change sink which just collects the scheduled changes for later recovery.
 */
public class CommonChangeSink implements TickProcessingContext.IChangeSink
{
	private Map<Integer, List<ScheduledChange>> _exportedEntityChanges = new HashMap<>();

	@Override
	public void next(int targetEntityId, IMutationEntity change)
	{
		List<ScheduledChange> entityChanges = _getChangeList(targetEntityId);
		entityChanges.add(new ScheduledChange(change, 0L));
	}

	@Override
	public void future(int targetEntityId, IMutationEntity change, long millisToDelay)
	{
		List<ScheduledChange> entityChanges = _getChangeList(targetEntityId);
		entityChanges.add(new ScheduledChange(change, millisToDelay));
	}

	/**
	 * Removes the collected exported changes, invalidating the receiver as a result.
	 * 
	 * @return The mutable change map, now owned by the caller.
	 */
	public final Map<Integer, List<ScheduledChange>> takeExportedChanges()
	{
		try
		{
			return _exportedEntityChanges;
		}
		finally
		{
			_exportedEntityChanges = null;
		}
	}


	private List<ScheduledChange> _getChangeList(int targetEntityId)
	{
		List<ScheduledChange> entityChanges = _exportedEntityChanges.get(targetEntityId);
		if (null == entityChanges)
		{
			entityChanges = new LinkedList<>();
			_exportedEntityChanges.put(targetEntityId, entityChanges);
		}
		return entityChanges;
	}
}
