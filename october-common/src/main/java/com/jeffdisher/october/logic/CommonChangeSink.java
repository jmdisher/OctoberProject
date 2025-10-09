package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The common implementation of the change sink which just collects the scheduled changes for later recovery.
 */
public class CommonChangeSink implements TickProcessingContext.IChangeSink
{
	private final Set<Integer> _loadedEntities;
	private final Set<Integer> _loadedCreatures;
	private Map<Integer, List<ScheduledChange>> _exportedEntityChanges;
	private Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> _exportedCreatureChanges;

	public CommonChangeSink(Set<Integer> loadedEntities, Set<Integer> loadedCreatures)
	{
		_loadedEntities = loadedEntities;
		_loadedCreatures = loadedCreatures;
		_exportedEntityChanges = new HashMap<>();
		_exportedCreatureChanges = new HashMap<>();
	}

	@Override
	public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
	{
		Assert.assertTrue(targetEntityId > 0);
		boolean didSchedule = false;
		
		if (_loadedEntities.contains(targetEntityId))
		{
			List<ScheduledChange> entityChanges = _getChangeList(targetEntityId);
			entityChanges.add(new ScheduledChange(change, 0L));
			didSchedule = true;
		}
		return didSchedule;
	}

	@Override
	public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
	{
		Assert.assertTrue(targetEntityId > 0);
		boolean didSchedule = false;
		
		if (_loadedEntities.contains(targetEntityId))
		{
			List<ScheduledChange> entityChanges = _getChangeList(targetEntityId);
			entityChanges.add(new ScheduledChange(change, millisToDelay));
			didSchedule = true;
		}
		return didSchedule;
	}

	@Override
	public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
	{
		Assert.assertTrue(targetCreatureId < 0);
		boolean didSchedule = false;
		
		if (_loadedCreatures.contains(targetCreatureId))
		{
			List<IEntityAction<IMutableCreatureEntity>> list = _exportedCreatureChanges.get(targetCreatureId);
			if (null == list)
			{
				list = new LinkedList<>();
				_exportedCreatureChanges.put(targetCreatureId, list);
			}
			list.add(change);
			didSchedule = true;
		}
		return didSchedule;
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
	public final Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> takeExportedCreatureChanges()
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
