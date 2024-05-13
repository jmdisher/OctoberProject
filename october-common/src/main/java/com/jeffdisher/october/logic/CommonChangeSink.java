package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The common implementation of the change sink which just collects the scheduled changes for later recovery.
 */
public class CommonChangeSink implements TickProcessingContext.IChangeSink
{
	private Map<Integer, List<ScheduledChange>> _exportedEntityChanges = new HashMap<>();
	private Map<Integer, List<IMutationEntity<IMutableMinimalEntity>>> _exportedCreatureChanges = new HashMap<>();

	@Override
	public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
	{
		Assert.assertTrue(targetEntityId > 0);
		List<ScheduledChange> entityChanges = _getChangeList(targetEntityId);
		entityChanges.add(new ScheduledChange(change, 0L));
	}

	@Override
	public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
	{
		Assert.assertTrue(targetEntityId > 0);
		List<ScheduledChange> entityChanges = _getChangeList(targetEntityId);
		entityChanges.add(new ScheduledChange(change, millisToDelay));
	}

	@Override
	public void creature(int targetCreatureId, IMutationEntity<IMutableMinimalEntity> change)
	{
		Assert.assertTrue(targetCreatureId < 0);
		List<IMutationEntity<IMutableMinimalEntity>> list = _exportedCreatureChanges.get(targetCreatureId);
		if (null == list)
		{
			list = new LinkedList<>();
			_exportedCreatureChanges.put(targetCreatureId, list);
		}
		list.add(change);
	}

	/**
	 * Removes the collected exported changes.  Note that the receiver can no longer listen to changes after this call.
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

	/**
	 * Removes the collected exported creature changes.  Note that the receiver can no longer listen to changes after
	 * this call.
	 * 
	 * @return The mutable change map, now owned by the caller.
	 */
	public final Map<Integer, List<IMutationEntity<IMutableMinimalEntity>>> takeExportedCreatureChanges()
	{
		try
		{
			return _exportedCreatureChanges;
		}
		finally
		{
			_exportedCreatureChanges = null;
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
