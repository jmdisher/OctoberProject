package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.utils.Assert;


/**
 * Note that this class must be considered carefully since calls will be made by different threads, concurrently, but
 * all calls related to a given entityId will always come in on the same thread.
 * The assumption is that one instance of this will exist only for the duration of a tick so the actual map will be
 * immutable to make concurrent access safe since the map is never changed, only its contained objects.
 */
public class TwoPhaseActivityManager
{
	// We will point to a mutable structure for each entry, since we can't be sure if the map allows concurrent updates to existing keys (it should be safe, but might update internal state).
	private final Map<Integer, EntityData> _data;

	/**
	 * Creates the short-lived activity manager with knowledge of the given fixed set of entity IDs.
	 * 
	 * @param entityIdsInTick The fixed set of entities that this instance will track.
	 */
	public TwoPhaseActivityManager(Set<Integer> entityIdsInTick)
	{
		Map<Integer, EntityData> filling = new HashMap<>();
		for (Integer id : entityIdsInTick)
		{
			filling.put(id, new EntityData());
		}
		_data = Collections.unmodifiableMap(filling);
	}

	/**
	 * Schedules an activity for the given entity, tracking the second change in the activity and the delay until it
	 * should be scheduled.
	 * 
	 * @param entityId The ID of the entity performing the activity.
	 * @param activityId The ID to use to track the activity.
	 * @param change The change which will conclude the activity.
	 * @param delayMillis The time, in milliseconds, until the change should be run.
	 */
	public void scheduleNewActivity(int entityId, long activityId, IEntityChange change, long delayMillis)
	{
		Assert.assertTrue(activityId > 0);
		EntityData data = _data.get(entityId);
		if (null != data.current)
		{
			// We need to cancel the existing activity.
			_completeActivity(data, data.current.activityId, false);
		}
		data.current = new ActivityPlan(activityId, change, delayMillis);
	}

	/**
	 * Concludes that a given activity has completed.  In this case, the activity wasn't scheduled here, but was run,
	 * instead.
	 * 
	 * @param entityId The ID of the entity performing the activity.
	 * @param activityId The ID to use to track the activity.
	 * @param wasSuccess True if the activity was applied, false if it failed.
	 */
	public void activityCompleted(int entityId, long activityId, boolean wasSuccess)
	{
		EntityData data = _data.get(entityId);
		// Note that the data.current will be null in this case since it was scheduled to run, not to be held here for later.
		Assert.assertTrue(null == data.current);
		
		_completeActivity(data, activityId, wasSuccess);
	}

	/**
	 * Called when an entity completed some change which wasn't part of the activity in order to invalidate any existing
	 * activities (since only 1 can be active at a time).
	 * 
	 * @param entityId The ID of the entity performing the activity.
	 */
	public void nonActivityCompleted(int entityId)
	{
		EntityData data = _data.get(entityId);
		// We only do anything here if it invalidates an activity.
		if (null != data.current)
		{
			// We need to cancel the existing activity.
			_completeActivity(data, data.current.activityId, false);
		}
	}

	/**
	 * Requests the current activity plan for an entity.
	 * 
	 * @param entityId The ID of the entity performing the activity.
	 * @return The plan, or null if there isn't currently an activity plan.
	 */
	public ActivityPlan getActivityForEntity(int entityId)
	{
		return _data.get(entityId).current;
	}

	/**
	 * Requests the list of results for activities run by a given entity.  Note that this list very rarely contains
	 * more than a single element as it would be unusual to complete multiple activities in a single tick.
	 * 
	 * @param entityId The ID of the entity performing the activity.
	 * @return The list of results (null if empty).
	 */
	public List<ActivityResult> getResultsForEntity(int entityId)
	{
		return _data.get(entityId).results;
	}


	private void _completeActivity(EntityData data, long activityId, boolean wasSuccess)
	{
		if (null == data.results)
		{
			data.results = new ArrayList<>();
		}
		ActivityResult result = new ActivityResult(activityId, wasSuccess);
		data.results.add(result);
		data.current = null;
	}


	public static record ActivityPlan(long activityId, IEntityChange phase2, long delayMillis)
	{}

	public static record ActivityResult(long activityId, boolean isSuccess)
	{}


	private static class EntityData
	{
		public ActivityPlan current = null;
		public List<ActivityResult> results = null;
	}
}
