package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.changes.EntityChangeMove;
import com.jeffdisher.october.types.EntityLocation;


public class TestTwoPhaseActivityManager
{
	@Test
	public void completeSingleEntity()
	{
		int entityId = 1;
		TwoPhaseActivityManager manager = new TwoPhaseActivityManager(Set.of(entityId));
		int activityId = 1;
		TwoPhaseActivityManager.ActivityPlan plan = manager.getActivityForEntity(entityId);
		Assert.assertNull(plan);
		manager.activityCompleted(entityId, activityId, true);
		List<TwoPhaseActivityManager.ActivityResult> results = manager.getResultsForEntity(entityId);
		Assert.assertEquals(1, results.size());
		TwoPhaseActivityManager.ActivityResult activity = results.get(0);
		Assert.assertEquals(activityId, activity.activityId());
		Assert.assertEquals(true, activity.isSuccess());
	}

	@Test
	public void invalidateSingleEntity()
	{
		int entityId = 1;
		TwoPhaseActivityManager manager = new TwoPhaseActivityManager(Set.of(entityId));
		int activityId = 1;
		manager.scheduleNewActivity(entityId, activityId, new EntityChangeMove(entityId, new EntityLocation(0.0f, 0.0f, 0.0f)), 10L);
		TwoPhaseActivityManager.ActivityPlan plan = manager.getActivityForEntity(entityId);
		Assert.assertEquals(activityId, plan.activityId());
		manager.nonActivityCompleted(entityId);
		List<TwoPhaseActivityManager.ActivityResult> results = manager.getResultsForEntity(entityId);
		Assert.assertEquals(1, results.size());
		TwoPhaseActivityManager.ActivityResult activity = results.get(0);
		Assert.assertEquals(activityId, activity.activityId());
		Assert.assertEquals(false, activity.isSuccess());
	}
}
