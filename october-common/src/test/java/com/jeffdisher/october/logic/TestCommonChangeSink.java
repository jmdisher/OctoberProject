package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class TestCommonChangeSink
{
	@Test
	public void basicCalls()
	{
		Set<Integer> loadedEntities = Set.of(1);
		Set<Integer> loadedCreatures = Set.of(-2);
		CommonChangeSink sink = new CommonChangeSink(loadedEntities, loadedCreatures, Set.of());
		
		
		EntityActionNudge<IMutablePlayerEntity> next = new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f));
		EntityActionNudge<IMutablePlayerEntity> future = new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f));
		EntityActionNudge<IMutableCreatureEntity> creature = new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f));
		
		Assert.assertTrue(sink.future(1, future, 1000L));
		Assert.assertTrue(sink.next(1, next));
		Assert.assertTrue(sink.creature(-2, creature));
		Assert.assertFalse(sink.future(2, new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f)), 1000L));
		Assert.assertFalse(sink.next(2, new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f))));
		Assert.assertFalse(sink.creature(-1, new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f))));
		
		Map<Integer, List<ScheduledChange>> playerChanges = sink.takeExportedChanges();
		Assert.assertEquals(1, playerChanges.size());
		List<ScheduledChange> changes = playerChanges.get(1);
		Assert.assertEquals(2, changes.size());
		Assert.assertEquals(1000L, changes.get(0).millisUntilReady());
		Assert.assertEquals(future, changes.get(0).change());
		Assert.assertEquals(0L, changes.get(1).millisUntilReady());
		Assert.assertEquals(next, changes.get(1).change());
		
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges = sink.takeExportedCreatureChanges();
		Assert.assertEquals(1, creatureChanges.size());
		List<IEntityAction<IMutableCreatureEntity>> actions = creatureChanges.get(-2);
		Assert.assertEquals(1, actions.size());
		Assert.assertEquals(creature, actions.get(0));
	}

	@Test
	public void passives()
	{
		Set<Integer> loadedEntities = Set.of();
		Set<Integer> loadedCreatures = Set.of();
		Set<Integer> loadedPassives = Set.of(1, 3);
		CommonChangeSink sink = new CommonChangeSink(loadedEntities, loadedCreatures, loadedPassives);
		
		_PassiveAction one = new _PassiveAction();
		_PassiveAction two = new _PassiveAction();
		_PassiveAction three = new _PassiveAction();
		Assert.assertTrue(sink.passive(1, one));
		Assert.assertFalse(sink.passive(2, two));
		Assert.assertTrue(sink.passive(3, three));
		
		Map<Integer, List<IPassiveAction>> passiveChanges = sink.takeExportedPassiveActions();
		Assert.assertEquals(2, passiveChanges.size());
		Assert.assertEquals(one, passiveChanges.get(1).get(0));
		Assert.assertEquals(three, passiveChanges.get(3).get(0));
	}


	private static class _PassiveAction implements IPassiveAction
	{
		@Override
		public PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity)
		{
			// This has no implementation as it is just a token to pass around.
			throw new AssertionError("Not used in test");
		}
	}
}
