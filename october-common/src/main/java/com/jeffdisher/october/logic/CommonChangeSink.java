package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.TargetedAction;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The common implementation of the change sink which just collects the scheduled changes for later recovery.
 */
public class CommonChangeSink implements TickProcessingContext.IChangeSink
{
	private final Set<Integer> _loadedEntities;
	private final Set<Integer> _loadedCreatures;
	private final Set<Integer> _loadedPassives;
	private List<TargetedAction<ScheduledChange>> _exportedEntityChanges;
	private List<TargetedAction<IEntityAction<IMutableCreatureEntity>>> _exportedCreatureChanges;
	private List<TargetedAction<IPassiveAction>> _exportedPassiveActions;

	public CommonChangeSink(Set<Integer> loadedEntities, Set<Integer> loadedCreatures, Set<Integer> loadedPassives)
	{
		_loadedEntities = loadedEntities;
		_loadedCreatures = loadedCreatures;
		_loadedPassives = loadedPassives;
		_exportedEntityChanges = new ArrayList<>();
		_exportedCreatureChanges = new ArrayList<>();
		_exportedPassiveActions = new ArrayList<>();
	}

	@Override
	public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
	{
		Assert.assertTrue(targetEntityId > 0);
		boolean didSchedule = false;
		
		if (_loadedEntities.contains(targetEntityId))
		{
			TargetedAction<ScheduledChange> targeted = new TargetedAction<>(targetEntityId, new ScheduledChange(change, 0L));
			_exportedEntityChanges.add(targeted);
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
			TargetedAction<ScheduledChange> targeted = new TargetedAction<>(targetEntityId, new ScheduledChange(change, millisToDelay));
			_exportedEntityChanges.add(targeted);
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
			TargetedAction<IEntityAction<IMutableCreatureEntity>> targeted = new TargetedAction<>(targetCreatureId, change);
			_exportedCreatureChanges.add(targeted);
			didSchedule = true;
		}
		return didSchedule;
	}

	@Override
	public boolean passive(int targetPassiveId, IPassiveAction action)
	{
		Assert.assertTrue(targetPassiveId > 0);
		boolean didSchedule = false;
		
		if (_loadedPassives.contains(targetPassiveId))
		{
			TargetedAction<IPassiveAction> targeted = new TargetedAction<>(targetPassiveId, action);
			_exportedPassiveActions.add(targeted);
			didSchedule = true;
		}
		return didSchedule;
	}

	/**
	 * Removes the collected exported changes.  Note that the receiver can no longer listen to changes after this call.
	 * 
	 * @return The mutable change list, now owned by the caller.
	 */
	public final List<TargetedAction<ScheduledChange>> takeExportedChanges()
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
	 * @return The mutable change list, now owned by the caller.
	 */
	public final List<TargetedAction<IEntityAction<IMutableCreatureEntity>>> takeExportedCreatureChanges()
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

	/**
	 * Removes the collected exported passive actions.  Note that the receiver can no longer listen to passive actions
	 * after this call.
	 * 
	 * @return The mutable passive action list, now owned by the caller.
	 */
	public List<TargetedAction<IPassiveAction>> takeExportedPassiveActions()
	{
		try
		{
			return _exportedPassiveActions;
		}
		finally
		{
			_exportedPassiveActions = null;
		}
	}
}
